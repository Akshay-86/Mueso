package com.akshay.musicplayer.media.player

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

object MediaCacheManager {
    @Volatile
    private var simpleCache: SimpleCache? = null

    @Synchronized
    fun getCache(context: Context): SimpleCache {
        return simpleCache ?: run {
            val cacheDir = File(context.applicationContext.cacheDir, "exoplayer_media_cache")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val evictor = LeastRecentlyUsedCacheEvictor(150L * 1024L * 1024L) // 150 MB cache limit
            val databaseProvider = StandaloneDatabaseProvider(context.applicationContext)
            SimpleCache(cacheDir, evictor, databaseProvider).also {
                simpleCache = it
            }
        }
    }
}
