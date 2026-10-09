package com.example.data.model

enum class MatchMode(val label: String) {
    CONTAINS("Contains phrase"),
    EXACT("Exact match"),
    STARTS_WITH("Starts with"),
    ANY("Catch-all (Any speech)")
}

enum class TargetApp(val displayName: String, val defaultAction: String, val defaultExtra: String) {
    TASKER(
        displayName = "Tasker",
        defaultAction = "net.dinglisch.android.tasker.ACTION_SPEECH_COMMAND",
        defaultExtra = "voice_command"
    ),
    MACRODROID(
        displayName = "MacroDroid",
        defaultAction = "com.arlosoft.macrodroid.intent.action.TRIGGER",
        defaultExtra = "command"
    ),
    CUSTOM(
        displayName = "Custom Broadcast",
        defaultAction = "com.voicecontrol.ACTION_COMMAND",
        defaultExtra = "text"
    )
}

enum class OfflineEngineMode(val title: String, val subtitle: String) {
    VOSK_OFFLINE(
        title = "Vosk Offline STT (True Speech-to-Text)",
        subtitle = "Transforms spoken words into text 100% offline using embedded Vosk engine"
    ),
    STANDALONE_AUDIO(
        title = "Direct Acoustic VAD Engine (Dicio-style)",
        subtitle = "100% on-device AudioRecord VAD & acoustic phrase matcher"
    ),
    HYBRID_AUTO(
        title = "Hybrid Auto (Vosk + Fallbacks)",
        subtitle = "Uses Vosk offline STT, automatically falls back to Direct Audio Engine if needed"
    ),
    GOOGLE_STT(
        title = "Google SpeechRecognizer (Offline Mode)",
        subtitle = "Relies on downloaded Google voice acoustic language packs"
    )
}

data class AppSettings(
    val preferOffline: Boolean = true,
    val engineMode: OfflineEngineMode = OfflineEngineMode.VOSK_OFFLINE,
    val vadSensitivity: Float = 0.5f,
    val silenceTimeoutMs: Long = 800L,
    val speechLanguage: String = "en-US",
    val wakeWordEnabled: Boolean = false,
    val wakeWord: String = "computer",
    val defaultTarget: TargetApp = TargetApp.TASKER,
    val defaultAction: String = "net.dinglisch.android.tasker.ACTION_SPEECH_COMMAND",
    val defaultExtraKey: String = "voice_command",
    val defaultPackage: String = "",
    val ttsFeedbackEnabled: Boolean = true,
    val ttsPitch: Float = 1.0f,
    val ttsSpeechRate: Float = 1.0f,
    val ttsTemplate: String = "Command received: %s",
    val startOnBoot: Boolean = false
)

enum class ListeningStatus(val label: String) {
    STOPPED("Stopped"),
    INITIALIZING("Starting..."),
    LISTENING("Listening (Microphone Active)"),
    SPEECH_DETECTED("Hearing Speech..."),
    PROCESSING("Transcribing..."),
    DISPATCHING("Sending Intent..."),
    SPEAKING("Speaking Feedback..."),
    ERROR("Error / Retrying")
}

data class ServiceState(
    val isRunning: Boolean = false,
    val status: ListeningStatus = ListeningStatus.STOPPED,
    val activeEngine: String = "Offline Audio",
    val rmsDb: Float = 0f,
    val partialTranscript: String = "",
    val lastRecognizedText: String = "",
    val lastRecognizedTime: Long = 0L,
    val lastDispatchedTarget: String = "",
    val lastDispatchedAction: String = "",
    val lastDispatchedExtras: String = "",
    val errorMessage: String? = null
)
