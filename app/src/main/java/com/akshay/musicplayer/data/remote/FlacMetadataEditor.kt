package com.akshay.musicplayer.data.remote

import android.graphics.BitmapFactory
import android.util.Log
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets

/**
 * Native, lightweight FLAC metadata and cover art injector.
 * Directly writes Vorbis Comments (Title, Artist, Album, AlbumArtist, Lyrics) and
 * Picture Block (type 3 - Front Cover) according to the official FLAC / Xiph specification.
 *
 * Avoids third-party library limitations on Android (such as Jaudiotagger's AndroidArtwork
 * throwing UnsupportedOperationException during FLAC tagging).
 */
object FlacMetadataEditor {

    private const val TAG = "MUESO_FLAC_EDITOR"

    private const val BLOCK_TYPE_STREAMINFO = 0
    private const val BLOCK_TYPE_PADDING = 1
    private const val BLOCK_TYPE_APPLICATION = 2
    private const val BLOCK_TYPE_SEEKTABLE = 3
    private const val BLOCK_TYPE_VORBIS_COMMENT = 4
    private const val BLOCK_TYPE_CUESHEET = 5
    private const val BLOCK_TYPE_PICTURE = 6

    private data class FlacBlock(
        val type: Int,
        val data: ByteArray
    )

    fun embedMetadata(
        file: File,
        title: String,
        artist: String,
        album: String,
        albumArtist: String,
        lyrics: String? = null,
        jpegBytes: ByteArray? = null
    ): Boolean {
        if (!file.exists() || file.length() < 42) {
            Log.e(TAG, "File does not exist or is too short: ${file.name}")
            return false
        }

        val tempTaggedFile = File(file.parentFile, "${file.name}.flac_tag_tmp")
        try {
            var audioDataStartOffset = 0L
            val preservedBlocks = mutableListOf<FlacBlock>()

            RandomAccessFile(file, "r").use { raf ->
                // Check magic "fLaC"
                val magic = ByteArray(4)
                raf.readFully(magic)
                if (magic[0] != 0x66.toByte() || magic[1] != 0x4C.toByte() ||
                    magic[2] != 0x61.toByte() || magic[3] != 0x43.toByte()
                ) {
                    Log.e(TAG, "Not a valid FLAC file (invalid magic): ${file.name}")
                    return false
                }

                var isLast = false
                while (!isLast) {
                    val header = ByteArray(4)
                    raf.readFully(header)
                    val firstByte = header[0].toInt() and 0xFF
                    isLast = (firstByte and 0x80) != 0
                    val blockType = firstByte and 0x7F
                    val blockLength = ((header[1].toInt() and 0xFF) shl 16) or
                            ((header[2].toInt() and 0xFF) shl 8) or
                            (header[3].toInt() and 0xFF)

                    val blockData = ByteArray(blockLength)
                    raf.readFully(blockData)

                    // Retain STREAMINFO (type 0) and non-comment/non-picture/non-padding blocks
                    if (blockType != BLOCK_TYPE_VORBIS_COMMENT &&
                        blockType != BLOCK_TYPE_PICTURE &&
                        blockType != BLOCK_TYPE_PADDING
                    ) {
                        preservedBlocks.add(FlacBlock(blockType, blockData))
                    }
                }
                audioDataStartOffset = raf.filePointer
            }

            // Ensure STREAMINFO is the first block
            if (preservedBlocks.isEmpty() || preservedBlocks[0].type != BLOCK_TYPE_STREAMINFO) {
                Log.e(TAG, "FLAC file does not start with STREAMINFO: ${file.name}")
                return false
            }

            // 1. Build Vorbis Comment block (Block Type 4)
            val vorbisCommentBlock = buildVorbisCommentBlock(
                title = title,
                artist = artist,
                album = album,
                albumArtist = albumArtist,
                lyrics = lyrics
            )

            // 2. Build Picture block (Block Type 6) if artwork is provided
            val pictureBlock = if (jpegBytes != null && jpegBytes.isNotEmpty()) {
                buildPictureBlock(jpegBytes)
            } else null

            // Assemble new blocks list: STREAMINFO -> Preserved Blocks -> VORBIS_COMMENT -> PICTURE
            val newBlocks = mutableListOf<FlacBlock>()
            newBlocks.add(preservedBlocks[0]) // STREAMINFO
            for (i in 1 until preservedBlocks.size) {
                newBlocks.add(preservedBlocks[i])
            }
            newBlocks.add(FlacBlock(BLOCK_TYPE_VORBIS_COMMENT, vorbisCommentBlock))
            if (pictureBlock != null) {
                newBlocks.add(FlacBlock(BLOCK_TYPE_PICTURE, pictureBlock))
            }

            // Write new FLAC file to tempTaggedFile
            BufferedOutputStream(FileOutputStream(tempTaggedFile), 65536).use { out ->
                // Write magic "fLaC"
                out.write("fLaC".toByteArray(StandardCharsets.US_ASCII))

                // Write metadata blocks
                for (i in newBlocks.indices) {
                    val block = newBlocks[i]
                    val isLastBlock = (i == newBlocks.lastIndex)
                    val headerByte0 = (if (isLastBlock) 0x80 else 0x00) or (block.type and 0x7F)
                    out.write(headerByte0)
                    out.write((block.data.size ushr 16) and 0xFF)
                    out.write((block.data.size ushr 8) and 0xFF)
                    out.write(block.data.size and 0xFF)
                    out.write(block.data)
                }

                // Stream copy audio frames from original file
                FileInputStream(file).use { fis ->
                    fis.channel.position(audioDataStartOffset)
                    val buffer = ByteArray(65536)
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        out.write(buffer, 0, bytesRead)
                    }
                }
                out.flush()
            }

            // Replace original file with newly tagged file
            if (tempTaggedFile.exists() && tempTaggedFile.length() > 0) {
                if (file.delete()) {
                    if (!tempTaggedFile.renameTo(file)) {
                        tempTaggedFile.copyTo(file, overwrite = true)
                        tempTaggedFile.delete()
                    }
                } else {
                    tempTaggedFile.copyTo(file, overwrite = true)
                    tempTaggedFile.delete()
                }
                Log.d(TAG, "Successfully tagged FLAC file: ${file.name} (blocks=${newBlocks.size})")
                return true
            } else {
                Log.e(TAG, "Temp tagged file empty or missing for ${file.name}")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error embedding FLAC metadata in ${file.name}: ${e.message}", e)
            try { tempTaggedFile.delete() } catch (_: Exception) {}
            return false
        }
    }

    private fun buildVorbisCommentBlock(
        title: String,
        artist: String,
        album: String,
        albumArtist: String,
        lyrics: String?
    ): ByteArray {
        val comments = mutableListOf<String>()
        if (title.isNotBlank()) comments.add("TITLE=$title")
        if (artist.isNotBlank() && !artist.equals("Unknown Artist", ignoreCase = true)) {
            comments.add("ARTIST=$artist")
        }
        if (album.isNotBlank()) comments.add("ALBUM=$album")
        val effectiveAlbumArtist = if (albumArtist.isNotBlank() && !albumArtist.equals("Unknown Artist", ignoreCase = true)) {
            albumArtist
        } else if (artist.isNotBlank() && !artist.equals("Unknown Artist", ignoreCase = true)) {
            artist
        } else ""
        if (effectiveAlbumArtist.isNotBlank()) {
            comments.add("ALBUMARTIST=$effectiveAlbumArtist")
            comments.add("ALBUM ARTIST=$effectiveAlbumArtist")
        }
        if (!lyrics.isNullOrBlank()) {
            comments.add("LYRICS=$lyrics")
            comments.add("UNSYNCEDLYRICS=$lyrics")
        }
        comments.add("ENCODER=Mueso")

        val baos = ByteArrayOutputStream()
        val vendorBytes = "Mueso".toByteArray(StandardCharsets.UTF_8)
        writeUInt32LE(baos, vendorBytes.size)
        baos.write(vendorBytes)

        writeUInt32LE(baos, comments.size)
        for (comment in comments) {
            val commentBytes = comment.toByteArray(StandardCharsets.UTF_8)
            writeUInt32LE(baos, commentBytes.size)
            baos.write(commentBytes)
        }

        return baos.toByteArray()
    }

    private fun buildPictureBlock(jpegBytes: ByteArray): ByteArray {
        val mime = "image/jpeg"
        val mimeBytes = mime.toByteArray(StandardCharsets.US_ASCII)

        // Decode image dimensions without loading entire bitmap into memory
        var width = 0
        var height = 0
        try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, options)
            if (options.outWidth > 0 && options.outHeight > 0) {
                width = options.outWidth
                height = options.outHeight
            }
        } catch (_: Exception) {}

        val baos = ByteArrayOutputStream()
        writeUInt32BE(baos, 3) // Picture type: 3 (Cover front)
        writeUInt32BE(baos, mimeBytes.size)
        baos.write(mimeBytes)
        writeUInt32BE(baos, 0) // Description length: 0
        writeUInt32BE(baos, width)
        writeUInt32BE(baos, height)
        writeUInt32BE(baos, 24) // Color depth: 24-bit
        writeUInt32BE(baos, 0) // Number of indexed colors: 0
        writeUInt32BE(baos, jpegBytes.size)
        baos.write(jpegBytes)

        return baos.toByteArray()
    }

    private fun writeUInt32LE(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 24) and 0xFF)
    }

    private fun writeUInt32BE(out: ByteArrayOutputStream, value: Int) {
        out.write((value ushr 24) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }
}
