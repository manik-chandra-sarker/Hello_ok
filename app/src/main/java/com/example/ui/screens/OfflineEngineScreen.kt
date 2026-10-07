package com.example.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AppSettings
import com.example.data.model.OfflineEngineMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineEngineScreen(
    settings: AppSettings,
    onUpdateSettings: (AppSettings) -> Unit,
    onTestTts: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var languageExpanded by remember { mutableStateOf(false) }

    val languages = listOf(
        "en-US" to "English (United States)",
        "en-GB" to "English (United Kingdom)",
        "en-AU" to "English (Australia)",
        "en-IN" to "English (India)",
        "es-ES" to "Spanish (Spain)",
        "de-DE" to "German (Germany)",
        "fr-FR" to "French (France)",
        "it-IT" to "Italian (Italy)",
        "pt-BR" to "Portuguese (Brazil)"
    )

    fun openVoiceSettings() {
        try {
            val googleIntent = Intent(Intent.ACTION_MAIN).apply {
                component = ComponentName(
                    "com.google.android.googlequicksearchbox",
                    "com.google.android.apps.gsa.settingsui.LanguageSettingsActivity"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(googleIntent)
        } catch (e: Exception) {
            try {
                val voiceIntent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(voiceIntent)
            } catch (e2: Exception) {
                val generalIntent = Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(generalIntent)
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Offline Engine Mode Selection
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Offline Voice Engine Architecture",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Choose how audio and speech commands are captured on your device without internet access:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OfflineEngineMode.values().forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (settings.engineMode == mode)
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    else
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                    RoundedCornerShape(8.dp)
                                )
                                .selectable(
                                    selected = settings.engineMode == mode,
                                    onClick = { onUpdateSettings(settings.copy(engineMode = mode)) }
                                )
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.engineMode == mode,
                                onClick = { onUpdateSettings(settings.copy(engineMode = mode)) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = mode.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = mode.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Direct Audio Engine (Dicio-style) Configuration
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Hearing,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Direct Audio Engine & VAD Tuning",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Fine-tune Voice Activity Detection (VAD) for your room/office ambient noise level:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // VAD Sensitivity Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Mic VAD Sensitivity", style = MaterialTheme.typography.bodySmall)
                            Text("${(settings.vadSensitivity * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = settings.vadSensitivity,
                            onValueChange = { onUpdateSettings(settings.copy(vadSensitivity = it)) },
                            valueRange = 0.1f..1.0f
                        )
                    }

                    // Silence Timeout Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Silence Pause Before Command Trigger", style = MaterialTheme.typography.bodySmall)
                            Text("${settings.silenceTimeoutMs} ms", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = settings.silenceTimeoutMs.toFloat(),
                            onValueChange = { onUpdateSettings(settings.copy(silenceTimeoutMs = it.toLong())) },
                            valueRange = 400f..2000f,
                            steps = 15
                        )
                    }
                }
            }
        }

        // Google Offline Language Model Settings
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Google Speech Recognition",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Switch(
                            checked = settings.preferOffline,
                            onCheckedChange = { onUpdateSettings(settings.copy(preferOffline = it)) },
                            modifier = Modifier.testTag("offline_speech_switch")
                        )
                    }

                    ExposedDropdownMenuBox(
                        expanded = languageExpanded,
                        onExpandedChange = { languageExpanded = !languageExpanded }
                    ) {
                        val currentLangLabel = languages.firstOrNull { it.first == settings.speechLanguage }?.second
                            ?: settings.speechLanguage

                        OutlinedTextField(
                            value = currentLangLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Speech Language") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = languageExpanded,
                            onDismissRequest = { languageExpanded = false }
                        ) {
                            languages.forEach { (code, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        languageExpanded = false
                                        onUpdateSettings(settings.copy(speechLanguage = code))
                                    }
                                )
                            }
                        }
                    }

                    FilledTonalButton(
                        onClick = { openVoiceSettings() },
                        modifier = Modifier.fillMaxWidth().testTag("open_voice_settings_button")
                    ) {
                        Icon(Icons.Default.Launch, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Manage Google Offline Languages")
                    }
                }
            }
        }

        // Offline Guide Step-by-Step Card
        item {
            OutlinedCard(
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "How Offline Recognition Works (Like Dicio)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "1. Direct Audio Engine: Uses raw AudioRecord with zero external dependencies. Voice is captured, analyzed for VAD peaks, and dispatched locally.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "2. Google Offline STT: If Google language files are downloaded (~50MB), Android transcribes continuous words with full dictionary support.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "3. Hybrid Mode: Gives you the best of both worlds — attempts Google offline STT, and automatically switches to the direct audio engine if Google STT fails.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Speech Synthesis (TTS) Settings Card
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Speech Synthesis (TTS)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Switch(
                            checked = settings.ttsFeedbackEnabled,
                            onCheckedChange = { onUpdateSettings(settings.copy(ttsFeedbackEnabled = it)) }
                        )
                    }

                    Text(
                        text = "Speaks spoken audio confirmations when commands are captured, automatically pausing the microphone during playback.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (settings.ttsFeedbackEnabled) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Voice Pitch", style = MaterialTheme.typography.bodySmall)
                                Text("${(settings.ttsPitch * 10).toInt() / 10f}x", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = settings.ttsPitch,
                                onValueChange = { onUpdateSettings(settings.copy(ttsPitch = it)) },
                                valueRange = 0.5f..2.0f
                            )
                        }

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Speech Rate (Speed)", style = MaterialTheme.typography.bodySmall)
                                Text("${(settings.ttsSpeechRate * 10).toInt() / 10f}x", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = settings.ttsSpeechRate,
                                onValueChange = { onUpdateSettings(settings.copy(ttsSpeechRate = it)) },
                                valueRange = 0.5f..2.0f
                            )
                        }

                        OutlinedTextField(
                            value = settings.ttsTemplate,
                            onValueChange = { onUpdateSettings(settings.copy(ttsTemplate = it)) },
                            label = { Text("Spoken Feedback Template") },
                            placeholder = { Text("Command received: %s") },
                            supportingText = { Text("'%s' is replaced with recognized text") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Button(
                            onClick = {
                                val sample = String.format(settings.ttsTemplate, "lights turned on")
                                onTestTts(sample)
                            },
                            modifier = Modifier.fillMaxWidth().testTag("test_voice_feedback_button")
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Test Voice Synthesis")
                        }
                    }
                }
            }
        }

        // Start on Boot
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PowerSettingsNew,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Start Service On Device Boot",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Automatically start background microphone capture when phone finishes booting.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Switch(
                        checked = settings.startOnBoot,
                        onCheckedChange = { onUpdateSettings(settings.copy(startOnBoot = it)) }
                    )
                }
            }
        }
    }
}
