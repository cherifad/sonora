// Subsonic/OpenSubsonic backend for Sonora. GPL-3.0.
package com.adlen.sonora.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.adlen.sonora.api.ReplayGain
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Applies the current ReplayGain value to PCM in ExoPlayer's chain, after the equalizer.
 * [gain] is set on the player's thread and read on the audio thread; 1 means "untouched",
 * and the buffer is copied as it came.
 */
@UnstableApi
class ReplayGainProcessor : BaseAudioProcessor() {
    @Volatile
    var gain: Float = 1f

    private var float = false

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val encoding = inputAudioFormat.encoding
        if ((encoding != C.ENCODING_PCM_16BIT && encoding != C.ENCODING_PCM_FLOAT) || inputAudioFormat.channelCount < 1) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        float = encoding == C.ENCODING_PCM_FLOAT
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val g = gain
        if (g == 1f) {
            val out = replaceOutputBuffer(inputBuffer.remaining())
            out.put(inputBuffer)
            out.flip()
            return
        }
        val out = replaceOutputBuffer(inputBuffer.remaining())
        inputBuffer.order(ByteOrder.nativeOrder())
        if (float) {
            while (inputBuffer.remaining() >= 4) {
                out.putFloat((inputBuffer.float * g).coerceIn(-1f, 1f))
            }
        } else {
            while (inputBuffer.remaining() >= 2) {
                val sample = inputBuffer.short * (1f / 32768f) * g
                out.putShort((sample.coerceIn(-1f, 1f) * 32768f).roundToInt().coerceIn(-32768, 32767).toShort())
            }
        }
        out.flip()
    }
}

/**
 * Linear gain for [rg]: the track or album gain in dB, capped so the declared peak cannot
 * clip. A missing gain, or none at all, leaves the audio at 1.
 */
fun replayGainLinear(rg: ReplayGain?, album: Boolean): Float {
    if (rg == null) return 1f
    val db = if (album) rg.albumGainDb else rg.trackGainDb
    val peak = if (album) rg.albumPeak else rg.trackPeak
    val linear = db?.let { 10f.pow(it / 20f) } ?: return 1f
    return if (peak != null && peak > 0f) min(linear, 1f / peak) else linear
}
