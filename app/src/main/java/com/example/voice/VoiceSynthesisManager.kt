package com.example.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.data.model.AppSettings
import java.util.Locale

class VoiceSynthesisManager(
    private val context: Context,
    private val onSpeechStatusChanged: (isSpeaking: Boolean) -> Unit = {}
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "TTS: Language not supported or missing data, trying default")
                tts?.setLanguage(Locale.getDefault())
            }
            isInitialized = true
            setupProgressListener()
            Log.d(TAG, "TTS initialized successfully")
        } else {
            Log.e(TAG, "TTS initialization failed: $status")
        }
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                onSpeechStatusChanged(true)
            }

            override fun onDone(utteranceId: String?) {
                onSpeechStatusChanged(false)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                onSpeechStatusChanged(false)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                onSpeechStatusChanged(false)
            }
        })
    }

    fun speak(text: String, settings: AppSettings) {
        if (!settings.ttsFeedbackEnabled || text.isBlank()) return
        if (!isInitialized) {
            Log.w(TAG, "TTS not initialized yet")
            return
        }

        try {
            tts?.setPitch(settings.ttsPitch)
            tts?.setSpeechRate(settings.ttsSpeechRate)

            val utteranceId = "voice_bridge_${System.currentTimeMillis()}"
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } catch (e: Exception) {
            Log.e(TAG, "Error speaking text", e)
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping TTS", e)
        }
        onSpeechStatusChanged(false)
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS", e)
        }
        tts = null
        isInitialized = false
    }

    companion object {
        private const val TAG = "VoiceSynthesisManager"
    }
}
