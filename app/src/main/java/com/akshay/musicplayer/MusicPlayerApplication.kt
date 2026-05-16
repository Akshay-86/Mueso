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
        AppContainer.initialize(this)
    }
}
