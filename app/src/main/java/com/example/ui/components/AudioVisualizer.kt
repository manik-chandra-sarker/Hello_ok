package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.model.ListeningStatus
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

@Composable
fun AudioVisualizer(
    status: ListeningStatus,
    rmsDb: Float,
    modifier: Modifier = Modifier
) {
    val isListening = status == ListeningStatus.LISTENING || status == ListeningStatus.SPEECH_DETECTED
    val isSpeaking = status == ListeningStatus.SPEAKING

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant

    // Normalize RMS decibels (usually -2 to 10 dB in Android SpeechRecognizer)
    val normalizedLevel = remember(rmsDb) {
        if (!isListening) 0.05f
        else ((rmsDb + 2f) / 12f).coerceIn(0.1f, 1f)
    }

    val animatedLevel = remember { Animatable(0.1f) }
    LaunchedEffect(normalizedLevel) {
        animatedLevel.animateTo(
            targetValue = normalizedLevel,
            animationSpec = tween(durationMillis = 90, easing = FastOutSlowInEasing)
        )
    }

    // Infinite pulsing for the microphone halo
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isListening) 1.25f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(110.dp)
        ) {
            // Outer glowing ring
            if (isListening || isSpeaking) {
                Surface(
                    shape = CircleShape,
                    color = primaryColor.copy(alpha = 0.20f),
                    modifier = Modifier
                        .size(100.dp)
                        .scale(pulseScale)
                ) {}
            }

            // Inner microphone circle
            Surface(
                shape = CircleShape,
                color = when {
                    status == ListeningStatus.SPEECH_DETECTED -> tertiaryColor
                    status == ListeningStatus.SPEAKING -> secondaryColor
                    isListening -> primaryColor
                    else -> surfaceColor
                },
                shadowElevation = 6.dp,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isListening || isSpeaking) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = "Microphone State",
                        tint = if (isListening || isSpeaking) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }

        // Live Audio Frequency / RMS Bars
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            val barCount = 25
            val totalWidth = size.width
            val barSpacing = totalWidth / barCount
            val barWidth = barSpacing * 0.55f

            val brush = Brush.verticalGradient(
                colors = listOf(primaryColor, secondaryColor)
            )

            for (i in 0 until barCount) {
                // Generate a waveform curve that peaks in center
                val distFromCenter = abs(i - (barCount / 2f)) / (barCount / 2f)
                val curveFactor = max(0.2f, 1f - distFromCenter * 0.7f)
                val pseudoRandom = (1f + kotlin.math.sin(i * 1.5 + System.currentTimeMillis() / 200.0).toFloat() * 0.3f)

                val barHeight = if (isListening) {
                    (size.height * animatedLevel.value * curveFactor * pseudoRandom).coerceIn(4f, size.height)
                } else if (isSpeaking) {
                    (size.height * 0.4f * curveFactor * pseudoRandom).coerceIn(4f, size.height)
                } else {
                    4f
                }

                val x = i * barSpacing + (barSpacing - barWidth) / 2f
                val y = (size.height - barHeight) / 2f

                drawRoundRect(
                    brush = brush,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
                )
            }
        }

        Text(
            text = when (status) {
                ListeningStatus.LISTENING -> "Live Mic Active • RMS: ${(rmsDb * 10).toInt() / 10f} dB"
                ListeningStatus.SPEECH_DETECTED -> "Speech Detected • Capturing voice..."
                ListeningStatus.PROCESSING -> "Offline Engine Transcribing..."
                ListeningStatus.DISPATCHING -> "Dispatching Automation Intent..."
                ListeningStatus.SPEAKING -> "Speaking Voice Feedback..."
                ListeningStatus.STOPPED -> "Microphone Offline • Tap Start"
                ListeningStatus.ERROR -> "Reconnecting Voice Engine..."
                ListeningStatus.INITIALIZING -> "Initializing Audio Subsystem..."
            },
            style = MaterialTheme.typography.labelMedium,
            color = if (isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
