package com.example.voice

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class VoskManager(
    private val context: Context,
    private val onTextRecognized: (text: String) -> Unit,
    private val onPartialTranscript: (text: String) -> Unit,
    private val onStatusChanged: (status: String) -> Unit,
    private val onErrorOccurred: (error: String) -> Unit
) : RecognitionListener {

    sealed class ModelState {
        object NotInstalled : ModelState()
        data class Downloading(val progressPercent: Int) : ModelState()
        object Ready : ModelState()
        data class Error(val message: String) : ModelState()
    }

    private val _modelState = MutableStateFlow<ModelState>(ModelState.NotInstalled)
    val modelState: StateFlow<ModelState> = _modelState.asStateFlow()

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var speechService: SpeechService? = null
    private var isListeningDesired = false

    private val modelDir: File
        get() = File(context.filesDir, "vosk-model-small-en-us")

    init {
        checkExistingModel()
    }

    fun isModelReady(): Boolean {
        return model != null && modelDir.exists()
    }

    fun checkExistingModel() {
        val dir = modelDir
        if (dir.exists() && isValidModelDir(dir)) {
            loadModelFromDir(dir)
        } else {
            _modelState.value = ModelState.NotInstalled
        }
    }

    private fun isValidModelDir(dir: File): Boolean {
        // Vosk model directory contains either "am" or "conf" or "final.mdl" or "graph"
        val files = dir.list() ?: return false
        return files.any { it.contains("am") || it.contains("conf") || it.contains("model") || it.contains("final.mdl") }
    }

    private fun loadModelFromDir(dir: File) {
        try {
            Log.d(TAG, "Loading Vosk Model from: ${dir.absolutePath}")
            model = Model(dir.absolutePath)
            _modelState.value = ModelState.Ready
            Log.d(TAG, "Vosk Model loaded successfully!")
        } catch (e: Exception) {
            Log.e(TAG, "Failed loading Vosk model", e)
            _modelState.value = ModelState.Error("Model load failed: ${e.message}")
        }
    }

    fun startListening() {
        isListeningDesired = true
        if (model == null) {
            Log.w(TAG, "Cannot start Vosk: Model not loaded yet")
            onErrorOccurred("Vosk model not installed. Please download the offline model.")
            return
        }

        try {
            stopListening() // Reset previous session if active
            recognizer = Recognizer(model, 16000.0f)
            speechService = SpeechService(recognizer, 16000.0f)
            speechService?.startListening(this)
            onStatusChanged("LISTENING")
            Log.d(TAG, "Vosk SpeechService listening started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Vosk SpeechService", e)
            onErrorOccurred("Vosk start error: ${e.message}")
        }
    }

    fun stopListening() {
        isListeningDesired = false
        try {
            speechService?.stop()
            speechService?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Vosk SpeechService", e)
        }
        speechService = null
        try {
            recognizer?.close()
        } catch (e: Exception) {
            // Ignore
        }
        recognizer = null
        onStatusChanged("STOPPED")
    }

    fun pause(pause: Boolean) {
        try {
            speechService?.setPause(pause)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting pause on Vosk", e)
        }
    }

    override fun onPartialResult(hypothesis: String?) {
        if (hypothesis.isNullOrBlank()) return
        try {
            val json = JSONObject(hypothesis)
            val partial = json.optString("partial", "").trim()
            if (partial.isNotBlank()) {
                onPartialTranscript(partial)
                onStatusChanged("SPEECH_DETECTED")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing partial result", e)
        }
    }

    override fun onResult(hypothesis: String?) {
        if (hypothesis.isNullOrBlank()) return
        try {
            val json = JSONObject(hypothesis)
            val text = json.optString("text", "").trim()
            if (text.isNotBlank()) {
                Log.d(TAG, "Vosk onResult: $text")
                onTextRecognized(text)
                onStatusChanged("LISTENING")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing result", e)
        }
    }

    override fun onFinalResult(hypothesis: String?) {
        if (hypothesis.isNullOrBlank()) return
        try {
            val json = JSONObject(hypothesis)
            val text = json.optString("text", "").trim()
            if (text.isNotBlank()) {
                Log.d(TAG, "Vosk onFinalResult: $text")
                onTextRecognized(text)
                onStatusChanged("LISTENING")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing final result", e)
        }
    }

    override fun onError(exception: Exception?) {
        Log.e(TAG, "Vosk onError", exception)
        onErrorOccurred("Vosk recognition error: ${exception?.message}")
        if (isListeningDesired) {
            // Restart after brief delay
            CoroutineScope(Dispatchers.Main).launch {
                kotlinx.coroutines.delay(1000)
                if (isListeningDesired) {
                    startListening()
                }
            }
        }
    }

    override fun onTimeout() {
        Log.d(TAG, "Vosk onTimeout")
        if (isListeningDesired) {
            startListening()
        }
    }

    fun downloadOfflineModel(scope: CoroutineScope) {
        if (_modelState.value is ModelState.Downloading) return

        _modelState.value = ModelState.Downloading(0)

        scope.launch(Dispatchers.IO) {
            val modelUrl = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
            val tempZipFile = File(context.cacheDir, "vosk_model_temp.zip")

            try {
                val client = OkHttpClient.Builder().build()
                val request = Request.Builder().url(modelUrl).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful || response.body == null) {
                    throw Exception("HTTP ${response.code}: failed to download Vosk model")
                }

                val body = response.body!!
                val contentLength = body.contentLength()
                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(tempZipFile)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    if (contentLength > 0) {
                        val progress = ((totalBytesRead * 100) / contentLength).toInt()
                        withContext(Dispatchers.Main) {
                            _modelState.value = ModelState.Downloading(progress)
                        }
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()

                // Unpack zip file
                unzipModel(tempZipFile, modelDir)
                tempZipFile.delete()

                withContext(Dispatchers.Main) {
                    loadModelFromDir(modelDir)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed downloading Vosk model", e)
                withContext(Dispatchers.Main) {
                    _modelState.value = ModelState.Error("Download failed: ${e.message}")
                    onErrorOccurred("Download failed: ${e.message}")
                }
            }
        }
    }

    private fun unzipModel(zipFile: File, targetDir: File) {
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        val zis = ZipInputStream(BufferedInputStream(zipFile.inputStream()))
        var entry = zis.nextEntry

        // Many Vosk zips have a root folder like "vosk-model-small-en-us-0.15/..."
        var rootPrefix = ""

        while (entry != null) {
            val entryName = entry.name
            if (rootPrefix.isEmpty() && entry.isDirectory) {
                rootPrefix = entryName
            }

            val relativeName = if (rootPrefix.isNotEmpty() && entryName.startsWith(rootPrefix)) {
                entryName.removePrefix(rootPrefix)
            } else {
                entryName
            }

            if (relativeName.isNotEmpty()) {
                val outFile = File(targetDir, relativeName)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    val fos = FileOutputStream(outFile)
                    zis.copyTo(fos)
                    fos.close()
                }
            }

            zis.closeEntry()
            entry = zis.nextEntry
        }
        zis.close()
    }

    fun destroy() {
        stopListening()
        try {
            model?.close()
        } catch (e: Exception) {
            // Ignore
        }
        model = null
    }

    companion object {
        private const val TAG = "VoskManager"
    }
}
