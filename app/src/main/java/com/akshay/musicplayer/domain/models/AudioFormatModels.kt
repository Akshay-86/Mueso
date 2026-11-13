package com.akshay.musicplayer.domain.models

/**
 * Detailed real-time audio technical specifications for the active track.
 * Used for the Audio Quality Capsule Pill.
 */
data class ActiveAudioFormat(
    val codec: String = "OPUS",
    val bitDepth: Int = 16,
    val sampleRateHz: Int = 48000,
    val bitrateKbps: Int = 160,
    val isLossless: Boolean = false,
    val isHiRes: Boolean = false,
    val sourceName: String = "YouTube Music",
    val channelCount: Int = 2,
    val audioOutputDevice: String = "Phone Speaker",
    val isBitPerfect: Boolean = false
) {
    /**
     * Formats a short badge label for display inside the capsule pill.
     * e.g., "FLAC • 16-bit", "OPUS • 160k", "AAC • 256k"
     */
    val pillLabel: String
        get() = when {
            isHiRes && codec.equals("FLAC", ignoreCase = true) -> "HI-RES FLAC • ${bitDepth}-bit/${(sampleRateHz / 1000f).let { if (it % 1.0f == 0f) "${it.toInt()}kHz" else "${it}kHz" }}"
            codec.equals("FLAC", ignoreCase = true) || (isLossless && codec.isBlank()) -> "FLAC • ${bitDepth}-bit"
            codec.equals("WAV", ignoreCase = true) -> "WAV • ${bitDepth}-bit"
            isLossless -> "LOSSLESS • ${bitDepth}-bit"
            codec.equals("OPUS", ignoreCase = true) -> "OPUS • ${bitrateKbps}k"
            codec.equals("AAC", ignoreCase = true) -> "AAC • ${bitrateKbps}k"
            codec.equals("MP3", ignoreCase = true) -> "MP3 • ${bitrateKbps}k"
            else -> "$codec • ${bitrateKbps}k"
        }
}

