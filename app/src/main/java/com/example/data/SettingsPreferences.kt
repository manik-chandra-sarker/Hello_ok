package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.AppSettings
import com.example.data.model.OfflineEngineMode
import com.example.data.model.TargetApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("voice_bridge_prefs", Context.MODE_PRIVATE)

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<AppSettings> = _settingsFlow.asStateFlow()

    private fun loadSettings(): AppSettings {
        val targetName = prefs.getString(KEY_DEFAULT_TARGET, TargetApp.TASKER.name) ?: TargetApp.TASKER.name
        val target = try {
            TargetApp.valueOf(targetName)
        } catch (e: Exception) {
            TargetApp.TASKER
        }

        val engineModeName = prefs.getString(KEY_ENGINE_MODE, OfflineEngineMode.SHERPA_ONNX.name) ?: OfflineEngineMode.SHERPA_ONNX.name
        val engineMode = try {
            OfflineEngineMode.valueOf(engineModeName)
        } catch (e: Exception) {
            OfflineEngineMode.SHERPA_ONNX
        }

        return AppSettings(
            preferOffline = prefs.getBoolean(KEY_PREFER_OFFLINE, true),
            engineMode = engineMode,
            vadSensitivity = prefs.getFloat(KEY_VAD_SENSITIVITY, 0.5f),
            silenceTimeoutMs = prefs.getLong(KEY_SILENCE_TIMEOUT_MS, 1200L),
            commandEndDelayMs = prefs.getLong(KEY_COMMAND_END_DELAY_MS, 2000L),
            speechLanguage = prefs.getString(KEY_SPEECH_LANGUAGE, "en-US") ?: "en-US",
            wakeWordEnabled = prefs.getBoolean(KEY_WAKE_WORD_ENABLED, false),
            wakeWord = prefs.getString(KEY_WAKE_WORD, "computer") ?: "computer",
            defaultTarget = target,
            defaultAction = prefs.getString(KEY_DEFAULT_ACTION, target.defaultAction) ?: target.defaultAction,
            defaultExtraKey = prefs.getString(KEY_DEFAULT_EXTRA_KEY, target.defaultExtra) ?: target.defaultExtra,
            defaultPackage = prefs.getString(KEY_DEFAULT_PACKAGE, "") ?: "",
            ttsFeedbackEnabled = prefs.getBoolean(KEY_TTS_FEEDBACK_ENABLED, true),
            ttsPitch = prefs.getFloat(KEY_TTS_PITCH, 1.0f),
            ttsSpeechRate = prefs.getFloat(KEY_TTS_SPEECH_RATE, 1.0f),
            ttsTemplate = prefs.getString(KEY_TTS_TEMPLATE, "Command received: %s") ?: "Command received: %s",
            startOnBoot = prefs.getBoolean(KEY_START_ON_BOOT, false)
        )
    }

    fun updateSettings(newSettings: AppSettings) {
        prefs.edit().apply {
            putBoolean(KEY_PREFER_OFFLINE, newSettings.preferOffline)
            putString(KEY_ENGINE_MODE, newSettings.engineMode.name)
            putFloat(KEY_VAD_SENSITIVITY, newSettings.vadSensitivity)
            putLong(KEY_SILENCE_TIMEOUT_MS, newSettings.silenceTimeoutMs)
            putLong(KEY_COMMAND_END_DELAY_MS, newSettings.commandEndDelayMs)
            putString(KEY_SPEECH_LANGUAGE, newSettings.speechLanguage)
            putBoolean(KEY_WAKE_WORD_ENABLED, newSettings.wakeWordEnabled)
            putString(KEY_WAKE_WORD, newSettings.wakeWord)
            putString(KEY_DEFAULT_TARGET, newSettings.defaultTarget.name)
            putString(KEY_DEFAULT_ACTION, newSettings.defaultAction)
            putString(KEY_DEFAULT_EXTRA_KEY, newSettings.defaultExtraKey)
            putString(KEY_DEFAULT_PACKAGE, newSettings.defaultPackage)
            putBoolean(KEY_TTS_FEEDBACK_ENABLED, newSettings.ttsFeedbackEnabled)
            putFloat(KEY_TTS_PITCH, newSettings.ttsPitch)
            putFloat(KEY_TTS_SPEECH_RATE, newSettings.ttsSpeechRate)
            putString(KEY_TTS_TEMPLATE, newSettings.ttsTemplate)
            putBoolean(KEY_START_ON_BOOT, newSettings.startOnBoot)
            apply()
        }
        _settingsFlow.value = newSettings
    }

    fun getSettings(): AppSettings = _settingsFlow.value

    companion object {
        private const val KEY_PREFER_OFFLINE = "prefer_offline"
        private const val KEY_ENGINE_MODE = "engine_mode"
        private const val KEY_VAD_SENSITIVITY = "vad_sensitivity"
        private const val KEY_SILENCE_TIMEOUT_MS = "silence_timeout_ms"
        private const val KEY_COMMAND_END_DELAY_MS = "command_end_delay_ms"
        private const val KEY_SPEECH_LANGUAGE = "speech_language"
        private const val KEY_WAKE_WORD_ENABLED = "wake_word_enabled"
        private const val KEY_WAKE_WORD = "wake_word"
        private const val KEY_DEFAULT_TARGET = "default_target"
        private const val KEY_DEFAULT_ACTION = "default_action"
        private const val KEY_DEFAULT_EXTRA_KEY = "default_extra_key"
        private const val KEY_DEFAULT_PACKAGE = "default_package"
        private const val KEY_TTS_FEEDBACK_ENABLED = "tts_feedback_enabled"
        private const val KEY_TTS_PITCH = "tts_pitch"
        private const val KEY_TTS_SPEECH_RATE = "tts_speech_rate"
        private const val KEY_TTS_TEMPLATE = "tts_template"
        private const val KEY_START_ON_BOOT = "start_on_boot"

        @Volatile
        private var INSTANCE: SettingsPreferences? = null

        fun getInstance(context: Context): SettingsPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
