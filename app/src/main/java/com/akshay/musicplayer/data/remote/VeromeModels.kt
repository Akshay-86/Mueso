package com.akshay.musicplayer.data.remote

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class VeromeTrendingResponse(
    val success: Boolean,
    val country: String,
    val tracks: List<VeromeTrack>
)

@JsonClass(generateAdapter = true)
data class VeromeTrack(
    val videoId: String,
    val name: String,
    val artist: String,
    val thumbnail: String?
)

@JsonClass(generateAdapter = true)
data class VeromeStreamResponse(
    val success: Boolean? = null,
    val streamingUrls: List<VeromeStreamFormat>? = null,
    val formats: List<VeromeStreamFormat>? = null,
    val error: String? = null
)


@JsonClass(generateAdapter = true)
data class VeromeStreamFormat(
    val url: String? = null,
    val directUrl: String? = null,
    val type: String? = null,
    val audioQuality: String? = null,
    val bitrate: String? = null
)

@JsonClass(generateAdapter = true)
data class VeromeSearchResponse(
    val query: String? = null,
    val results: List<VeromeSearchResult>? = null
)

@JsonClass(generateAdapter = true)
data class VeromeSearchResult(
    val title: String? = null,
    val videoId: String? = null,
    val artists: List<VeromeArtist>? = null,
    val thumbnails: List<VeromeThumbnail>? = null,
    val resultType: String? = null
)

@JsonClass(generateAdapter = true)
data class VeromeArtist(
    val name: String? = null,
    val id: String? = null
)

@JsonClass(generateAdapter = true)
data class VeromeThumbnail(
    val url: String? = null,
    val width: Int? = null,
    val height: Int? = null
)


