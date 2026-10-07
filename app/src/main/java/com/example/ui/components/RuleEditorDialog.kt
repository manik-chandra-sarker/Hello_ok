package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.db.VoiceRuleEntity
import com.example.data.model.MatchMode
import com.example.data.model.TargetApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditorDialog(
    initialRule: VoiceRuleEntity? = null,
    onDismiss: () -> Unit,
    onSave: (VoiceRuleEntity) -> Unit
) {
    var name by remember { mutableStateOf(initialRule?.name ?: "") }
    var triggerPhrase by remember { mutableStateOf(initialRule?.triggerPhrase ?: "") }
    var matchMode by remember { mutableStateOf(initialRule?.matchMode ?: MatchMode.CONTAINS) }
    var targetApp by remember { mutableStateOf(initialRule?.targetApp ?: TargetApp.TASKER) }
    var customAction by remember { mutableStateOf(initialRule?.customAction ?: "") }
    var customExtraKey by remember { mutableStateOf(initialRule?.customExtraKey ?: "") }
    var customPayload by remember { mutableStateOf(initialRule?.customPayload ?: "") }
    var customTtsFeedback by remember { mutableStateOf(initialRule?.customTtsFeedback ?: "") }

    var matchModeExpanded by remember { mutableStateOf(false) }
    var targetAppExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialRule == null) "Add Voice Command Rule" else "Edit Voice Rule")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Rule Name") },
                    placeholder = { Text("e.g. Flashlight Toggle") },
                    modifier = Modifier.fillMaxWidth().testTag("rule_name_input"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = triggerPhrase,
                    onValueChange = { triggerPhrase = it },
                    label = { Text("Trigger Spoken Phrase") },
                    placeholder = { Text("e.g. flashlight") },
                    modifier = Modifier.fillMaxWidth().testTag("trigger_phrase_input"),
                    singleLine = true
                )

                // Match Mode Dropdown
                ExposedDropdownMenuBox(
                    expanded = matchModeExpanded,
                    onExpandedChange = { matchModeExpanded = !matchModeExpanded }
                ) {
                    OutlinedTextField(
                        value = matchMode.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Match Mode") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = matchModeExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = matchModeExpanded,
                        onDismissRequest = { matchModeExpanded = false }
                    ) {
                        MatchMode.values().forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.label) },
                                onClick = {
                                    matchMode = mode
                                    matchModeExpanded = false
                                }
                            )
                        }
                    }
                }

                // Target App Dropdown
                ExposedDropdownMenuBox(
                    expanded = targetAppExpanded,
                    onExpandedChange = { targetAppExpanded = !targetAppExpanded }
                ) {
                    OutlinedTextField(
                        value = targetApp.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Target App") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = targetAppExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = targetAppExpanded,
                        onDismissRequest = { targetAppExpanded = false }
                    ) {
                        TargetApp.values().forEach { target ->
                            DropdownMenuItem(
                                text = { Text(target.displayName) },
                                onClick = {
                                    targetApp = target
                                    if (customAction.isBlank()) customAction = target.defaultAction
                                    if (customExtraKey.isBlank()) customExtraKey = target.defaultExtra
                                    targetAppExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = customAction,
                    onValueChange = { customAction = it },
                    label = { Text("Broadcast Action") },
                    placeholder = { Text(targetApp.defaultAction) },
                    modifier = Modifier.fillMaxWidth().testTag("custom_action_input"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = customExtraKey,
                    onValueChange = { customExtraKey = it },
                    label = { Text("Extra Key Name") },
                    placeholder = { Text(targetApp.defaultExtra) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = customPayload,
                    onValueChange = { customPayload = it },
                    label = { Text("Extra Value / Payload (Optional)") },
                    placeholder = { Text("Leave blank to send transcribed voice text") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = customTtsFeedback,
                    onValueChange = { customTtsFeedback = it },
                    label = { Text("Spoken Voice Feedback (TTS)") },
                    placeholder = { Text("e.g. Turning on flashlight") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (triggerPhrase.isNotBlank()) {
                        val rule = VoiceRuleEntity(
                            id = initialRule?.id ?: 0L,
                            name = name.ifBlank { triggerPhrase },
                            triggerPhrase = triggerPhrase.trim(),
                            matchMode = matchMode,
                            targetApp = targetApp,
                            customAction = customAction.trim(),
                            customExtraKey = customExtraKey.trim(),
                            customPayload = customPayload.trim(),
                            customTtsFeedback = customTtsFeedback.trim(),
                            isEnabled = initialRule?.isEnabled ?: true
                        )
                        onSave(rule)
                    }
                },
                modifier = Modifier.testTag("save_rule_button")
            ) {
                Text("Save Rule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
