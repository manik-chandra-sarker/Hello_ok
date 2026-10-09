package com.example.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.SettingsPreferences
import com.example.data.db.AppDatabase
import com.example.data.model.AppSettings
import com.example.data.model.ListeningStatus
import com.example.data.model.OfflineEngineMode
import com.example.data.model.ServiceState
import com.example.voice.AutomationDispatcher
import com.example.voice.OfflineAudioEngine
import com.example.voice.OfflineCommandMatcher
import com.example.voice.VoiceRecognitionManager
import com.example.voice.VoiceSynthesisManager
import com.example.voice.VoskManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class VoiceCaptureService : Service(), AudioManager.OnAudioFocusChangeListener {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private lateinit var settingsPreferences: SettingsPreferences
    private lateinit var recognitionManager: VoiceRecognitionManager
    private lateinit var offlineAudioEngine: OfflineAudioEngine
    private lateinit var offlineMatcher: OfflineCommandMatcher
    private lateinit var voskManager: VoskManager
    private lateinit var synthesisManager: VoiceSynthesisManager
    private lateinit var automationDispatcher: AutomationDispatcher
    private lateinit var db: AppDatabase

    private var currentSettings: AppSettings = AppSettings()
    private var isUsingDirectAudioEngine = false
    private var lastAudioFrameTime = System.currentTimeMillis()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "VoiceCaptureService onCreate")
        settingsPreferences = SettingsPreferences.getInstance(this)
        currentSettings = settingsPreferences.getSettings()
        db = AppDatabase.getInstance(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        acquireWakeLock()
        requestSystemAudioFocus()
        createNotificationChannel()

        automationDispatcher = AutomationDispatcher(this)
        offlineMatcher = OfflineCommandMatcher()

        voskManager = VoskManager(
            context = this,
            onTextRecognized = { text ->
                lastAudioFrameTime = System.currentTimeMillis()
                handleRecognizedSpeech(text)
            },
            onPartialTranscript = { partial ->
                lastAudioFrameTime = System.currentTimeMillis()
                _serviceState.update { it.copy(partialTranscript = partial) }
            },
            onStatusChanged = { status ->
                val state = when (status) {
                    "LISTENING" -> ListeningStatus.LISTENING
                    "SPEECH_DETECTED" -> ListeningStatus.SPEECH_DETECTED
                    "STOPPED" -> ListeningStatus.STOPPED
                    else -> ListeningStatus.LISTENING
                }
                _serviceState.update { it.copy(status = state) }
                updateNotification()
            },
            onErrorOccurred = { error ->
                Log.w(TAG, "Vosk error: $error")
                _serviceState.update { it.copy(errorMessage = error) }
                updateNotification()
            }
        )
        activeVoskManager = voskManager

        synthesisManager = VoiceSynthesisManager(this) { isSpeaking ->
            if (isSpeaking) {
                voskManager.pause(true)
                _serviceState.update { it.copy(status = ListeningStatus.SPEAKING) }
            } else {
                voskManager.pause(false)
                if (isUsingDirectAudioEngine) {
                    _serviceState.update { it.copy(status = ListeningStatus.LISTENING) }
                } else {
                    recognitionManager.resumeAfterTts()
                    _serviceState.update { it.copy(status = ListeningStatus.LISTENING) }
                }
            }
        }

        recognitionManager = VoiceRecognitionManager(
            context = this,
            onStateChanged = { stateName ->
                val status = when (stateName) {
                    "LISTENING" -> ListeningStatus.LISTENING
                    "SPEECH_DETECTED" -> ListeningStatus.SPEECH_DETECTED
                    "PROCESSING" -> ListeningStatus.PROCESSING
                    "SPEAKING" -> ListeningStatus.SPEAKING
                    "STOPPED" -> ListeningStatus.STOPPED
                    else -> ListeningStatus.INITIALIZING
                }
                _serviceState.update { it.copy(status = status) }
                updateNotification()
            },
            onRmsChanged = { rmsDb ->
                lastAudioFrameTime = System.currentTimeMillis()
                _serviceState.update { it.copy(rmsDb = rmsDb) }
            },
            onPartialTranscript = { partial ->
                _serviceState.update { it.copy(partialTranscript = partial) }
            },
            onFinalResult = { text ->
                handleRecognizedSpeech(text)
            },
            onErrorOccurred = { errorMsg ->
                _serviceState.update {
                    it.copy(
                        status = ListeningStatus.ERROR,
                        errorMessage = errorMsg
                    )
                }
                updateNotification()
            },
            onFatalOfflineError = {
                // If in hybrid mode and Google STT fails, auto-fallback to standalone audio engine or Vosk!
                if (!isUsingDirectAudioEngine) {
                    Log.w(TAG, "Speech recognition fatal error detected, switching to Direct Offline Audio Engine")
                    serviceScope.launch {
                        switchToDirectAudioEngine()
                    }
                }
            }
        )

        offlineAudioEngine = OfflineAudioEngine(
            onStateChanged = { stateName ->
                val status = when (stateName) {
                    "LISTENING" -> ListeningStatus.LISTENING
                    "SPEECH_DETECTED" -> ListeningStatus.SPEECH_DETECTED
                    "PROCESSING" -> ListeningStatus.PROCESSING
                    "STOPPED" -> ListeningStatus.STOPPED
                    else -> ListeningStatus.INITIALIZING
                }
                _serviceState.update { it.copy(status = status) }
                updateNotification()
            },
            onRmsChanged = { rmsDb ->
                lastAudioFrameTime = System.currentTimeMillis()
                _serviceState.update { it.copy(rmsDb = rmsDb) }
            },
            onSpeechUtteranceCaptured = { audioData, durationMs ->
                serviceScope.launch(Dispatchers.IO) {
                    val activeRules = db.voiceRuleDao().getActiveRules()
                    val matchResult = offlineMatcher.matchUtterance(audioData, durationMs, activeRules)
                    Log.d(TAG, "Offline matcher result: ${matchResult.matchedPhrase} (conf=${matchResult.confidence})")
                    serviceScope.launch(Dispatchers.Main) {
                        handleRecognizedSpeech(matchResult.matchedPhrase)
                    }
                }
            },
            onErrorOccurred = { errorMsg ->
                _serviceState.update {
                    it.copy(
                        status = ListeningStatus.ERROR,
                        errorMessage = errorMsg
                    )
                }
                updateNotification()
            }
        )

        // Observe settings changes
        serviceScope.launch {
            settingsPreferences.settingsFlow.collect { newSettings ->
                val prev = currentSettings
                currentSettings = newSettings
                if (_serviceState.value.isRunning &&
                    (prev.engineMode != newSettings.engineMode || prev.speechLanguage != newSettings.speechLanguage)
                ) {
                    stopListeningSession()
                    startListeningSession()
                }
            }
        }

        // Keep-Alive Watchdog for continuous background survival
        startKeepAliveWatchdog()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        Log.d(TAG, "onStartCommand action: $action")

        when (action) {
            ACTION_START -> {
                startForegroundServiceInternal()
                startListeningSession()
            }
            ACTION_STOP -> {
                stopListeningSession()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_SIMULATE_COMMAND -> {
                val simulatedText = intent?.getStringExtra(EXTRA_SIMULATED_TEXT) ?: "test command"
                handleRecognizedSpeech(simulatedText)
            }
        }

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (_serviceState.value.isRunning) {
            Log.d(TAG, "onTaskRemoved: Scheduling service revival via AlarmManager")
            val restartIntent = Intent(applicationContext, VoiceCaptureService::class.java).apply {
                action = ACTION_START
                setPackage(packageName)
            }
            val restartPendingIntent = PendingIntent.getService(
                applicationContext,
                101,
                restartIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 1000L,
                restartPendingIntent
            )
        }
    }

    private fun startKeepAliveWatchdog() {
        serviceScope.launch {
            while (isActive) {
                delay(12000L) // Check every 12 seconds
                if (_serviceState.value.isRunning) {
                    val silenceDuration = System.currentTimeMillis() - lastAudioFrameTime
                    if (silenceDuration > 30000L && _serviceState.value.status != ListeningStatus.SPEAKING) {
                        Log.d(TAG, "Watchdog: Refreshing audio session to ensure microphone stays alive")
                        if (currentSettings.engineMode == OfflineEngineMode.VOSK_OFFLINE && voskManager.isModelReady()) {
                            voskManager.stopListening()
                            delay(200)
                            voskManager.startListening()
                        } else if (isUsingDirectAudioEngine) {
                            offlineAudioEngine.stop()
                            delay(200)
                            offlineAudioEngine.start(currentSettings)
                        } else {
                            recognitionManager.stopListening()
                            delay(200)
                            recognitionManager.startListening(currentSettings)
                        }
                        lastAudioFrameTime = System.currentTimeMillis()
                    }
                }
            }
        }
    }

    private fun startForegroundServiceInternal() {
        val notification = buildNotification("Microphone active - Listening for offline voice commands")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        _serviceState.update { it.copy(isRunning = true, status = ListeningStatus.INITIALIZING) }
    }

    private fun startListeningSession() {
        currentSettings = settingsPreferences.getSettings()
        lastAudioFrameTime = System.currentTimeMillis()

        when (currentSettings.engineMode) {
            OfflineEngineMode.VOSK_OFFLINE -> {
                isUsingDirectAudioEngine = false
                recognitionManager.stopListening()
                offlineAudioEngine.stop()

                if (voskManager.isModelReady()) {
                    voskManager.startListening()
                    _serviceState.update {
                        it.copy(
                            isRunning = true,
                            status = ListeningStatus.LISTENING,
                            activeEngine = "Vosk Offline STT (Kaldi Engine)",
                            errorMessage = null
                        )
                    }
                } else {
                    Log.i(TAG, "Vosk model not downloaded yet. Starting Direct Audio Engine fallback.")
                    isUsingDirectAudioEngine = true
                    offlineAudioEngine.start(currentSettings)
                    _serviceState.update {
                        it.copy(
                            isRunning = true,
                            status = ListeningStatus.LISTENING,
                            activeEngine = "Direct Audio Engine (Download Vosk in Settings)",
                            errorMessage = "Vosk model not installed. Running Direct Audio Engine fallback."
                        )
                    }
                }
            }
            OfflineEngineMode.STANDALONE_AUDIO -> {
                isUsingDirectAudioEngine = true
                voskManager.stopListening()
                recognitionManager.stopListening()
                offlineAudioEngine.start(currentSettings)
                _serviceState.update {
                    it.copy(
                        isRunning = true,
                        status = ListeningStatus.LISTENING,
                        activeEngine = "Direct Audio Engine (Dicio-style)",
                        errorMessage = null
                    )
                }
            }
            OfflineEngineMode.HYBRID_AUTO -> {
                if (voskManager.isModelReady()) {
                    isUsingDirectAudioEngine = false
                    offlineAudioEngine.stop()
                    recognitionManager.stopListening()
                    voskManager.startListening()
                    _serviceState.update {
                        it.copy(
                            isRunning = true,
                            status = ListeningStatus.LISTENING,
                            activeEngine = "Vosk Offline STT (Kaldi Engine)",
                            errorMessage = null
                        )
                    }
                } else {
                    val hasRecognitionService = try {
                        SpeechRecognizer.isRecognitionAvailable(this) &&
                        packageManager.queryIntentServices(Intent("android.speech.RecognitionService"), 0).isNotEmpty()
                    } catch (e: Exception) {
                        false
                    }

                    if (!hasRecognitionService) {
                        Log.i(TAG, "No system SpeechRecognizer found on device, starting Direct Audio Engine directly.")
                        switchToDirectAudioEngine()
                    } else {
                        isUsingDirectAudioEngine = false
                        voskManager.stopListening()
                        offlineAudioEngine.stop()
                        recognitionManager.startListening(currentSettings)
                        _serviceState.update {
                            it.copy(
                               isRunning = true,
                               status = ListeningStatus.LISTENING,
                               activeEngine = "Google Offline STT",
                               errorMessage = null
                            )
                        }
                    }
                }
            }
            OfflineEngineMode.GOOGLE_STT -> {
                isUsingDirectAudioEngine = false
                voskManager.stopListening()
                offlineAudioEngine.stop()
                recognitionManager.startListening(currentSettings)
                _serviceState.update {
                    it.copy(
                        isRunning = true,
                        status = ListeningStatus.LISTENING,
                        activeEngine = "Google Offline STT",
                        errorMessage = null
                    )
                }
            }
        }

        updateNotification()
    }

    private fun switchToDirectAudioEngine() {
        isUsingDirectAudioEngine = true
        voskManager.stopListening()
        recognitionManager.stopListening()
        offlineAudioEngine.stop()
        offlineAudioEngine.start(currentSettings)
        _serviceState.update {
            it.copy(
                isRunning = true,
                status = ListeningStatus.LISTENING,
                activeEngine = "Direct Audio Engine (Dicio-style Active)",
                errorMessage = null
            )
        }
        updateNotification()
    }

    private fun stopListeningSession() {
        voskManager.stopListening()
        recognitionManager.stopListening()
        offlineAudioEngine.stop()
        synthesisManager.stop()
        isUsingDirectAudioEngine = false
        _serviceState.update {
            it.copy(
                isRunning = false,
                status = ListeningStatus.STOPPED,
                rmsDb = 0f,
                partialTranscript = ""
            )
        }
    }

    private fun handleRecognizedSpeech(text: String) {
        _serviceState.update {
            it.copy(
                status = ListeningStatus.DISPATCHING,
                lastRecognizedText = text,
                lastRecognizedTime = System.currentTimeMillis(),
                partialTranscript = ""
            )
        }
        updateNotification("Command heard: \"$text\"")

        automationDispatcher.processAndDispatch(text, currentSettings) { result ->
            _serviceState.update {
                it.copy(
                    status = ListeningStatus.LISTENING,
                    lastDispatchedTarget = result.target,
                    lastDispatchedAction = result.action,
                    lastDispatchedExtras = result.extrasSummary
                )
            }
            updateNotification("Sent to ${result.target}: ${result.extrasSummary}")

            // Spoken synthesis feedback
            if (currentSettings.ttsFeedbackEnabled && !result.spokenFeedback.isNullOrBlank()) {
                if (!isUsingDirectAudioEngine) {
                    recognitionManager.pauseForTts()
                }
                synthesisManager.speak(result.spokenFeedback, currentSettings)
            }
        }
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "VoiceBridge::AudioCaptureWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(24 * 60 * 60 * 1000L) // 24 hours
            }
            Log.d(TAG, "WakeLock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "Failed acquiring WakeLock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
            wakeLock = null
            Log.d(TAG, "WakeLock released")
        } catch (e: Exception) {
            Log.e(TAG, "Failed releasing WakeLock", e)
        }
    }

    private fun requestSystemAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setOnAudioFocusChangeListener(this)
                    .build()
                audioFocusRequest?.let { audioManager?.requestAudioFocus(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(
                    this,
                    AudioManager.STREAM_NOTIFICATION,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audio focus request exception", e)
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.d(TAG, "Audio focus lost permanently")
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d(TAG, "Audio focus lost transiently")
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "Audio focus gained/restored")
                if (_serviceState.value.isRunning && !isUsingDirectAudioEngine) {
                    recognitionManager.resumeAfterTts()
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VoiceBridge Microphone Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active background microphone & offline voice recognition status"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpenApp = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, VoiceCaptureService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val engineLabel = when (currentSettings.engineMode) {
            OfflineEngineMode.VOSK_OFFLINE -> "Vosk Offline STT"
            OfflineEngineMode.STANDALONE_AUDIO -> "Dicio-style Audio Engine"
            else -> "Voice Service"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VoiceBridge • $engineLabel")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(pendingOpenApp)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Service", pendingStop)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(text: String? = null) {
        val msg = text ?: when (_serviceState.value.status) {
            ListeningStatus.LISTENING -> "Listening for voice commands (Offline)"
            ListeningStatus.SPEECH_DETECTED -> "Audio detected..."
            ListeningStatus.PROCESSING -> "Recognizing speech..."
            ListeningStatus.DISPATCHING -> "Sending command intent..."
            ListeningStatus.SPEAKING -> "Speaking audio response..."
            ListeningStatus.ERROR -> "Recognizer error, reconnecting..."
            ListeningStatus.STOPPED -> "Service stopped"
            else -> "Voice recognition active"
        }

        val notification = buildNotification(msg)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "VoiceCaptureService onDestroy")
        stopListeningSession()
        voskManager.destroy()
        recognitionManager.destroy()
        offlineAudioEngine.stop()
        synthesisManager.shutdown()
        releaseWakeLock()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(this)
        }

        _serviceState.update { ServiceState() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "VoiceCaptureService"
        private const val CHANNEL_ID = "voice_bridge_service_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.action.START_VOICE_SERVICE"
        const val ACTION_STOP = "com.example.action.STOP_VOICE_SERVICE"
        const val ACTION_SIMULATE_COMMAND = "com.example.action.SIMULATE_COMMAND"
        const val EXTRA_SIMULATED_TEXT = "extra_simulated_text"

        private var activeVoskManager: VoskManager? = null
        val voskManagerInstance: VoskManager?
            get() = activeVoskManager

        private val _serviceState = MutableStateFlow(ServiceState())
        val serviceState: StateFlow<ServiceState> = _serviceState.asStateFlow()

        fun startService(context: Context) {
            val intent = Intent(context, VoiceCaptureService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, VoiceCaptureService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun simulateVoiceCommand(context: Context, text: String) {
            val intent = Intent(context, VoiceCaptureService::class.java).apply {
                action = ACTION_SIMULATE_COMMAND
                putExtra(EXTRA_SIMULATED_TEXT, text)
            }
            context.startService(intent)
        }
    }
}
