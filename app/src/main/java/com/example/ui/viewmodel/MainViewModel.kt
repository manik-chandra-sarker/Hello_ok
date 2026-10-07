package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SettingsPreferences
import com.example.data.db.AppDatabase
import com.example.data.db.CommandLogEntity
import com.example.data.db.VoiceRuleEntity
import com.example.data.model.AppSettings
import com.example.data.model.ServiceState
import com.example.service.VoiceCaptureService
import com.example.voice.VoiceSynthesisManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val prefs = SettingsPreferences.getInstance(application)
    private val ttsTester = VoiceSynthesisManager(application)

    val serviceState: StateFlow<ServiceState> = VoiceCaptureService.serviceState

    val settings: StateFlow<AppSettings> = prefs.settingsFlow

    val rules: StateFlow<List<VoiceRuleEntity>> = db.voiceRuleDao()
        .getAllRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val logs: StateFlow<List<CommandLogEntity>> = db.commandLogDao()
        .getAllLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleService() {
        if (serviceState.value.isRunning) {
            VoiceCaptureService.stopService(getApplication())
        } else {
            VoiceCaptureService.startService(getApplication())
        }
    }

    fun startService() {
        VoiceCaptureService.startService(getApplication())
    }

    fun stopService() {
        VoiceCaptureService.stopService(getApplication())
    }

    fun updateSettings(newSettings: AppSettings) {
        prefs.updateSettings(newSettings)
    }

    fun saveRule(rule: VoiceRuleEntity) {
        viewModelScope.launch {
            if (rule.id == 0L) {
                db.voiceRuleDao().insertRule(rule)
            } else {
                db.voiceRuleDao().updateRule(rule)
            }
        }
    }

    fun toggleRuleEnabled(rule: VoiceRuleEntity) {
        viewModelScope.launch {
            db.voiceRuleDao().updateRule(rule.copy(isEnabled = !rule.isEnabled))
        }
    }

    fun deleteRule(rule: VoiceRuleEntity) {
        viewModelScope.launch {
            db.voiceRuleDao().deleteRule(rule)
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            db.commandLogDao().clearLogs()
        }
    }

    fun simulateVoiceCommand(text: String) {
        if (serviceState.value.isRunning) {
            VoiceCaptureService.simulateVoiceCommand(getApplication(), text)
        } else {
            // Also allow simulating even when service is paused
            VoiceCaptureService.startService(getApplication())
            viewModelScope.launch {
                kotlinx.coroutines.delay(400)
                VoiceCaptureService.simulateVoiceCommand(getApplication(), text)
            }
        }
    }

    fun resendLogIntent(log: CommandLogEntity) {
        val app = getApplication<Application>()
        try {
            val intent = Intent(log.intentAction).apply {
                flags = Intent.FLAG_INCLUDE_STOPPED_PACKAGES
                putExtra("voice_command", log.rawText)
                putExtra("command", log.rawText)
                putExtra("%voice_command", log.rawText)
                putExtra("%voice_text", log.rawText)
                putExtra("timestamp", System.currentTimeMillis())
            }
            app.sendBroadcast(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun testTts(text: String) {
        val current = settings.value
        ttsTester.speak(text, current)
    }

    override fun onCleared() {
        super.onCleared()
        ttsTester.shutdown()
    }
}
