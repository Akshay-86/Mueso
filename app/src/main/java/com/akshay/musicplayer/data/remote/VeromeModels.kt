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

