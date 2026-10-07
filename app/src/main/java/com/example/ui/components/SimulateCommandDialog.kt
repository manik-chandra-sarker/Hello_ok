package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedSuggestionChip
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SimulateCommandDialog(
    onDismiss: () -> Unit,
    onSimulate: (String) -> Unit
) {
    var text by remember { mutableStateOf("flashlight") }

    val presets = listOf(
        "flashlight",
        "silent mode",
        "good morning",
        "open spotify",
        "set volume 100",
        "take note buy milk"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Simulate Voice Command") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Test how the app captures voice, matches rules, sends the broadcast intent to Tasker/MacroDroid, and speaks back audio feedback.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Spoken Voice Text") },
                    placeholder = { Text("e.g. flashlight") },
                    modifier = Modifier.fillMaxWidth().testTag("simulate_input"),
                    singleLine = true
                )

                Text(
                    text = "Quick Presets:",
                    style = MaterialTheme.typography.labelSmall
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    presets.forEach { preset ->
                        ElevatedSuggestionChip(
                            onClick = { text = preset },
                            label = { Text(preset) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (text.isNotBlank()) {
                        onSimulate(text.trim())
                        onDismiss()
                    }
                },
                modifier = Modifier.testTag("simulate_dispatch_button")
            ) {
                Text("Send & Simulate")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
