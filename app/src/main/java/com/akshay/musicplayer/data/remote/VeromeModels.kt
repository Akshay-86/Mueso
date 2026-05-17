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
