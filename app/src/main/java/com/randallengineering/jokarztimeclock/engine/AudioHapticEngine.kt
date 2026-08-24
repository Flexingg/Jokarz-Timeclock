package com.randallengineering.jokarztimeclock.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.sin

class AudioHapticEngine(private val context: Context) {

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private val audioScope = CoroutineScope(Dispatchers.Default)

    fun vibrate(durationMs: Long = 50L) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playClockInSound(enabled: Boolean = true) {
        if (!enabled) return
        vibrate(60)
        audioScope.launch {
            playToneSequence(listOf(Pair(440.0, 100), Pair(880.0, 180)))
        }
    }

    fun playClockOutSound(enabled: Boolean = true) {
        if (!enabled) return
        vibrate(80)
        audioScope.launch {
            playToneSequence(listOf(Pair(660.0, 120), Pair(330.0, 200)))
        }
    }

    fun playMilestoneChime(enabled: Boolean = true) {
        if (!enabled) return
        vibrate(120)
        audioScope.launch {
            playToneSequence(listOf(Pair(523.25, 80), Pair(659.25, 80), Pair(783.99, 80), Pair(1046.50, 250)))
        }
    }

    fun playClickSound(enabled: Boolean = true) {
        if (!enabled) return
        vibrate(25)
        audioScope.launch {
            playTone(800.0, 30)
        }
    }

    private fun playTone(freq: Double, durationMs: Int) {
        try {
            val sampleRate = 44100
            val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val time = i.toDouble() / sampleRate
                val envelope = 1.0 - (i.toDouble() / numSamples) // Linear fade-out
                val sample = (sin(2.0 * Math.PI * freq * time) * Short.MAX_VALUE * 0.25 * envelope).toInt()
                buffer[i] = sample.toShort()
            }

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()

            val track = AudioTrack(
                audioAttributes,
                audioFormat,
                buffer.size * 2,
                AudioTrack.MODE_STATIC,
                0
            )

            track.write(buffer, 0, buffer.size)
            track.play()
            Thread.sleep(durationMs.toLong() + 20L)
            track.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playToneSequence(tones: List<Pair<Double, Int>>) {
        tones.forEach { (freq, dur) ->
            playTone(freq, dur)
        }
    }
}
