package com.akshay.musicplayer

import android.app.Application
import android.content.Context

object AppContainer {
    private lateinit var context: Context

    fun initialize(context: Context) {
        this.context = context
    }

    fun getContext(): Context = context
}

class MusicPlayerApplication : Application() {
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
    }
}
