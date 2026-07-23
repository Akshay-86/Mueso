package com.akshay.musicplayer.domain.models

data class LyricLine(
    val timestampMs: Long,
    val text: String
)

object LrcParser {
    fun parse(lrcText: String?): List<LyricLine> {
        if (lrcText.isNullOrBlank()) return emptyList()
        val lines = mutableListOf<LyricLine>()
        val timeRegex = Regex("\\[(\\d{1,2}):(\\d{2})[.:](\\d{2,3})\\]")

        lrcText.lines().forEach { rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isNotBlank()) {
                val matches = timeRegex.findAll(trimmed).toList()
                if (matches.isNotEmpty()) {
                    val lyricText = timeRegex.replace(trimmed, "").trim()
                    if (lyricText.isNotBlank()) {
                        for (match in matches) {
                            val (minStr, secStr, msStr) = match.destructured
                            val minutes = minStr.toLongOrNull() ?: 0L
                            val seconds = secStr.toLongOrNull() ?: 0L
                            val msVal = msStr.toLongOrNull() ?: 0L
                            val ms = if (msStr.length == 2) msVal * 10 else msVal
                            val totalMs = (minutes * 60 * 1000) + (seconds * 1000) + ms
                            lines.add(LyricLine(totalMs, lyricText))
                        }
                    }
                }
            }
        }
        return lines.sortedBy { it.timestampMs }
    }
}
