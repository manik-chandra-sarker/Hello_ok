package com.example.voice

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.db.AppDatabase
import com.example.data.db.CommandLogEntity
import com.example.data.db.VoiceRuleEntity
import com.example.data.model.AppSettings
import com.example.data.model.MatchMode
import com.example.data.model.TargetApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AutomationDispatcher(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    data class DispatchResult(
        val success: Boolean,
        val target: String,
        val action: String,
        val extrasSummary: String,
        val spokenFeedback: String?,
        val matchedRule: VoiceRuleEntity?
    )

    fun processAndDispatch(rawSpokenText: String, settings: AppSettings, onResult: (DispatchResult) -> Unit) {
        val trimmed = rawSpokenText.trim()
        if (trimmed.isBlank()) return

        scope.launch {
            // Check wake word if enabled
            val processedText: String
            if (settings.wakeWordEnabled && settings.wakeWord.isNotBlank()) {
                val wake = settings.wakeWord.trim().lowercase()
                val lowerSpoken = trimmed.lowercase()
                if (!lowerSpoken.startsWith(wake) && !lowerSpoken.contains(wake)) {
                    Log.d(TAG, "Ignored: does not contain wake word '$wake'")
                    return@launch
                }
                // Strip wake word from command
                processedText = trimmed.replaceFirst(Regex("(?i)\\b$wake\\b[,\\s]*"), "").trim().ifBlank { trimmed }
            } else {
                processedText = trimmed
            }

            val activeRules = db.voiceRuleDao().getActiveRules()
            val matchedRule = findMatchingRule(activeRules, processedText)

            val result = if (matchedRule != null) {
                dispatchRuleIntent(processedText, matchedRule, settings)
            } else {
                dispatchDefaultIntent(processedText, settings)
            }

            // Save to logs
            db.commandLogDao().insertLog(
                CommandLogEntity(
                    rawText = trimmed,
                    matchedRuleName = matchedRule?.name,
                    targetApp = result.target,
                    intentAction = result.action,
                    extrasSummary = result.extrasSummary,
                    success = result.success
                )
            )

            onResult(result)
        }
    }

    private fun findMatchingRule(rules: List<VoiceRuleEntity>, text: String): VoiceRuleEntity? {
        val lowerText = text.lowercase()
        return rules.firstOrNull { rule ->
            val trigger = rule.triggerPhrase.trim().lowercase()
            when (rule.matchMode) {
                MatchMode.EXACT -> lowerText == trigger
                MatchMode.CONTAINS -> lowerText.contains(trigger)
                MatchMode.STARTS_WITH -> lowerText.startsWith(trigger)
                MatchMode.ANY -> true
            }
        }
    }

    private fun dispatchRuleIntent(
        processedText: String,
        rule: VoiceRuleEntity,
        settings: AppSettings
    ): DispatchResult {
        val action = rule.customAction.ifBlank {
            when (rule.targetApp) {
                TargetApp.TASKER -> TargetApp.TASKER.defaultAction
                TargetApp.MACRODROID -> TargetApp.MACRODROID.defaultAction
                TargetApp.CUSTOM -> settings.defaultAction
            }
        }

        val extraKey = rule.customExtraKey.ifBlank {
            when (rule.targetApp) {
                TargetApp.TASKER -> TargetApp.TASKER.defaultExtra
                TargetApp.MACRODROID -> TargetApp.MACRODROID.defaultExtra
                TargetApp.CUSTOM -> settings.defaultExtraKey
            }
        }

        val payload = rule.customPayload.ifBlank { processedText }

        // Construct high-priority foreground broadcast intent for Android 11+
        val intent = Intent(action).apply {
            flags = Intent.FLAG_INCLUDE_STOPPED_PACKAGES or Intent.FLAG_RECEIVER_FOREGROUND

            // Primary configured extra
            putExtra(extraKey, payload)

            // Automation friendly extras
            putExtra("voice_command", payload)
            putExtra("voice_text", processedText)
            putExtra("raw_text", processedText)
            putExtra("command", payload)
            putExtra("text", payload)
            putExtra("rule_name", rule.name)
            putExtra("timestamp", System.currentTimeMillis())

            when (rule.targetApp) {
                TargetApp.TASKER -> {
                    putExtra("net.dinglisch.android.tasker.EXTRA_SPEECH_COMMAND", payload)
                    putExtra("%voice_command", payload)
                    putExtra("%voice_text", processedText)
                    putExtra("%command", payload)
                    putExtra("%text", payload)
                }
                TargetApp.MACRODROID -> {
                    putExtra("macro_param", payload)
                    putExtra("macro_name", payload)
                }
                TargetApp.CUSTOM -> {
                    if (settings.defaultPackage.isNotBlank()) {
                        setPackage(settings.defaultPackage)
                    }
                }
            }
        }

        val success = sendBroadcastWithFallback(intent, rule.targetApp)

        val spokenFeedback = if (rule.customTtsFeedback.isNotBlank()) {
            rule.customTtsFeedback
        } else {
            String.format(settings.ttsTemplate, processedText)
        }

        return DispatchResult(
            success = success,
            target = rule.targetApp.displayName,
            action = action,
            extrasSummary = "$extraKey=\"$payload\"",
            spokenFeedback = spokenFeedback,
            matchedRule = rule
        )
    }

    private fun dispatchDefaultIntent(
        processedText: String,
        settings: AppSettings
    ): DispatchResult {
        val action = settings.defaultAction.ifBlank { settings.defaultTarget.defaultAction }
        val extraKey = settings.defaultExtraKey.ifBlank { settings.defaultTarget.defaultExtra }

        val intent = Intent(action).apply {
            flags = Intent.FLAG_INCLUDE_STOPPED_PACKAGES or Intent.FLAG_RECEIVER_FOREGROUND

            putExtra(extraKey, processedText)
            putExtra("voice_command", processedText)
            putExtra("voice_text", processedText)
            putExtra("raw_text", processedText)
            putExtra("command", processedText)
            putExtra("text", processedText)
            putExtra("timestamp", System.currentTimeMillis())

            when (settings.defaultTarget) {
                TargetApp.TASKER -> {
                    putExtra("net.dinglisch.android.tasker.EXTRA_SPEECH_COMMAND", processedText)
                    putExtra("%voice_command", processedText)
                    putExtra("%voice_text", processedText)
                    putExtra("%command", processedText)
                    putExtra("%text", processedText)
                }
                TargetApp.MACRODROID -> {
                    putExtra("macro_param", processedText)
                }
                TargetApp.CUSTOM -> {
                    if (settings.defaultPackage.isNotBlank()) {
                        setPackage(settings.defaultPackage)
                    }
                }
            }
        }

        val success = sendBroadcastWithFallback(intent, settings.defaultTarget)
        val spokenFeedback = String.format(settings.ttsTemplate, processedText)

        return DispatchResult(
            success = success,
            target = settings.defaultTarget.displayName,
            action = action,
            extrasSummary = "$extraKey=\"$processedText\"",
            spokenFeedback = spokenFeedback,
            matchedRule = null
        )
    }

    /**
     * Broadcasts intent with high priority and direct package targets for Tasker/MacroDroid.
     */
    private fun sendBroadcastWithFallback(intent: Intent, targetApp: TargetApp): Boolean {
        var dispatched = false
        try {
            // General broadcast
            context.sendBroadcast(intent)
            dispatched = true
            Log.d(TAG, "Sent high-priority broadcast intent: action=${intent.action}")

            // Directed package broadcast for guaranteed Android 11 delivery
            when (targetApp) {
                TargetApp.TASKER -> {
                    for (pkg in listOf("net.dinglisch.android.taskerm", "net.dinglisch.android.tasker")) {
                        try {
                            val targeted = Intent(intent).setPackage(pkg)
                            context.sendBroadcast(targeted)
                            Log.d(TAG, "Sent targeted Tasker broadcast to $pkg")
                        } catch (e: Exception) {
                            // Package might not be installed, ignore
                        }
                    }
                }
                TargetApp.MACRODROID -> {
                    try {
                        val targeted = Intent(intent).setPackage("com.arlosoft.macrodroid")
                        context.sendBroadcast(targeted)
                        Log.d(TAG, "Sent targeted MacroDroid broadcast to com.arlosoft.macrodroid")
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
                TargetApp.CUSTOM -> { /* Handled above */ }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed sending broadcast intent", e)
        }
        return dispatched
    }

    companion object {
        private const val TAG = "AutomationDispatcher"
    }
}
