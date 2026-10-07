package com.example.voice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.data.model.AppSettings

class VoiceRecognitionManager(
    private val context: Context,
    private val onStateChanged: (state: String) -> Unit,
    private val onRmsChanged: (rmsDb: Float) -> Unit,
    private val onPartialTranscript: (text: String) -> Unit,
    private val onFinalResult: (text: String) -> Unit,
    private val onErrorOccurred: (errorMessage: String) -> Unit,
    private val onFatalOfflineError: (() -> Unit)? = null
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListeningDesired = false
    private var isCurrentlyRecognizing = false
    private var isPausedForTts = false
    private var currentSettings: AppSettings? = null
    private var consecutiveErrors = 0

    // Google Recognition Service Component
    private val googleComponent = ComponentName(
        "com.google.android.googlequicksearchbox",
        "com.google.android.voicesearch.serviceapi.GoogleRecognitionService"
    )

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "onReadyForSpeech")
            isCurrentlyRecognizing = true
            consecutiveErrors = 0
            onStateChanged("LISTENING")
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "onBeginningOfSpeech")
            onStateChanged("SPEECH_DETECTED")
        }

        override fun onRmsChanged(rmsdB: Float) {
            onRmsChanged.invoke(rmsdB)
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            // Raw audio buffer
        }

        override fun onEndOfSpeech() {
            Log.d(TAG, "onEndOfSpeech")
            onStateChanged("PROCESSING")
        }

        override fun onError(errorCode: Int) {
            isCurrentlyRecognizing = false
            val errorMsg = getErrorMessage(errorCode)
            Log.w(TAG, "SpeechRecognizer onError: $errorCode ($errorMsg)")

            val isSilentTimeout = errorCode == SpeechRecognizer.ERROR_NO_MATCH ||
                    errorCode == SpeechRecognizer.ERROR_SPEECH_TIMEOUT

            if (!isSilentTimeout) {
                consecutiveErrors++
                onErrorOccurred(errorMsg)

                // Error 10 = ERROR_CANNOT_LISTEN_TO_SPEECH or binding failure
                // Error 2 = ERROR_NETWORK, Error 4 = ERROR_SERVER
                if (errorCode == 10 ||
                    errorCode == SpeechRecognizer.ERROR_NETWORK ||
                    errorCode == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
                    errorCode == SpeechRecognizer.ERROR_SERVER ||
                    consecutiveErrors >= 2
                ) {
                    Log.w(TAG, "Fatal recognition or binding error ($errorCode), switching to Direct Offline Audio Engine")
                    isListeningDesired = false
                    mainHandler.removeCallbacksAndMessages(null)
                    onFatalOfflineError?.invoke()
                    return
                }
            }

            if (!isListeningDesired || isPausedForTts) {
                return
            }

            // Smooth restart loop only for harmless silence timeouts
            val restartDelay = if (isSilentTimeout) 200L else 600L

            mainHandler.postDelayed({
                if (isListeningDesired && !isPausedForTts) {
                    startListeningInternal()
                }
            }, restartDelay)
        }

        override fun onResults(results: Bundle?) {
            isCurrentlyRecognizing = false
            consecutiveErrors = 0
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val recognizedText = matches?.firstOrNull()?.trim()

            if (!recognizedText.isNullOrBlank()) {
                Log.d(TAG, "Speech recognized: $recognizedText")
                onFinalResult(recognizedText)
            } else {
                Log.d(TAG, "Empty results received")
            }

            // Restart listening continuously
            if (isListeningDesired && !isPausedForTts) {
                mainHandler.postDelayed({
                    if (isListeningDesired && !isPausedForTts) {
                        startListeningInternal()
                    }
                }, 200L)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partial = matches?.firstOrNull()?.trim() ?: ""
            if (partial.isNotBlank()) {
                onPartialTranscript(partial)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {
            // Extended events
        }
    }

    fun startListening(settings: AppSettings) {
        currentSettings = settings
        isListeningDesired = true
        isPausedForTts = false
        consecutiveErrors = 0

        mainHandler.post {
            ensureRecognizer()
            startListeningInternal()
        }
    }

    fun stopListening() {
        isListeningDesired = false
        mainHandler.removeCallbacksAndMessages(null)

        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping recognizer", e)
            }
            isCurrentlyRecognizing = false
            onStateChanged("STOPPED")
        }
    }

    fun pauseForTts() {
        isPausedForTts = true
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                Log.e(TAG, "Error cancelling for TTS", e)
            }
            isCurrentlyRecognizing = false
            onStateChanged("SPEAKING")
        }
    }

    fun resumeAfterTts() {
        isPausedForTts = false
        if (isListeningDesired) {
            mainHandler.postDelayed({
                if (isListeningDesired && !isPausedForTts) {
                    startListeningInternal()
                }
            }, 300L)
        }
    }

    private fun isComponentAvailable(component: ComponentName): Boolean {
        return try {
            val intent = Intent("android.speech.RecognitionService").setComponent(component)
            val services = context.packageManager.queryIntentServices(intent, 0)
            services.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    private fun hasAnyRecognitionService(): Boolean {
        return try {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) return false
            val intent = Intent("android.speech.RecognitionService")
            val services = context.packageManager.queryIntentServices(intent, 0)
            services.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    private fun ensureRecognizer() {
        if (speechRecognizer == null) {
            speechRecognizer = createRecognizerInstance()
            speechRecognizer?.setRecognitionListener(recognitionListener)
        }
    }

    private fun recreateRecognizer() {
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying recognizer", e)
        }
        speechRecognizer = createRecognizerInstance()
        speechRecognizer?.setRecognitionListener(recognitionListener)
    }

    private fun createRecognizerInstance(): SpeechRecognizer? {
        if (!hasAnyRecognitionService()) {
            Log.w(TAG, "No system recognition service found")
            return null
        }

        return try {
            if (isComponentAvailable(googleComponent)) {
                try {
                    SpeechRecognizer.createSpeechRecognizer(context, googleComponent)
                } catch (e: Exception) {
                    Log.w(TAG, "Google recognition service unavailable, using default", e)
                    SpeechRecognizer.createSpeechRecognizer(context)
                }
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed creating SpeechRecognizer", e)
            null
        }
    }

    private fun startListeningInternal() {
        if (!isListeningDesired || isPausedForTts) return

        try {
            ensureRecognizer()
            val recognizer = speechRecognizer
            if (recognizer == null) {
                Log.w(TAG, "No SpeechRecognizer available, triggering offline engine fallback")
                isListeningDesired = false
                onFatalOfflineError?.invoke()
                return
            }

            val intent = createRecognizerIntent(currentSettings)
            recognizer.startListening(intent)
            Log.d(TAG, "startListening invoked successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting listening, triggering fallback", e)
            isListeningDesired = false
            onFatalOfflineError?.invoke()
        }
    }

    private fun createRecognizerIntent(settings: AppSettings?): Intent {
        val lang = settings?.speechLanguage ?: "en-US"
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)

            // Offline preference flags
            val preferOffline = settings?.preferOffline ?: true
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
            putExtra("android.speech.extra.PREFER_OFFLINE", preferOffline)

            // Language specifications
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, lang)
        }
    }

    fun destroy() {
        isListeningDesired = false
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.e(TAG, "Error destroying speechRecognizer", e)
            }
            speechRecognizer = null
        }
    }

    private fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            10 -> "Recognition service bind failed / not supported (Error 10)"
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions (Microphone required)"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout (No speech input)"
            else -> "Speech recognition error ($errorCode)"
        }
    }

    companion object {
        private const val TAG = "VoiceRecognitionMgr"
    }
}
