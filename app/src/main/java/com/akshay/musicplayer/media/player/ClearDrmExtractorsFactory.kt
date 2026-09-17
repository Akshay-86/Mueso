package com.akshay.musicplayer.media.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput

/**
 * Transparent ExtractorsFactory wrapper that strips synthetic or inert container DRM metadata
 * (such as Amazon CloudFront clear-payload MP4 wrappers with Widevine PSSH atoms)
 * so Android's native audio codecs (c2.android.flac.decoder) can decode the clear frames without DRM rejection.
 */
@UnstableApi
class ClearDrmExtractorsFactory(
    private val delegate: ExtractorsFactory = DefaultExtractorsFactory()
) : ExtractorsFactory {

    override fun createExtractors(): Array<Extractor> {
        return delegate.createExtractors().map { ClearDrmExtractor(it) }.toTypedArray()
    }

    override fun createExtractors(uri: Uri, responseHeaders: Map<String, List<String>>): Array<Extractor> {
        return delegate.createExtractors(uri, responseHeaders).map { ClearDrmExtractor(it) }.toTypedArray()
    }
}

@UnstableApi
private class ClearDrmExtractor(private val delegate: Extractor) : Extractor {
    override fun init(output: ExtractorOutput) {
        delegate.init(ClearDrmExtractorOutput(output))
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        return delegate.read(input, seekPosition)
    }

    override fun seek(position: Long, timeUs: Long) {
        delegate.seek(position, timeUs)
    }

    override fun release() {
        delegate.release()
    }

    override fun sniff(input: ExtractorInput): Boolean {
        return delegate.sniff(input)
    }

    override fun getUnderlyingImplementation(): Extractor {
        return delegate.underlyingImplementation
    }
}

@UnstableApi
private class ClearDrmExtractorOutput(private val delegate: ExtractorOutput) : ExtractorOutput {
    override fun track(id: Int, type: Int): TrackOutput {
        return ClearDrmTrackOutput(delegate.track(id, type))
    }

    override fun endTracks() {
        delegate.endTracks()
    }

    override fun seekMap(seekMap: SeekMap) {
        delegate.seekMap(seekMap)
    }
}

@UnstableApi
private class ClearDrmTrackOutput(private val delegate: TrackOutput) : TrackOutput {
    override fun format(format: Format) {
        val cleanFormat = if (format.drmInitData != null) {
            format.buildUpon().setDrmInitData(null).build()
        } else {
            format
        }
        delegate.format(cleanFormat)
    }

    override fun sampleData(input: DataReader, length: Int, allowEndOfInput: Boolean): Int {
        return delegate.sampleData(input, length, allowEndOfInput)
    }

    override fun sampleData(data: ParsableByteArray, length: Int) {
        delegate.sampleData(data, length)
    }

    override fun sampleData(input: DataReader, length: Int, allowEndOfInput: Boolean, sampleDataPart: Int): Int {
        return delegate.sampleData(input, length, allowEndOfInput, sampleDataPart)
    }

    override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
        delegate.sampleData(data, length, sampleDataPart)
    }

    override fun sampleMetadata(
        timeUs: Long,
        flags: Int,
        size: Int,
        offset: Int,
        cryptoData: TrackOutput.CryptoData?
    ) {
        val cleanFlags = flags and C.BUFFER_FLAG_ENCRYPTED.inv()
        delegate.sampleMetadata(timeUs, cleanFlags, size, offset, null)
    }
}
