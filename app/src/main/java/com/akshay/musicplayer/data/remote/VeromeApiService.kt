package com.akshay.musicplayer.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface VeromeApiService {
    @GET("/api/trending")
    suspend fun getTrending(@Query("country") country: String = "IN"): VeromeTrendingResponse

    @GET("/api/stream")
    suspend fun getStream(@Query("id") videoId: String): VeromeStreamResponse
}

