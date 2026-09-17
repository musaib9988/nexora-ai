package com.example.phone

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.*
import kotlin.math.sin

/**
 * Built-in Melody & Music Player for Nexora / Seeru AI.
 * Synthesizes a soothing acoustic musical melody loop so that
 * "gaana bajao" / "play song" produces audible music immediately
 * on any Android device or emulator without needing external audio files.
 */
object BuiltInMusicPlayer {
    private var isPlaying = false
    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null

    // Musical note frequencies (Hz) for a soothing melody
    private val melodyNotes = doubleArrayOf(
        261.63, // C4
        329.63, // E4
        392.00, // G4
        523.25, // C5
        440.00, // A4
        349.23, // F4
        392.00, // G4
        329.63, // E4
        261.63, // C4
        293.66, // D4
        329.63, // E4
        392.00  // G4
    )

    private const val SAMPLE_RATE = 22050
    private const val NOTE_DURATION_MS = 400

    fun isCurrentlyPlaying(): Boolean = isPlaying

    fun play(scope: CoroutineScope = CoroutineScope(Dispatchers.Default)) {
        if (isPlaying) return
        isPlaying = true

        playbackJob = scope.launch(Dispatchers.Default) {
            try {
                val minBufferSize = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBufferSize.coerceAtLeast(4096))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack = track
                track.play()

                while (isPlaying && isActive) {
                    for (freq in melodyNotes) {
                        if (!isPlaying || !isActive) break

                        val samples = generateNoteSamples(freq, NOTE_DURATION_MS)
                        track.write(samples, 0, samples.size)
                        delay(20) // Slight note gap
                    }
                    delay(400) // Brief musical pause between loops
                }

                track.stop()
                track.release()
                audioTrack = null
            } catch (e: Exception) {
                Log.e("BuiltInMusicPlayer", "Playback error: ${e.message}")
            } finally {
                isPlaying = false
            }
        }
    }

    fun stop() {
        isPlaying = false
        playbackJob?.cancel()
        playbackJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            // Ignore
        }
        audioTrack = null
    }

    private fun generateNoteSamples(frequencyHz: Double, durationMs: Int): ShortArray {
        val totalSamples = (SAMPLE_RATE * (durationMs / 1000.0)).toInt()
        val samples = ShortArray(totalSamples)

        val attackSamples = (totalSamples * 0.15).toInt().coerceAtLeast(1)
        val decaySamples = (totalSamples * 0.25).toInt().coerceAtLeast(1)
        val releaseStart = totalSamples - decaySamples

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            // Fundamental tone + warm harmonics
            val wave = 0.65 * sin(2.0 * Math.PI * frequencyHz * t) +
                    0.25 * sin(4.0 * Math.PI * frequencyHz * t) +
                    0.10 * sin(6.0 * Math.PI * frequencyHz * t)

            // Envelope to avoid pops/clicks
            val envelope = when {
                i < attackSamples -> i.toDouble() / attackSamples
                i > releaseStart -> (totalSamples - i).toDouble() / decaySamples
                else -> 1.0
            }

            val pcm = (wave * envelope * 24000).toInt().coerceIn(-32767, 32767)
            samples[i] = pcm.toShort()
        }

        return samples
    }
}
