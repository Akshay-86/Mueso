package com.akshay.musicplayer.util

import android.media.MediaMetadataRetriever
import android.util.Log
import com.akshay.musicplayer.domain.models.LrcParser
import com.akshay.musicplayer.domain.models.LyricsData
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

object EmbeddedLyricsHelper {
    private const val TAG = "EmbeddedLyrics"

    /**
     * Attempts to find embedded lyrics in the audio file, or a sidecar .lrc/.txt file.
     * Returns LyricsData if found, null otherwise.
     */
    fun extractLyrics(filePath: String): LyricsData? {
        if (filePath.isBlank() || filePath.startsWith("online:") || filePath.startsWith("http://") || filePath.startsWith("https://")) {
            return null
        }

        try {
            val file = File(filePath)
            if (!file.exists() || !file.canRead()) return null

            // 1. Check for sidecar .lrc or .txt file next to the song
            val sidecarText = findSidecarLyrics(file)
            if (!sidecarText.isNullOrBlank()) {
                Log.d(TAG, "Found sidecar lyrics file for: ${file.name}")
                val parsed = LrcParser.parse(sidecarText)
                if (parsed.isNotEmpty() || sidecarText.lines().any { it.isNotBlank() }) {
                    return LyricsData(lines = parsed, rawText = sidecarText)
                }
            }

            // 2. Extract embedded tags based on file extension
            val ext = file.extension.lowercase()
            val embeddedText = when (ext) {
                "mp3" -> extractId3Lyrics(file) ?: extractViaMediaMetadataRetriever(file)
                "m4a", "mp4", "aac" -> extractMp4Lyrics(file) ?: extractViaMediaMetadataRetriever(file)
                "flac", "ogg" -> extractVorbisLyrics(file) ?: extractViaMediaMetadataRetriever(file)
                else -> extractViaMediaMetadataRetriever(file)
            }

            if (!embeddedText.isNullOrBlank()) {
                Log.d(TAG, "Successfully extracted embedded lyrics for: ${file.name}")
                val parsed = LrcParser.parse(embeddedText)
                return LyricsData(lines = parsed, rawText = embeddedText)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error extracting embedded lyrics from $filePath: ${e.message}")
        }

        return null
    }

    /**
     * Checks for <SongName>.lrc or <SongName>.txt in the same directory.
     */
    private fun findSidecarLyrics(audioFile: File): String? {
        val parent = audioFile.parentFile ?: return null
        val baseName = audioFile.nameWithoutExtension

        val lrcFile = File(parent, "$baseName.lrc")
        if (lrcFile.exists() && lrcFile.canRead()) {
            return try { lrcFile.readText(StandardCharsets.UTF_8).trim() } catch (_: Exception) { null }
        }

        val txtFile = File(parent, "$baseName.txt")
        if (txtFile.exists() && txtFile.canRead()) {
            val content = try { txtFile.readText(StandardCharsets.UTF_8).trim() } catch (_: Exception) { null }
            if (content != null && (content.contains("[0") || content.lines().size > 3)) {
                return content
            }
        }

        return null
    }

    /**
     * Attempts MediaMetadataRetriever key 1000 (often used for lyrics on Android).
     */
    private fun extractViaMediaMetadataRetriever(file: File): String? {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            // 1000 is METADATA_KEY_LYRICS on some Android OEM distributions
            val lyrics = try { retriever.extractMetadata(1000) } catch (_: Exception) { null }
            retriever.release()
            if (!lyrics.isNullOrBlank()) return lyrics.trim()
        } catch (_: Exception) {}
        return null
    }

    /**
     * Parses ID3v2 frames in an MP3 file looking for USLT (Unsynchronized lyrics) frame.
     */
    private fun extractId3Lyrics(file: File): String? {
        try {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(10)
                raf.readFully(header)
                if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) {
                    return null
                }

                val majorVersion = header[3].toInt()
                val tagSize = ((header[6].toInt() and 0x7F) shl 21) or
                        ((header[7].toInt() and 0x7F) shl 14) or
                        ((header[8].toInt() and 0x7F) shl 7) or
                        (header[9].toInt() and 0x7F)

                val tagEnd = 10L + tagSize
                while (raf.filePointer < tagEnd - 10) {
                    val frameIdBytes = ByteArray(4)
                    val read = raf.read(frameIdBytes)
                    if (read < 4) break
                    val frameId = String(frameIdBytes, StandardCharsets.ISO_8859_1)

                    if (frameId.all { it == '\u0000' } || !frameId.all { it in 'A'..'Z' || it in '0'..'9' }) {
                        break
                    }

                    val frameSize = if (majorVersion == 4) {
                        // ID3v2.4 syncsafe integer
                        val b = ByteArray(4)
                        raf.readFully(b)
                        ((b[0].toInt() and 0x7F) shl 21) or
                                ((b[1].toInt() and 0x7F) shl 14) or
                                ((b[2].toInt() and 0x7F) shl 7) or
                                (b[3].toInt() and 0x7F)
                    } else {
                        // ID3v2.3 standard 32-bit int
                        raf.readInt()
                    }

                    raf.skipBytes(2) // skip frame flags

                    if (frameSize <= 0 || frameSize > tagSize) break

                    if (frameId == "USLT" || frameId == "SYLT") {
                        val frameData = ByteArray(frameSize)
                        raf.readFully(frameData)
                        return parseUsltFrame(frameData)
                    } else {
                        raf.skipBytes(frameSize)
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun parseUsltFrame(data: ByteArray): String? {
        if (data.size < 5) return null
        val encodingByte = data[0].toInt()
        val charset: Charset = when (encodingByte) {
            1 -> StandardCharsets.UTF_16
            2 -> StandardCharsets.UTF_16BE
            3 -> StandardCharsets.UTF_8
            else -> StandardCharsets.ISO_8859_1
        }

        // data[1..3] is 3-byte language code (e.g. "eng")
        var descriptorEnd = 4
        if (encodingByte == 1 || encodingByte == 2) {
            // 2-byte null terminator
            while (descriptorEnd < data.size - 1) {
                if (data[descriptorEnd] == 0.toByte() && data[descriptorEnd + 1] == 0.toByte()) {
                    descriptorEnd += 2
                    break
                }
                descriptorEnd += 2
            }
        } else {
            // 1-byte null terminator
            while (descriptorEnd < data.size) {
                if (data[descriptorEnd] == 0.toByte()) {
                    descriptorEnd += 1
                    break
                }
                descriptorEnd += 1
            }
        }

        if (descriptorEnd >= data.size) return null
        val lyricsBytes = data.copyOfRange(descriptorEnd, data.size)
        val text = String(lyricsBytes, charset).trim()
        return text.takeIf { it.isNotBlank() }
    }

    /**
     * Extracts ©lyr atom from MP4/M4A audio containers.
     */
    private fun extractMp4Lyrics(file: File): String? {
        try {
            RandomAccessFile(file, "r").use { raf ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                val target = "\u00a9lyr".toByteArray(StandardCharsets.ISO_8859_1)

                var offset = 0L
                val maxSearch = minOf(file.length(), 2 * 1024 * 1024L) // Search first 2MB

                while (offset < maxSearch) {
                    raf.seek(offset)
                    bytesRead = raf.read(buffer)
                    if (bytesRead <= 0) break

                    for (i in 0 until bytesRead - 4) {
                        if (buffer[i] == target[0] &&
                            buffer[i + 1] == target[1] &&
                            buffer[i + 2] == target[2] &&
                            buffer[i + 3] == target[3]
                        ) {
                            // Found ©lyr atom header!
                            val atomOffset = offset + i
                            if (atomOffset >= 4) {
                                raf.seek(atomOffset - 4)
                                val atomSize = raf.readInt()
                                if (atomSize in 16..65536) {
                                    val atomData = ByteArray(atomSize)
                                    raf.seek(atomOffset - 4)
                                    raf.readFully(atomData)
                                    // ©lyr atom contains a 'data' sub-atom: [4 bytes size][4 bytes 'data'][4 bytes flags/type][4 bytes locale][text...]
                                    val dataIdx = indexOf(atomData, "data".toByteArray(StandardCharsets.US_ASCII))
                                    if (dataIdx != -1 && dataIdx + 12 < atomData.size) {
                                        val textBytes = atomData.copyOfRange(dataIdx + 12, atomData.size)
                                        val lyrics = String(textBytes, StandardCharsets.UTF_8).trim()
                                        if (lyrics.isNotBlank()) return lyrics
                                    }
                                }
                            }
                        }
                    }
                    offset += bytesRead - 4
                }
            }
        } catch (_: Exception) {}
        return null
    }

    /**
     * Extracts Vorbis comments (LYRICS= or UNSYNCEDLYRICS=) from FLAC/OGG files.
     */
    private fun extractVorbisLyrics(file: File): String? {
        try {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(4)
                raf.readFully(header)
                if (String(header, StandardCharsets.US_ASCII) != "fLaC") return null

                var isLast = false
                while (!isLast && raf.filePointer < file.length()) {
                    val blockHeader = raf.read()
                    if (blockHeader == -1) break
                    isLast = (blockHeader and 0x80) != 0
                    val blockType = blockHeader and 0x7F

                    val b1 = raf.read()
                    val b2 = raf.read()
                    val b3 = raf.read()
                    if (b1 == -1 || b2 == -1 || b3 == -1) break
                    val blockSize = (b1 shl 16) or (b2 shl 8) or b3

                    if (blockType == 4) { // VORBIS_COMMENT
                        val commentData = ByteArray(blockSize)
                        raf.readFully(commentData)
                        return parseVorbisComment(commentData)
                    } else {
                        raf.skipBytes(blockSize)
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun parseVorbisComment(data: ByteArray): String? {
        try {
            var ptr = 0
            if (data.size < 4) return null
            val vendorLen = readIntLe(data, ptr)
            ptr += 4 + vendorLen
            if (ptr + 4 > data.size) return null
            val count = readIntLe(data, ptr)
            ptr += 4

            for (i in 0 until count) {
                if (ptr + 4 > data.size) break
                val len = readIntLe(data, ptr)
                ptr += 4
                if (ptr + len > data.size) break
                val comment = String(data, ptr, len, StandardCharsets.UTF_8)
                ptr += len

                if (comment.startsWith("LYRICS=", ignoreCase = true)) {
                    return comment.substring(7).trim()
                }
                if (comment.startsWith("UNSYNCEDLYRICS=", ignoreCase = true)) {
                    return comment.substring(15).trim()
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun indexOf(array: ByteArray, target: ByteArray): Int {
        if (target.isEmpty() || array.size < target.size) return -1
        outer@ for (i in 0..array.size - target.size) {
            for (j in target.indices) {
                if (array[i + j] != target[j]) continue@outer
            }
            return i
        }
        return -1
    }

    private fun readIntLe(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8) or
                ((data[offset + 2].toInt() and 0xFF) shl 16) or
                ((data[offset + 3].toInt() and 0xFF) shl 24)
    }
}
