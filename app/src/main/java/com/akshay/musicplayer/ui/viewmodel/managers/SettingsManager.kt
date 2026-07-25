package com.akshay.musicplayer.ui.viewmodel.managers

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsManager(private val sharedPreferences: SharedPreferences) {

    private val _isDarkMode = MutableStateFlow(sharedPreferences.getBoolean("is_dark_mode", true))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _heroPlaylistId = MutableStateFlow(sharedPreferences.getString("hero_playlist_id", "curated_top_global") ?: "curated_top_global")
    val heroPlaylistId: StateFlow<String> = _heroPlaylistId.asStateFlow()

    private val _showOnLockscreen = MutableStateFlow(sharedPreferences.getBoolean("show_on_lockscreen", true))
    val showOnLockscreen: StateFlow<Boolean> = _showOnLockscreen.asStateFlow()

    private val _highRefreshRate = MutableStateFlow(sharedPreferences.getBoolean("high_refresh_rate", true))
    val highRefreshRate: StateFlow<Boolean> = _highRefreshRate.asStateFlow()

    private val _audioQuality = MutableStateFlow(sharedPreferences.getString("audio_quality", "High (320 kbps)") ?: "High (320 kbps)")
    val audioQuality: StateFlow<String> = _audioQuality.asStateFlow()

    private val _thumbnailQuality = MutableStateFlow(sharedPreferences.getString("thumbnail_quality", "1080p (Maxres)") ?: "1080p (Maxres)")
    val thumbnailQuality: StateFlow<String> = _thumbnailQuality.asStateFlow()

    private val _downloadQuality = MutableStateFlow(sharedPreferences.getString("download_quality", "Highest (320 kbps)") ?: "Highest (320 kbps)")
    val downloadQuality: StateFlow<String> = _downloadQuality.asStateFlow()

    private val _downloadFolder = MutableStateFlow(sharedPreferences.getString("download_folder", "Music/Mueso") ?: "Music/Mueso")
    val downloadFolder: StateFlow<String> = _downloadFolder.asStateFlow()

    private val _enableLyrics = MutableStateFlow(sharedPreferences.getBoolean("enable_lyrics", true))
    val enableLyrics: StateFlow<Boolean> = _enableLyrics.asStateFlow()

    // SponsorBlock Settings
    private val _enableSponsorBlock = MutableStateFlow(sharedPreferences.getBoolean("enable_sponsorblock", true))
    val enableSponsorBlock: StateFlow<Boolean> = _enableSponsorBlock.asStateFlow()
    
    private val _skipSponsor = MutableStateFlow(sharedPreferences.getBoolean("skip_sponsor", true))
    val skipSponsor: StateFlow<Boolean> = _skipSponsor.asStateFlow()

    private val _skipSelfPromo = MutableStateFlow(sharedPreferences.getBoolean("skip_self_promo", true))
    val skipSelfPromo: StateFlow<Boolean> = _skipSelfPromo.asStateFlow()

    private val _skipInteraction = MutableStateFlow(sharedPreferences.getBoolean("skip_interaction", true))
    val skipInteraction: StateFlow<Boolean> = _skipInteraction.asStateFlow()

    private val _skipIntroOutro = MutableStateFlow(sharedPreferences.getBoolean("skip_intro_outro", true))
    val skipIntroOutro: StateFlow<Boolean> = _skipIntroOutro.asStateFlow()

    private val _skipNonMusicOffTopic = MutableStateFlow(sharedPreferences.getBoolean("skip_non_music_off_topic", true))
    val skipNonMusicOffTopic: StateFlow<Boolean> = _skipNonMusicOffTopic.asStateFlow()

    fun setDarkMode(enabled: Boolean) {
        _isDarkMode.value = enabled
        sharedPreferences.edit().putBoolean("is_dark_mode", enabled).apply()
    }

    fun setHeroPlaylistId(id: String) {
        _heroPlaylistId.value = id
        sharedPreferences.edit().putString("hero_playlist_id", id).apply()
    }

    fun setShowOnLockscreen(enabled: Boolean) {
        _showOnLockscreen.value = enabled
        sharedPreferences.edit().putBoolean("show_on_lockscreen", enabled).apply()
    }

    fun setHighRefreshRate(enabled: Boolean) {
        _highRefreshRate.value = enabled
        sharedPreferences.edit().putBoolean("high_refresh_rate", enabled).apply()
    }

    fun setAudioQuality(quality: String) {
        _audioQuality.value = quality
        sharedPreferences.edit().putString("audio_quality", quality).apply()
    }

    fun setThumbnailQuality(quality: String) {
        _thumbnailQuality.value = quality
        sharedPreferences.edit().putString("thumbnail_quality", quality).apply()
    }

    fun setDownloadQuality(quality: String) {
        _downloadQuality.value = quality
        sharedPreferences.edit().putString("download_quality", quality).apply()
    }

    fun setDownloadFolder(folder: String) {
        _downloadFolder.value = folder
        sharedPreferences.edit().putString("download_folder", folder).apply()
    }

    fun setEnableLyrics(enabled: Boolean) {
        _enableLyrics.value = enabled
        sharedPreferences.edit().putBoolean("enable_lyrics", enabled).apply()
    }

    fun setEnableSponsorBlock(enabled: Boolean) {
        _enableSponsorBlock.value = enabled
        sharedPreferences.edit().putBoolean("enable_sponsorblock", enabled).apply()
    }
    
    fun setSkipSponsor(enabled: Boolean) {
        _skipSponsor.value = enabled
        sharedPreferences.edit().putBoolean("skip_sponsor", enabled).apply()
    }

    fun setSkipSelfPromo(enabled: Boolean) {
        _skipSelfPromo.value = enabled
        sharedPreferences.edit().putBoolean("skip_self_promo", enabled).apply()
    }

    fun setSkipInteraction(enabled: Boolean) {
        _skipInteraction.value = enabled
        sharedPreferences.edit().putBoolean("skip_interaction", enabled).apply()
    }

    fun setSkipIntroOutro(enabled: Boolean) {
        _skipIntroOutro.value = enabled
        sharedPreferences.edit().putBoolean("skip_intro_outro", enabled).apply()
    }

    fun setSkipNonMusicOffTopic(enabled: Boolean) {
        _skipNonMusicOffTopic.value = enabled
        sharedPreferences.edit().putBoolean("skip_non_music_off_topic", enabled).apply()
    }
}
