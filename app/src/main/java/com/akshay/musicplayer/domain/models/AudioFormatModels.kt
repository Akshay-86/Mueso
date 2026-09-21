package com.akshay.musicplayer.domain.models

/**
 * Detailed real-time audio technical specifications for the active track.
 * Used for the Audio Quality Capsule Pill and the audiophile Signal Path dialog.
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
     * e.g., "HI-RES FLAC • 24-bit/96kHz", "FLAC • 16-bit", "OPUS • 160k"
     */
    val pillLabel: String
        get() = when {
            isHiRes -> "HI-RES FLAC • ${bitDepth}-bit/${(sampleRateHz / 1000f).let { if (it % 1.0f == 0f) "${it.toInt()}kHz" else "${it}kHz" }}"
            isLossless -> "FLAC • ${bitDepth}-bit"
            codec.equals("FLAC", ignoreCase = true) -> "FLAC • 16-bit"
            codec.equals("OPUS", ignoreCase = true) -> "OPUS • ${bitrateKbps}k"
            codec.equals("AAC", ignoreCase = true) -> "AAC • ${bitrateKbps}k"
            codec.equals("MP3", ignoreCase = true) -> "MP3 • ${bitrateKbps}k"
            else -> "$codec • ${bitrateKbps}k"
        }
}

/**
 * Result of resolving a direct lossless audio stream from ClashFLAC / Qobuz / Amazon CDN.
 */
data class LosslessStreamResult(
    val streamUrl: String,
    val codec: String = "flac",
    val bitrateKbps: Int = 1411,
    val sampleRateHz: Int = 44100,
    val bitDepth: Int = 16,
    val source: String = "Lossless FLAC",
    val durationSeconds: Int = 0,
    val matchedTitle: String = "",
    val matchedArtist: String = "",
    val album: String? = null
)
