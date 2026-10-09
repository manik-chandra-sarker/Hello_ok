package com.example.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.example.data.model.AppSettings
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Direct On-Device Offline Audio Engine (Dicio-style).
 * Uses raw AudioRecord to continuously listen, analyze acoustic energy (RMS),
 * perform Voice Activity Detection (VAD), and capture voice utterances completely offline
 * without any Google cloud or network dependency.
 */
class OfflineAudioEngine(
    private val onStateChanged: (state: String) -> Unit,
    private val onRmsChanged: (rmsDb: Float) -> Unit,
    private val onSpeechUtteranceCaptured: (audioData: ShortArray, durationMs: Long) -> Unit,
    private val onErrorOccurred: (error: String) -> Unit
) {

    private val isRecording = AtomicBoolean(false)
    private var workerThread: Thread? = null
    private var audioRecord: AudioRecord? = null

    // Audio recording specifications (16kHz, 16-bit PCM, Mono)
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private var currentSettings: AppSettings? = null

    @SuppressLint("MissingPermission")
    fun start(settings: AppSettings) {
        if (isRecording.get()) return
        currentSettings = settings

        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = max(minBufferSize * 2, 4096)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                onErrorOccurred("Failed to initialize AudioRecord. Microphone may be busy.")
                return
            }

            audioRecord?.startRecording()
            isRecording.set(true)
            onStateChanged("LISTENING")

            workerThread = Thread({
                processAudioLoop(bufferSize)
            }, "OfflineAudioEngineThread").apply {
                priority = Thread.NORM_PRIORITY + 2
                start()
            }

            Log.d(TAG, "Offline Audio Engine started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting OfflineAudioEngine", e)
            onErrorOccurred("Microphone error: ${e.message}")
        }
    }

    fun stop() {
        if (!isRecording.getAndSet(false)) return

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audioRecord", e)
        }
        audioRecord = null

        workerThread?.interrupt()
        workerThread = null

        onStateChanged("STOPPED")
        Log.d(TAG, "Offline Audio Engine stopped")
    }

    private fun processAudioLoop(bufferSize: Int) {
        val audioBuffer = ShortArray(1024)
        val speechAccumulator = ArrayList<Short>()

        var isSpeechActive = false
        var silenceStartTime = 0L
        var ambientNoiseRms = 15.0f

        val silenceTimeout = currentSettings?.silenceTimeoutMs ?: 800L
        val sensitivity = currentSettings?.vadSensitivity ?: 0.5f

        var consecutiveErrors = 0
        while (isRecording.get()) {
            val record = audioRecord ?: break
            val readCount = record.read(audioBuffer, 0, audioBuffer.size)

            if (readCount <= 0) {
                consecutiveErrors++
                if (readCount == AudioRecord.ERROR_INVALID_OPERATION || readCount == AudioRecord.ERROR_BAD_VALUE) {
                    onErrorOccurred("Audio read error code $readCount")
                    try {
                        Thread.sleep(200L)
                    } catch (e: InterruptedException) {
                        break
                    }
                } else {
                    try {
                        Thread.sleep(30L)
                    } catch (e: InterruptedException) {
                        break
                    }
                }
                if (consecutiveErrors >= 10) {
                    Log.w(TAG, "Consecutive audio read failures in OfflineAudioEngine, pausing")
                    try {
                        Thread.sleep(300L)
                    } catch (e: InterruptedException) {
                        break
                    }
                    consecutiveErrors = 0
                }
                continue
            }
            consecutiveErrors = 0

            // Calculate RMS energy of current audio frame
            var sumSquare = 0.0
            for (i in 0 until readCount) {
                val sample = audioBuffer[i]
                sumSquare += sample * sample
            }
            val rms = sqrt(sumSquare / readCount).toFloat()
            val rmsDb = if (rms > 1f) (20 * log10(rms.toDouble())).toFloat() else 0f
            onRmsChanged(rmsDb)

            // Dynamic adaptive threshold based on noise floor & user sensitivity
            val speechThreshold = ambientNoiseRms + (12.0f * (1.1f - sensitivity))

            if (rmsDb > speechThreshold) {
                // Speech is currently happening
                if (!isSpeechActive) {
                    isSpeechActive = true
                    onStateChanged("SPEECH_DETECTED")
                }
                silenceStartTime = 0L

                // Accumulate audio samples (cap at 15 seconds to prevent unbounded memory growth)
                if (speechAccumulator.size < sampleRate * 15) {
                    for (i in 0 until readCount) {
                        speechAccumulator.add(audioBuffer[i])
                    }
                }
            } else {
                // Audio is quiet / below speech threshold
                // Smoothly update ambient noise floor
                ambientNoiseRms = ambientNoiseRms * 0.95f + minOf(rmsDb, 35f) * 0.05f

                if (isSpeechActive) {
                    // Accumulate a bit of trailing silence for natural boundary
                    for (i in 0 until readCount) {
                        if (speechAccumulator.size < sampleRate * 15) {
                            speechAccumulator.add(audioBuffer[i])
                        }
                    }

                    if (silenceStartTime == 0L) {
                        silenceStartTime = System.currentTimeMillis()
                    } else if (System.currentTimeMillis() - silenceStartTime >= silenceTimeout) {
                        // Utterance completed!
                        isSpeechActive = false
                        onStateChanged("PROCESSING")

                        val durationMs = (speechAccumulator.size * 1000L) / sampleRate
                        // Only process if utterance was at least 300ms long
                        if (durationMs >= 300L) {
                            val capturedSamples = ShortArray(speechAccumulator.size)
                            for (i in speechAccumulator.indices) {
                                capturedSamples[i] = speechAccumulator[i]
                            }
                            onSpeechUtteranceCaptured(capturedSamples, durationMs)
                        } else {
                            onStateChanged("LISTENING")
                        }

                        speechAccumulator.clear()
                        silenceStartTime = 0L
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "OfflineAudioEngine"
    }
}
