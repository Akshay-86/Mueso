package com.akshay.musicplayer.data.remote.stream

import android.util.Log

data class AudioTrackFormat(
    val itag: Int,
    val bitrate: Int,      // in bps (e.g. 160000 for 160 kbps)
    val mimeType: String,  // e.g. "audio/webm; codecs=\"opus\"" or "audio/mp4"
    val url: String = ""
)

enum class NetworkTier {
    EXCELLENT, // WiFi, 5G, high-speed 4G (>= 2000 kbps)
    GOOD,      // Normal 4G, 3G (800 - 1999 kbps)
    MODERATE,  // Congested LTE, 3G (300 - 799 kbps)
    POOR       // 2G, EDGE, very low signal (< 300 kbps)
}

object AdaptiveBitrateManager {
    private const val TAG = "AdaptiveBitrate"

    private fun logD(tag: String, msg: String) {
        try {
            android.util.Log.d(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] $msg")
        }
    }

    private fun logI(tag: String, msg: String) {
        try {
            android.util.Log.i(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] INFO: $msg")
        }
    }

    private fun logW(tag: String, msg: String) {
        try {
            android.util.Log.w(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] WARN: $msg")
        }
    }

    fun determineNetworkTier(bandwidthKbps: Int): NetworkTier {
        return when {
            bandwidthKbps >= 2000 -> NetworkTier.EXCELLENT
            bandwidthKbps >= 800 -> NetworkTier.GOOD
            bandwidthKbps >= 300 -> NetworkTier.MODERATE
            else -> NetworkTier.POOR
        }
    }

    /**
     * Selects the optimal audio stream format from [availableFormats] based on:
     * - [userPreference]: e.g. "High (320 kbps)", "Medium (160 kbps)", "Low (Data Saver)", "Auto"
     * - [bandwidthKbps]: Downstream network bandwidth in kbps (e.g. from NetworkCapabilities)
     * - [bufferAheadSec]: Seconds of buffered audio ahead (if available)
     */
    fun selectOptimalAudioFormat(
        availableFormats: List<AudioTrackFormat>,
        userPreference: String? = null,
        bandwidthKbps: Int? = null,
        bufferAheadSec: Float? = null
    ): AudioTrackFormat? {
        if (availableFormats.isEmpty()) return null

        val pref = userPreference?.lowercase() ?: "high"

        // 1. User quality setting acts as an absolute MAXIMUM CEILING:
        // - "Low" -> Max ceiling is POOR (itag 139 / ~48 kbps)
        // - "Medium" -> Max ceiling is MODERATE (itag 140 / ~128 kbps)
        // - "High" / "Auto" -> Max ceiling is EXCELLENT (itag 251 / ~160 kbps)
        val userMaxCeiling = when {
            pref.contains("low") || pref.contains("saver") || pref.contains("48") || pref.contains("64") -> NetworkTier.POOR
            pref.contains("medium") || pref.contains("128") || pref.contains("160") -> NetworkTier.MODERATE
            else -> NetworkTier.EXCELLENT
        }

        // 2. Determine raw network tier based on downstream bandwidth
        val rawNetworkTier = bandwidthKbps?.let { determineNetworkTier(it) } ?: NetworkTier.EXCELLENT

        // 3. Buffer health analysis
        val isBufferCritical = bufferAheadSec != null && bufferAheadSec < 5f
        val isBufferLow = bufferAheadSec != null && bufferAheadSec < 15f

        // 4. Compute effective tier: NEVER exceed user ceiling, but drop down if network/buffer demands it
        val effectiveTier = when {
            isBufferCritical -> NetworkTier.POOR
            isBufferLow -> if (userMaxCeiling == NetworkTier.POOR) NetworkTier.POOR else NetworkTier.MODERATE
            rawNetworkTier == NetworkTier.POOR -> NetworkTier.POOR
            rawNetworkTier == NetworkTier.MODERATE -> if (userMaxCeiling == NetworkTier.POOR) NetworkTier.POOR else NetworkTier.MODERATE
            else -> userMaxCeiling // On GOOD/EXCELLENT network, cap strictly at user's ceiling!
        }

        return when (effectiveTier) {
            NetworkTier.POOR -> {
                val lowest = availableFormats.minByOrNull { it.bitrate } ?: availableFormats.first()
                logW(
                    TAG,
                    "Selected LOW quality (cap=$userMaxCeiling, net=$rawNetworkTier, buf=${bufferAheadSec}s): " +
                            "itag=${lowest.itag}, bitrate=${lowest.bitrate / 1000}kbps"
                )
                lowest
            }
            NetworkTier.MODERATE -> {
                val mediumMp4 = availableFormats.firstOrNull { it.itag == 140 }
                val selected = mediumMp4 ?: availableFormats.minByOrNull { Math.abs(it.bitrate - 128_000) } ?: availableFormats.first()
                logI(
                    TAG,
                    "Selected MEDIUM quality (cap=$userMaxCeiling, net=$rawNetworkTier, buf=${bufferAheadSec}s): " +
                            "itag=${selected.itag}, bitrate=${selected.bitrate / 1000}kbps"
                )
                selected
            }
            NetworkTier.GOOD, NetworkTier.EXCELLENT -> {
                val highest = availableFormats.maxByOrNull { it.bitrate } ?: availableFormats.first()
                logD(
                    TAG,
                    "Selected HIGH quality (cap=$userMaxCeiling, net=$rawNetworkTier, buf=${bufferAheadSec}s): " +
                            "itag=${highest.itag}, bitrate=${highest.bitrate / 1000}kbps"
                )
                highest
            }
        }
    }
}
