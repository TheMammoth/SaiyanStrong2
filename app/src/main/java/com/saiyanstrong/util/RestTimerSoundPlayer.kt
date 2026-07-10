package com.saiyanstrong.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Fully synthesized rest-timer sound cues — no bundled audio assets. Both play on
 * STREAM_MUSIC so a silenced/media-muted phone stays silent, matching a workout app's
 * expected behavior (never forces sound through like an alarm would).
 */
@Singleton
class RestTimerSoundPlayer @Inject constructor() {

    fun playTick(enabled: Boolean) {
        if (!enabled) return
        runCatching {
            ToneGenerator(AudioManager.STREAM_MUSIC, TICK_VOLUME).apply {
                startTone(ToneGenerator.TONE_PROP_BEEP2, TICK_DURATION_MS)
            }
        }
    }

    fun playGong(enabled: Boolean) {
        if (!enabled) return
        runCatching {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(gongBuffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(gongBuffer, 0, gongBuffer.size)
            track.setNotificationMarkerPosition(gongBuffer.size)
            track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack) = t.release()
                override fun onPeriodicNotification(t: AudioTrack) = Unit
            })
            track.play()
        }
    }

    private companion object {
        const val TICK_VOLUME = 90
        const val TICK_DURATION_MS = 120
        const val SAMPLE_RATE = 44_100
        const val GONG_DURATION_MS = 900

        // Two detuned low sine partials under an exponential-decay envelope — a low
        // "thud" gong character generated procedurally, computed once and reused.
        val gongBuffer: ShortArray by lazy {
            val sampleCount = SAMPLE_RATE * GONG_DURATION_MS / 1000
            ShortArray(sampleCount) { i ->
                val t = i.toDouble() / SAMPLE_RATE
                val envelope = exp(-t * 4.5)
                val wave = sin(2 * PI * 90.0 * t) * 0.6 + sin(2 * PI * 181.0 * t) * 0.4
                (wave * envelope * Short.MAX_VALUE)
                    .toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
            }
        }
    }
}
