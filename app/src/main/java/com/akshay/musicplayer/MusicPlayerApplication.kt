package com.akshay.musicplayer

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy

@android.annotation.SuppressLint("StaticFieldLeak")
object AppContainer {
    private lateinit var context: Context

    fun initialize(context: Context) {
        this.context = context.applicationContext
    }

    fun getContext(): Context = context
}

class MusicPlayerApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(150L * 1024L * 1024L) // 150 MB disk cache for artwork
                    .build()
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .respectCacheHeaders(false) // Cache YouTube thumbnails on disk regardless of short/revalidate headers
            .crossfade(true)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            var cause: Throwable? = throwable
            var isForegroundException = false
            while (cause != null) {
                val name = cause.javaClass.simpleName
                if (name == "ForegroundServiceStartNotAllowedException" ||
                    (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && cause is android.app.ForegroundServiceStartNotAllowedException)) {
                    isForegroundException = true
                    break
                }
                cause = cause.cause
            }
            if (isForegroundException) {
                android.util.Log.w("MusicPlayerApp", "Caught and suppressed unhandled ForegroundServiceStartNotAllowedException to prevent crash: ${throwable.message}")
            } else {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        AppContainer.initialize(this)
        com.akshay.musicplayer.data.remote.NetworkMonitor.initialize(this)
        try {
            com.akshay.musicplayer.data.remote.stream.OnlineStreamExtractor.init(this)
        } catch (e: Exception) {
            android.util.Log.e("MusicPlayerApp", "Failed to pre-initialize OnlineStreamExtractor", e)
        }
    }
}
