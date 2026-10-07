package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.db.VoiceRuleEntity
import com.example.data.model.AppSettings
import com.example.data.model.TargetApp
import com.example.ui.components.RuleEditorDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    settings: AppSettings,
    rules: List<VoiceRuleEntity>,
    onUpdateSettings: (AppSettings) -> Unit,
    onSaveRule: (VoiceRuleEntity) -> Unit,
    onToggleRule: (VoiceRuleEntity) -> Unit,
    onDeleteRule: (VoiceRuleEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var editingRule by remember { mutableStateOf<VoiceRuleEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    var targetDropdownExpanded by remember { mutableStateOf(false) }

    if (showAddDialog || editingRule != null) {
        RuleEditorDialog(
            initialRule = editingRule,
            onDismiss = {
                showAddDialog = false
                editingRule = null
            },
            onSave = { rule ->
                onSaveRule(rule)
                showAddDialog = false
                editingRule = null
            }
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_rule_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Voice Rule")
            }
        },
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Default Automation App Target
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
                                imageVector = Icons.Default.Send,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Default Intent Destination",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "Unmatched voice commands are automatically broadcasted to your selected automation app with full voice parameters.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        ExposedDropdownMenuBox(
                            expanded = targetDropdownExpanded,
                            onExpandedChange = { targetDropdownExpanded = !targetDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = settings.defaultTarget.displayName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Default Automation Receiver") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = targetDropdownExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = targetDropdownExpanded,
                                onDismissRequest = { targetDropdownExpanded = false }
                            ) {
                                TargetApp.values().forEach { target ->
                                    DropdownMenuItem(
                                        text = { Text(target.displayName) },
                                        onClick = {
                                            targetDropdownExpanded = false
                                            onUpdateSettings(
                                                settings.copy(
                                                    defaultTarget = target,
                                                    defaultAction = target.defaultAction,
                                                    defaultExtraKey = target.defaultExtra
                                                )
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = settings.defaultAction,
                            onValueChange = { onUpdateSettings(settings.copy(defaultAction = it)) },
                            label = { Text("Default Broadcast Action") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = settings.defaultExtraKey,
                            onValueChange = { onUpdateSettings(settings.copy(defaultExtraKey = it)) },
                            label = { Text("Default Extra Key (e.g. voice_command)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }

            // Wake Word / Keyword Filter Card
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
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Hearing,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Wake Word Filter",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Switch(
                                checked = settings.wakeWordEnabled,
                                onCheckedChange = { onUpdateSettings(settings.copy(wakeWordEnabled = it)) }
                            )
                        }

                        Text(
                            text = if (settings.wakeWordEnabled)
                                "Only trigger intents if spoken speech contains the wake word (wake word will be stripped from the sent command)."
                            else
                                "Disabled: All recognized speech will be captured and dispatched directly.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (settings.wakeWordEnabled) {
                            OutlinedTextField(
                                value = settings.wakeWord,
                                onValueChange = { onUpdateSettings(settings.copy(wakeWord = it)) },
                                label = { Text("Wake Word Phrase") },
                                placeholder = { Text("e.g. computer or jarvis") },
                                modifier = Modifier.fillMaxWidth().testTag("wake_word_input"),
                                singleLine = true
                            )
                        }
                    }
                }
            }

            // Rules Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Custom Command Rules (${rules.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (rules.isEmpty()) {
                item {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No custom rules configured yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "All voice commands will be routed using the default broadcast target above. Tap '+' to create specific phrase mappings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            items(rules, key = { it.id }) { rule ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (rule.isEnabled)
                            MaterialTheme.colorScheme.surface
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = rule.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Trigger: \"${rule.triggerPhrase}\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Switch(
                                checked = rule.isEnabled,
                                onCheckedChange = { onToggleRule(rule) }
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = rule.targetApp.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = rule.matchMode.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (rule.customAction.isNotBlank()) {
                            Text(
                                text = "Action: ${rule.customAction}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        if (rule.customTtsFeedback.isNotBlank()) {
                            Text(
                                text = "TTS Reply: \"${rule.customTtsFeedback}\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            IconButton(onClick = { editingRule = rule }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Rule", modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { onDeleteRule(rule) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete Rule",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
