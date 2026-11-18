// Subsonic/OpenSubsonic backend for Sonora. GPL-3.0.
package com.adlen.sonora.audio

/**
 * Whether a stream may be handed to the phone's audio chip (offload) instead of being decoded in
 * this process.
 *
 * Offload is worth a lot: the decode happens in the hardware, the CPU stays asleep and the
 * battery lasts. The catch is that the sink then throws away the whole audio processing chain —
 * ExoPlayer builds an empty pipeline in offload mode (see `DefaultAudioSink`) — so an equalizer,
 * a gain normalization, a silence skipper or a changed speed would silently stop working.
 *
 * Pure, so the rule is tested off-device: offload runs only when nothing is processing.
 */
internal object Offload {
    const val MIN_SDK = 29

    fun wanted(
        preference: Boolean,
        sdkInt: Int,
        equalizerActive: Boolean,
        replayGain: Boolean,
        skipSilence: Boolean,
        speed: Float,
    ): Boolean = preference &&
        sdkInt >= MIN_SDK &&
        !equalizerActive &&
        !replayGain &&
        !skipSilence &&
        speed == 1f
}
