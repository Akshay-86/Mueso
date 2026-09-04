package com.akshay.musicplayer.data.remote

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Native, lightweight MP4/M4A metadata and cover art injector.
 * Directly updates or injects the 'moov/udta/meta/ilst' box containing standard
 * iTunes metadata (Title, Artist, Album Artist, Album, Cover Art) without
 * relying on third-party library codec parsers which fail on DASH fragmented audio.
 */
object Mp4MetadataEditor {

    private const val TAG = "MUESO_MP4_EDITOR"

    fun embedMetadata(
        file: File,
        title: String,
        artist: String,
        album: String,
        albumArtist: String,
        lyrics: String? = null,
        jpegBytes: ByteArray? = null
    ): Boolean {
        if (!file.exists() || file.length() == 0L) return false

        val tempTaggedFile = File(file.parentFile, "${file.name}.tag_tmp")
        try {
            RandomAccessFile(file, "r").use { raf ->
                val fileLength = raf.length()
                var moovOffset = -1L
                var moovSize = -1L

                // Scan top-level boxes for 'moov'
                var offset = 0L
                while (offset < fileLength - 8) {
                    raf.seek(offset)
                    val boxSize = raf.readInt().toLong() and 0xFFFFFFFFL
                    val boxTypeBytes = ByteArray(4)
                    raf.readFully(boxTypeBytes)
                    val boxType = String(boxTypeBytes, Charsets.US_ASCII)

                    val actualSize = when {
                        boxSize == 1L -> {
                            // 64-bit extended size
                            raf.readLong()
                        }
                        boxSize == 0L -> {
                            // Extends to EOF
                            fileLength - offset
                        }
                        else -> boxSize
                    }

                    if (actualSize < 8L || offset + actualSize > fileLength + 16L) {
                        break
                    }

                    if (boxType == "moov") {
                        moovOffset = offset
                        moovSize = actualSize
                        break
                    }

                    offset += actualSize
                }

                if (moovOffset < 0 || moovSize <= 8) {
                    Log.w(TAG, "No valid 'moov' box found in ${file.name}")
                    return false
                }

                // Read entire moov box payload (excluding 8-byte header)
                raf.seek(moovOffset + 8)
                val moovPayloadSize = (moovSize - 8).toInt()
                val moovPayload = ByteArray(moovPayloadSize)
                raf.readFully(moovPayload)

                // Build new 'udta' box with metadata
                val newUdtaBox = buildUdtaBox(title, artist, album, albumArtist, lyrics, jpegBytes)

                // Filter out any existing 'udta' from existing moov sub-boxes
                val filteredMoovChildren = ByteArrayOutputStream()
                var oldUdtaSize = 0
                var p = 0
                while (p < moovPayload.size - 8) {
                    val subSize = ByteBuffer.wrap(moovPayload, p, 4).order(ByteOrder.BIG_ENDIAN).int
                    if (subSize < 8 || p + subSize > moovPayload.size) {
                        // Copy remaining
                        filteredMoovChildren.write(moovPayload, p, moovPayload.size - p)
                        break
                    }
                    val subType = String(moovPayload, p + 4, 4, Charsets.US_ASCII)
                    if (subType == "udta") {
                        oldUdtaSize = subSize
                    } else {
                        filteredMoovChildren.write(moovPayload, p, subSize)
                    }
                    p += subSize
                }

                val deltaSize = newUdtaBox.size - oldUdtaSize

                // If monolithic MP4 contains stco/co64 chunk tables, adjust them for the shift
                var cleanChildrenBytes = filteredMoovChildren.toByteArray()
                if (deltaSize != 0) {
                    adjustChunkOffsets(cleanChildrenBytes, deltaSize)
                }

                // Build new moov box
                val newMoovPayloadSize = cleanChildrenBytes.size + newUdtaBox.size
                val newMoovTotalSize = 8 + newMoovPayloadSize
                val newMoovHeader = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).apply {
                    putInt(newMoovTotalSize)
                    put("moov".toByteArray(Charsets.US_ASCII))
                }.array()

                // Write new file
                RandomAccessFile(tempTaggedFile, "rw").use { outRaf ->
                    outRaf.setLength(0)

                    // 1. Bytes before moov (usually ftyp)
                    if (moovOffset > 0) {
                        raf.seek(0)
                        val beforeMoov = ByteArray(moovOffset.toInt())
                        raf.readFully(beforeMoov)
                        outRaf.write(beforeMoov)
                    }

                    // 2. New moov box
                    outRaf.write(newMoovHeader)
                    outRaf.write(cleanChildrenBytes)
                    outRaf.write(newUdtaBox)

                    // 3. Bytes after original moov (sidx, moof, mdat, etc.)
                    val afterMoovOffset = moovOffset + moovSize
                    val remainingBytes = fileLength - afterMoovOffset
                    if (remainingBytes > 0) {
                        raf.seek(afterMoovOffset)
                        val buf = ByteArray(64 * 1024)
                        var totalRead = 0L
                        while (totalRead < remainingBytes) {
                            val toRead = minOf(buf.size.toLong(), remainingBytes - totalRead).toInt()
                            val r = raf.read(buf, 0, toRead)
                            if (r <= 0) break
                            outRaf.write(buf, 0, r)
                            totalRead += r
                        }
                    }
                }
            }

            // Replace original file with tagged file
            if (tempTaggedFile.exists() && tempTaggedFile.length() > 0) {
                tempTaggedFile.copyTo(file, overwrite = true)
                tempTaggedFile.delete()
                Log.d(TAG, "Successfully injected MP4 metadata and cover art into ${file.name}")
                return true
            }
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to embed MP4 metadata: ${e.message}", e)
            try { tempTaggedFile.delete() } catch (_: Exception) {}
            return false
        }
    }

    private fun adjustChunkOffsets(bytes: ByteArray, delta: Int) {
        // Recursively or iteratively scan for 'stco' and 'co64' boxes inside moov children
        var i = 0
        while (i < bytes.size - 8) {
            val size = ByteBuffer.wrap(bytes, i, 4).order(ByteOrder.BIG_ENDIAN).int
            if (size < 8 || i + size > bytes.size) break
            val type = String(bytes, i + 4, 4, Charsets.US_ASCII)

            if (type == "trak" || type == "mdia" || type == "minf" || type == "stbl") {
                // Dive inside container
                val innerBytes = bytes.sliceArray(i + 8 until i + size)
                adjustChunkOffsets(innerBytes, delta)
                System.arraycopy(innerBytes, 0, bytes, i + 8, innerBytes.size)
            } else if (type == "stco" && size >= 16) {
                // stco: 4 size, 4 type, 4 ver/flags, 4 entryCount, then entryCount * 4
                val count = ByteBuffer.wrap(bytes, i + 12, 4).order(ByteOrder.BIG_ENDIAN).int
                for (c in 0 until count) {
                    val pos = i + 16 + c * 4
                    if (pos + 4 <= i + size) {
                        val oldVal = ByteBuffer.wrap(bytes, pos, 4).order(ByteOrder.BIG_ENDIAN).int
                        val newVal = oldVal + delta
                        val newBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(newVal).array()
                        System.arraycopy(newBytes, 0, bytes, pos, 4)
                    }
                }
            } else if (type == "co64" && size >= 16) {
                // co64: 4 size, 4 type, 4 ver/flags, 4 entryCount, then entryCount * 8
                val count = ByteBuffer.wrap(bytes, i + 12, 4).order(ByteOrder.BIG_ENDIAN).int
                for (c in 0 until count) {
                    val pos = i + 16 + c * 8
                    if (pos + 8 <= i + size) {
                        val oldVal = ByteBuffer.wrap(bytes, pos, 8).order(ByteOrder.BIG_ENDIAN).long
                        val newVal = oldVal + delta
                        val newBytes = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(newVal).array()
                        System.arraycopy(newBytes, 0, bytes, pos, 8)
                    }
                }
            }
            i += size
        }
    }

    private fun buildUdtaBox(
        title: String,
        artist: String,
        album: String,
        albumArtist: String,
        lyrics: String?,
        jpegBytes: ByteArray?
    ): ByteArray {
        val ilstChildren = ByteArrayOutputStream()

        if (title.isNotBlank()) {
            ilstChildren.write(buildTextItem(byteArrayOf(0xA9.toByte(), 'n'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte()), title))
        }
        if (artist.isNotBlank()) {
            ilstChildren.write(buildTextItem(byteArrayOf(0xA9.toByte(), 'A'.code.toByte(), 'R'.code.toByte(), 'T'.code.toByte()), artist))
        }
        if (albumArtist.isNotBlank()) {
            ilstChildren.write(buildTextItem("aART".toByteArray(Charsets.US_ASCII), albumArtist))
        }
        if (album.isNotBlank()) {
            ilstChildren.write(buildTextItem(byteArrayOf(0xA9.toByte(), 'a'.code.toByte(), 'l'.code.toByte(), 'b'.code.toByte()), album))
        }
        if (!lyrics.isNullOrBlank()) {
            ilstChildren.write(buildTextItem(byteArrayOf(0xA9.toByte(), 'l'.code.toByte(), 'y'.code.toByte(), 'r'.code.toByte()), lyrics))
        }
        if (jpegBytes != null && jpegBytes.isNotEmpty()) {
            ilstChildren.write(buildImageItem(jpegBytes))
        }

        val ilstData = ilstChildren.toByteArray()
        val ilstTotalSize = 8 + ilstData.size
        val ilstBox = ByteBuffer.allocate(ilstTotalSize).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(ilstTotalSize)
            put("ilst".toByteArray(Charsets.US_ASCII))
            put(ilstData)
        }.array()

        val hdlrBox = buildHdlrBox()

        // meta box (FullBox: size(4), 'meta'(4), ver/flags(4), children...)
        val metaChildrenSize = hdlrBox.size + ilstBox.size
        val metaTotalSize = 12 + metaChildrenSize
        val metaBox = ByteBuffer.allocate(metaTotalSize).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(metaTotalSize)
            put("meta".toByteArray(Charsets.US_ASCII))
            putInt(0) // Version 0, Flags 0
            put(hdlrBox)
            put(ilstBox)
        }.array()

        // udta box
        val udtaTotalSize = 8 + metaBox.size
        return ByteBuffer.allocate(udtaTotalSize).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(udtaTotalSize)
            put("udta".toByteArray(Charsets.US_ASCII))
            put(metaBox)
        }.array()
    }

    private fun buildTextItem(itemTypeBytes: ByteArray, text: String): ByteArray {
        val payload = text.toByteArray(Charsets.UTF_8)
        val dataBox = buildDataBox(1, payload) // 1 = UTF-8 text
        val totalSize = 8 + dataBox.size
        return ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(totalSize)
            put(itemTypeBytes)
            put(dataBox)
        }.array()
    }

    private fun buildImageItem(jpegBytes: ByteArray): ByteArray {
        val dataBox = buildDataBox(13, jpegBytes) // 13 = JPEG image
        val totalSize = 8 + dataBox.size
        return ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(totalSize)
            put("covr".toByteArray(Charsets.US_ASCII))
            put(dataBox)
        }.array()
    }

    private fun buildDataBox(dataType: Int, payload: ByteArray): ByteArray {
        // data box: size(4), 'data'(4), type(4), locale(4=0), payload
        val totalSize = 16 + payload.size
        return ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(totalSize)
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataType)
            putInt(0) // Locale 0
            put(payload)
        }.array()
    }

    private fun buildHdlrBox(): ByteArray {
        // hdlr: size(4), 'hdlr'(4), ver/flags(4), pre_defined(4), handler(4), reserved(12), name(1=0)
        val totalSize = 33
        return ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(totalSize)
            put("hdlr".toByteArray(Charsets.US_ASCII))
            putInt(0) // Version 0, Flags 0
            putInt(0) // Pre-defined 0
            put("mdir".toByteArray(Charsets.US_ASCII))
            put("appl".toByteArray(Charsets.US_ASCII))
            put(ByteArray(8)) // 8 zero bytes
            put(0.toByte()) // null terminated name string
        }.array()
    }
}
