package com.example.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.example.data.model.AppSettings
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

class SherpaOnnxManager(
    private val context: Context,
    private val onTextRecognized: (text: String) -> Unit,
    private val onPartialTranscript: (text: String) -> Unit,
    private val onStatusChanged: (status: String) -> Unit,
    private val onRmsChanged: (rmsDb: Float) -> Unit,
    private val onErrorOccurred: (error: String) -> Unit
) {

    sealed class ModelState {
        object NotInstalled : ModelState()
        data class Downloading(val progressPercent: Int) : ModelState()
        object Ready : ModelState()
        data class Error(val message: String) : ModelState()
    }

    private val _modelState = MutableStateFlow<ModelState>(ModelState.NotInstalled)
    val modelState: StateFlow<ModelState> = _modelState.asStateFlow()

    private var recognizer: OnlineRecognizer? = null
    private var stream: OnlineStream? = null

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private val isRunning = AtomicBoolean(false)
    private var isPaused = false

    private var currentSettings: AppSettings = AppSettings()
    private var lastSpokenText = ""
    private var lastSpeechDetectedTime = 0L

    val modelDir: File
        get() = File(context.filesDir, "sherpa-onnx-model")

    init {
        checkExistingModel()
    }

    fun isModelReady(): Boolean {
        return recognizer != null || (modelDir.exists() && isValidModelDir(modelDir))
    }

    fun checkExistingModel() {
        val dir = modelDir
        if (dir.exists() && isValidModelDir(dir)) {
            loadModelFromDir(dir, currentSettings)
        } else {
            _modelState.value = ModelState.NotInstalled
        }
    }

    private fun isValidModelDir(dir: File): Boolean {
        val files = dir.walkTopDown().filter { it.isFile }.toList()
        val hasEncoder = files.any { it.name.contains("encoder") && it.name.endsWith(".onnx") }
        val hasDecoder = files.any { it.name.contains("decoder") && it.name.endsWith(".onnx") }
        val hasJoiner = files.any { it.name.contains("joiner") && it.name.endsWith(".onnx") }
        val hasTokens = files.any { it.name.contains("tokens") }
        return hasEncoder && hasDecoder && hasJoiner && hasTokens
    }

    fun loadModelFromDir(dir: File, settings: AppSettings) {
        currentSettings = settings
        try {
            val allFiles = dir.walkTopDown().filter { it.isFile }.toList()
            val encoder = allFiles.first { it.name.contains("encoder") && it.name.endsWith(".onnx") }
            val decoder = allFiles.first { it.name.contains("decoder") && it.name.endsWith(".onnx") }
            val joiner = allFiles.first { it.name.contains("joiner") && it.name.endsWith(".onnx") }
            val tokens = allFiles.first { it.name.contains("tokens") }

            Log.d(TAG, "Loading Sherpa-ONNX model files from ${dir.absolutePath}")

            val transducerConfig = OnlineTransducerModelConfig().apply {
                this.encoder = encoder.absolutePath
                this.decoder = decoder.absolutePath
                this.joiner = joiner.absolutePath
            }

            val modelConfig = OnlineModelConfig().apply {
                this.transducer = transducerConfig
                this.tokens = tokens.absolutePath
                this.numThreads = 2
                this.debug = false
                this.provider = "cpu"
                this.modelType = "zipformer"
            }

            val featConfig = FeatureConfig().apply {
                this.sampleRate = 16000
                this.featureDim = 80
            }

            // Command end delay in seconds (e.g. 2.0s or user configured)
            val silenceSeconds = (settings.commandEndDelayMs / 1000f).coerceIn(0.5f, 10.0f)
            val endpointConfig = EndpointConfig().apply {
                this.rule1 = EndpointRule(false, silenceSeconds + 0.8f, 0.0f)
                this.rule2 = EndpointRule(true, silenceSeconds, 0.0f)
                this.rule3 = EndpointRule(false, 0.0f, 30.0f)
            }

            val config = OnlineRecognizerConfig().apply {
                this.featConfig = featConfig
                this.modelConfig = modelConfig
                this.endpointConfig = endpointConfig
                this.enableEndpoint = true
            }

            recognizer?.release()
            // null AssetManager instructs Sherpa-ONNX to load from filesystem paths
            recognizer = OnlineRecognizer(null, config)
            _modelState.value = ModelState.Ready
            Log.d(TAG, "Sherpa-ONNX OnlineRecognizer initialized successfully with commandEndDelayMs = ${settings.commandEndDelayMs}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed loading Sherpa-ONNX model", e)
            _modelState.value = ModelState.Error("Model load failed: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun startListening(settings: AppSettings) {
        currentSettings = settings
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            onErrorOccurred("RECORD_AUDIO permission is not granted")
            return
        }

        if (recognizer == null) {
            if (isValidModelDir(modelDir)) {
                loadModelFromDir(modelDir, settings)
            } else {
                onErrorOccurred("Sherpa-ONNX model not installed. Please download the offline model.")
                return
            }
        }

        if (isRunning.get()) return

        val rec = recognizer ?: return
        stream?.release()
        stream = rec.createStream()
        lastSpokenText = ""
        lastSpeechDetectedTime = 0L

        val sampleRate = 16000
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                onErrorOccurred("Failed initializing AudioRecord for Sherpa-ONNX")
                return
            }

            audioRecord?.startRecording()
            isRunning.set(true)
            onStatusChanged("LISTENING")

            recordingThread = Thread({
                processAudioStream(sampleRate)
            }, "SherpaOnnxThread").apply {
                priority = Thread.NORM_PRIORITY + 2
                start()
            }

            Log.d(TAG, "Sherpa-ONNX audio stream listening started")
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting Sherpa-ONNX AudioRecord", e)
            onErrorOccurred("Mic start failed: ${e.message}")
        }
    }

    private fun processAudioStream(sampleRate: Int) {
        val shortBuffer = ShortArray(1024)
        val floatBuffer = FloatArray(1024)
        var consecutiveErrors = 0

        while (isRunning.get()) {
            if (isPaused) {
                try {
                    Thread.sleep(60L)
                } catch (e: InterruptedException) {
                    break
                }
                continue
            }

            val record = audioRecord ?: break
            val readCount = record.read(shortBuffer, 0, shortBuffer.size)

            if (readCount <= 0) {
                consecutiveErrors++
                if (readCount == AudioRecord.ERROR_INVALID_OPERATION || readCount == AudioRecord.ERROR_BAD_VALUE) {
                    Log.w(TAG, "AudioRecord read returned error code $readCount")
                }
                if (consecutiveErrors >= 10) {
                    Log.w(TAG, "AudioRecord read repeated errors, backing off")
                    try {
                        Thread.sleep(300L)
                    } catch (e: InterruptedException) {
                        break
                    }
                    consecutiveErrors = 0
                } else {
                    try {
                        Thread.sleep(30L)
                    } catch (e: InterruptedException) {
                        break
                    }
                }
                continue
            }
            consecutiveErrors = 0

            // Calculate RMS for visualizer
            var sumSquare = 0.0
            for (i in 0 until readCount) {
                val sample = shortBuffer[i]
                sumSquare += sample * sample
                floatBuffer[i] = sample / 32768.0f
            }
            val rms = kotlin.math.sqrt(sumSquare / readCount).toFloat()
            val rmsDb = if (rms > 1f) (20 * kotlin.math.log10(rms.toDouble())).toFloat() else 0f
            onRmsChanged(rmsDb)

            val curStream = stream ?: continue
            val rec = recognizer ?: continue

            curStream.acceptWaveform(floatBuffer.copyOf(readCount), sampleRate)

            while (rec.isReady(curStream)) {
                rec.decode(curStream)
            }

            val text = rec.getResult(curStream).text.trim()
            val now = System.currentTimeMillis()

            if (text.isNotBlank()) {
                if (text != lastSpokenText) {
                    lastSpokenText = text
                    lastSpeechDetectedTime = now
                    onPartialTranscript(text)
                    onStatusChanged("SPEECH_DETECTED")
                }

                // Give user full configurable time delay (e.g. 2000ms - 5000ms) to pause and finish command!
                val silenceElapsed = now - lastSpeechDetectedTime
                val isEndpointReached = rec.isEndpoint(curStream)

                // Only finalize after user's configured delay has elapsed:
                if (silenceElapsed >= currentSettings.commandEndDelayMs || (isEndpointReached && silenceElapsed >= currentSettings.commandEndDelayMs)) {
                    Log.d(TAG, "Command completed after ${silenceElapsed}ms silence: \"$text\"")
                    val finalizedText = text
                    lastSpokenText = ""
                    lastSpeechDetectedTime = 0L

                    // Reset stream for next voice command
                    rec.reset(curStream)
                    onTextRecognized(finalizedText)
                    onStatusChanged("LISTENING")
                }
            } else if (rec.isEndpoint(curStream)) {
                rec.reset(curStream)
            }
        }
    }

    fun stopListening() {
        isRunning.set(false)
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audioRecord", e)
        }
        audioRecord = null

        recordingThread?.interrupt()
        recordingThread = null

        stream?.release()
        stream = null

        onStatusChanged("STOPPED")
    }

    fun pause(pause: Boolean) {
        isPaused = pause
    }

    fun updateCommandDelay(newDelayMs: Long) {
        currentSettings = currentSettings.copy(commandEndDelayMs = newDelayMs)
        Log.d(TAG, "Updated commandEndDelayMs to $newDelayMs ms")
        if (recognizer != null && isValidModelDir(modelDir)) {
            loadModelFromDir(modelDir, currentSettings)
        }
    }

    fun downloadOfflineModel(scope: CoroutineScope) {
        if (_modelState.value is ModelState.Downloading) return
        _modelState.value = ModelState.Downloading(0)

        scope.launch(Dispatchers.IO) {
            val modelUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-en-20M-2023-02-17.tar.bz2"
            val tempArchive = File(context.cacheDir, "sherpa_model_temp.tar.bz2")

            try {
                val client = OkHttpClient.Builder().build()
                val request = Request.Builder().url(modelUrl).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful || response.body == null) {
                    throw Exception("HTTP ${response.code}: failed to download Sherpa-ONNX model")
                }

                val body = response.body!!
                val contentLength = body.contentLength()
                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(tempArchive)

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

                // Extract archive to modelDir
                unpackArchive(tempArchive, modelDir)
                tempArchive.delete()

                withContext(Dispatchers.Main) {
                    loadModelFromDir(modelDir, currentSettings)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed downloading Sherpa-ONNX model", e)
                withContext(Dispatchers.Main) {
                    _modelState.value = ModelState.Error("Download failed: ${e.message}")
                    onErrorOccurred("Download failed: ${e.message}")
                }
            }
        }
    }

    private fun unpackArchive(archiveFile: File, targetDir: File) {
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        if (archiveFile.name.endsWith(".zip", ignoreCase = true)) {
            ZipInputStream(BufferedInputStream(archiveFile.inputStream())).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val relativePath = entry.name.substringAfter("/")
                    if (relativePath.isNotBlank()) {
                        val file = File(targetDir, relativePath)
                        if (entry.isDirectory) {
                            file.mkdirs()
                        } else {
                            file.parentFile?.mkdirs()
                            FileOutputStream(file).use { fos ->
                                zis.copyTo(fos)
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } else {
            val inStream = if (archiveFile.name.endsWith(".bz2", ignoreCase = true)) {
                BZip2CompressorInputStream(BufferedInputStream(archiveFile.inputStream()))
            } else if (archiveFile.name.endsWith(".gz", ignoreCase = true)) {
                GZIPInputStream(BufferedInputStream(archiveFile.inputStream()))
            } else {
                BufferedInputStream(archiveFile.inputStream())
            }

            val tarIn = TarArchiveInputStream(inStream)
            var entry = tarIn.nextTarEntry
            while (entry != null) {
                val relativePath = entry.name.substringAfter("/")
                if (relativePath.isNotBlank()) {
                    val file = File(targetDir, relativePath)
                    if (entry.isDirectory) {
                        file.mkdirs()
                    } else {
                        file.parentFile?.mkdirs()
                        FileOutputStream(file).use { fos ->
                            tarIn.copyTo(fos)
                        }
                    }
                }
                entry = tarIn.nextTarEntry
            }
            tarIn.close()
        }
    }

    fun destroy() {
        stopListening()
        recognizer?.release()
        recognizer = null
    }

    companion object {
        private const val TAG = "SherpaOnnxManager"
    }
}
