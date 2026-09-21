package com.akshay.musicplayer.ui.viewmodel.managers

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsManager(private val sharedPreferences: SharedPreferences) {

    private val _isDarkMode = MutableStateFlow(sharedPreferences.getBoolean("is_dark_mode", true))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _themeMode = MutableStateFlow(sharedPreferences.getString("theme_mode", "system") ?: "system")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _usePureBlack = MutableStateFlow(sharedPreferences.getBoolean("use_pure_black", false))
    val usePureBlack: StateFlow<Boolean> = _usePureBlack.asStateFlow()

    private val _accentColorId = MutableStateFlow(sharedPreferences.getString("accent_color_id", "sunset_orange") ?: "sunset_orange")
    val accentColorId: StateFlow<String> = _accentColorId.asStateFlow()

    private val _fontScaleOption = MutableStateFlow(sharedPreferences.getString("font_scale_option", "standard") ?: "standard")
    val fontScaleOption: StateFlow<String> = _fontScaleOption.asStateFlow()

    private val _cornerRadiusOption = MutableStateFlow(sharedPreferences.getString("corner_radius_option", "rounded") ?: "rounded")
    val cornerRadiusOption: StateFlow<String> = _cornerRadiusOption.asStateFlow()

    private val _lyricsFontSizeOption = MutableStateFlow(sharedPreferences.getString("lyrics_font_size_option", "standard") ?: "standard")
    val lyricsFontSizeOption: StateFlow<String> = _lyricsFontSizeOption.asStateFlow()

    private val _heroPlaylistId = MutableStateFlow(sharedPreferences.getString("hero_playlist_id", "curated_top_global") ?: "curated_top_global")
    val heroPlaylistId: StateFlow<String> = _heroPlaylistId.asStateFlow()

    private val _showOnLockscreen = MutableStateFlow(sharedPreferences.getBoolean("show_on_lockscreen", true))
    val showOnLockscreen: StateFlow<Boolean> = _showOnLockscreen.asStateFlow()

    private val _highRefreshRate = MutableStateFlow(sharedPreferences.getBoolean("high_refresh_rate", false))
    val highRefreshRate: StateFlow<Boolean> = _highRefreshRate.asStateFlow()

    private val _audioQuality = MutableStateFlow(sharedPreferences.getString("audio_quality", "Medium (160 kbps)") ?: "Medium (160 kbps)")
    val audioQuality: StateFlow<String> = _audioQuality.asStateFlow()

    private val _thumbnailQuality = MutableStateFlow(sharedPreferences.getString("thumbnail_quality", "Medium (480p)") ?: "Medium (480p)")
    val thumbnailQuality: StateFlow<String> = _thumbnailQuality.asStateFlow()

    private val _downloadQuality = MutableStateFlow(sharedPreferences.getString("download_quality", "Lossless (FLAC)") ?: "Lossless (FLAC)")
    val downloadQuality: StateFlow<String> = _downloadQuality.asStateFlow()

    private val _downloadFolder = MutableStateFlow(sharedPreferences.getString("download_folder", "Music/Mueso") ?: "Music/Mueso")
    val downloadFolder: StateFlow<String> = _downloadFolder.asStateFlow()

    private val _enableLyrics = MutableStateFlow(sharedPreferences.getBoolean("enable_lyrics", true))
    val enableLyrics: StateFlow<Boolean> = _enableLyrics.asStateFlow()

    private val _embedLyricsInDownload = MutableStateFlow(sharedPreferences.getBoolean("embed_lyrics_in_download", true))
    val embedLyricsInDownload: StateFlow<Boolean> = _embedLyricsInDownload.asStateFlow()

    private val _preferredLanguage = MutableStateFlow(sharedPreferences.getString("preferred_language", "Telugu") ?: "Telugu")
    val preferredLanguage: StateFlow<String> = _preferredLanguage.asStateFlow()

    fun setPreferredLanguage(language: String) {
        _preferredLanguage.value = language
        sharedPreferences.edit().putString("preferred_language", language).apply()
    }

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

    // Audiophile & Lossless Settings
    private val _losslessStreamingEnabled = MutableStateFlow(sharedPreferences.getBoolean("lossless_streaming_enabled", false))
    val losslessStreamingEnabled: StateFlow<Boolean> = _losslessStreamingEnabled.asStateFlow()

    private val _losslessServerUrl = MutableStateFlow(
        sharedPreferences.getString("lossless_server_url", com.akshay.musicplayer.data.remote.lossless.LosslessMusicRepository.DEFAULT_SERVER_URL)
            ?.let { if (it.contains("clashflac.kanjijewels.com", ignoreCase = true)) com.akshay.musicplayer.data.remote.lossless.LosslessMusicRepository.DEFAULT_SERVER_URL else it }
            ?: com.akshay.musicplayer.data.remote.lossless.LosslessMusicRepository.DEFAULT_SERVER_URL
    )
    val losslessServerUrl: StateFlow<String> = _losslessServerUrl.asStateFlow()

    private val _isStudioMasterClarityEnabled = MutableStateFlow(sharedPreferences.getBoolean("studio_master_clarity", false))
    val isStudioMasterClarityEnabled: StateFlow<Boolean> = _isStudioMasterClarityEnabled.asStateFlow()

    private val _isBitPerfectEnabled = MutableStateFlow(sharedPreferences.getBoolean("bit_perfect_mode", false))
    val isBitPerfectEnabled: StateFlow<Boolean> = _isBitPerfectEnabled.asStateFlow()

    private val _crossfadeEnabled = MutableStateFlow(sharedPreferences.getBoolean("crossfade_enabled", false))
    val crossfadeEnabled: StateFlow<Boolean> = _crossfadeEnabled.asStateFlow()

    private val _crossfadeSeconds = MutableStateFlow(sharedPreferences.getInt("crossfade_seconds", 5))
    val crossfadeSeconds: StateFlow<Int> = _crossfadeSeconds.asStateFlow()

    // Layout & Theme Preferences
    private val _playerLayoutStyle = MutableStateFlow(sharedPreferences.getString("player_layout_style", "reels") ?: "reels")
    val playerLayoutStyle: StateFlow<String> = _playerLayoutStyle.asStateFlow()

    fun setDarkMode(enabled: Boolean) {
        _isDarkMode.value = enabled
        sharedPreferences.edit().putBoolean("is_dark_mode", enabled).apply()
        val mode = if (enabled) "dark" else "light"
        _themeMode.value = mode
        sharedPreferences.edit().putString("theme_mode", mode).apply()
    }

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        sharedPreferences.edit().putString("theme_mode", mode).apply()
        val isDark = mode != "light"
        _isDarkMode.value = isDark
        sharedPreferences.edit().putBoolean("is_dark_mode", isDark).apply()
    }

    fun setUsePureBlack(enabled: Boolean) {
        _usePureBlack.value = enabled
        sharedPreferences.edit().putBoolean("use_pure_black", enabled).commit()
    }

    fun setAccentColorId(id: String) {
        _accentColorId.value = id
        sharedPreferences.edit().putString("accent_color_id", id).commit()
    }

    fun setFontScaleOption(option: String) {
        _fontScaleOption.value = option
        sharedPreferences.edit().putString("font_scale_option", option).apply()
    }

    fun setCornerRadiusOption(option: String) {
        _cornerRadiusOption.value = option
        sharedPreferences.edit().putString("corner_radius_option", option).apply()
    }

    fun setLyricsFontSizeOption(option: String) {
        _lyricsFontSizeOption.value = option
        sharedPreferences.edit().putString("lyrics_font_size_option", option).apply()
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

    fun setEmbedLyricsInDownload(enabled: Boolean) {
        _embedLyricsInDownload.value = enabled
        sharedPreferences.edit().putBoolean("embed_lyrics_in_download", enabled).apply()
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

    private val _playButtonPosition = MutableStateFlow(sharedPreferences.getString("play_button_position", "Left") ?: "Left")
    val playButtonPosition: StateFlow<String> = _playButtonPosition.asStateFlow()

    fun setPlayButtonPosition(position: String) {
        _playButtonPosition.value = position
        sharedPreferences.edit().putString("play_button_position", position).apply()
    }

    fun setSkipNonMusicOffTopic(enabled: Boolean) {
        _skipNonMusicOffTopic.value = enabled
        sharedPreferences.edit().putBoolean("skip_non_music_off_topic", enabled).apply()
    }

    fun setLosslessStreamingEnabled(enabled: Boolean) {
        _losslessStreamingEnabled.value = enabled
        sharedPreferences.edit().putBoolean("lossless_streaming_enabled", enabled).apply()
    }

    fun setLosslessServerUrl(url: String) {
        _losslessServerUrl.value = url
        sharedPreferences.edit().putString("lossless_server_url", url).apply()
    }

    fun setStudioMasterClarityEnabled(enabled: Boolean) {
        _isStudioMasterClarityEnabled.value = enabled
        sharedPreferences.edit().putBoolean("studio_master_clarity", enabled).apply()
    }

    fun setBitPerfectEnabled(enabled: Boolean) {
        _isBitPerfectEnabled.value = enabled
        sharedPreferences.edit().putBoolean("bit_perfect_mode", enabled).apply()
    }

    fun setCrossfadeEnabled(enabled: Boolean) {
        _crossfadeEnabled.value = enabled
        sharedPreferences.edit().putBoolean("crossfade_enabled", enabled).apply()
    }

    fun setCrossfadeSeconds(seconds: Int) {
        _crossfadeSeconds.value = seconds
        sharedPreferences.edit().putInt("crossfade_seconds", seconds).apply()
    }

    fun setPlayerLayoutStyle(style: String) {
        _playerLayoutStyle.value = style
        sharedPreferences.edit().putString("player_layout_style", style).commit()
    }

    fun reloadFromPreferences() {
        _isDarkMode.value = sharedPreferences.getBoolean("is_dark_mode", true)
        _themeMode.value = sharedPreferences.getString("theme_mode", "system") ?: "system"
        _usePureBlack.value = sharedPreferences.getBoolean("use_pure_black", false)
        _accentColorId.value = sharedPreferences.getString("accent_color_id", "sunset_orange") ?: "sunset_orange"
        _fontScaleOption.value = sharedPreferences.getString("font_scale_option", "standard") ?: "standard"
        _cornerRadiusOption.value = sharedPreferences.getString("corner_radius_option", "rounded") ?: "rounded"
        _lyricsFontSizeOption.value = sharedPreferences.getString("lyrics_font_size_option", "standard") ?: "standard"
        _heroPlaylistId.value = sharedPreferences.getString("hero_playlist_id", "curated_top_global") ?: "curated_top_global"
        _showOnLockscreen.value = sharedPreferences.getBoolean("show_on_lockscreen", true)
        _highRefreshRate.value = sharedPreferences.getBoolean("high_refresh_rate", false)
        _audioQuality.value = sharedPreferences.getString("audio_quality", "Medium (160 kbps)") ?: "Medium (160 kbps)"
        _thumbnailQuality.value = sharedPreferences.getString("thumbnail_quality", "Medium (480p)") ?: "Medium (480p)"
        _downloadQuality.value = sharedPreferences.getString("download_quality", "Lossless (FLAC)") ?: "Lossless (FLAC)"
        _downloadFolder.value = sharedPreferences.getString("download_folder", "Music/Mueso") ?: "Music/Mueso"
        _enableLyrics.value = sharedPreferences.getBoolean("enable_lyrics", true)
        _embedLyricsInDownload.value = sharedPreferences.getBoolean("embed_lyrics_in_download", true)
        _enableSponsorBlock.value = sharedPreferences.getBoolean("enable_sponsorblock", true)
        _skipSponsor.value = sharedPreferences.getBoolean("skip_sponsor", true)
        _skipSelfPromo.value = sharedPreferences.getBoolean("skip_self_promo", true)
        _skipInteraction.value = sharedPreferences.getBoolean("skip_interaction", true)
        _skipIntroOutro.value = sharedPreferences.getBoolean("skip_intro_outro", true)
        _skipNonMusicOffTopic.value = sharedPreferences.getBoolean("skip_non_music_off_topic", true)
        _playButtonPosition.value = sharedPreferences.getString("play_button_position", "Left") ?: "Left"
        _losslessStreamingEnabled.value = sharedPreferences.getBoolean("lossless_streaming_enabled", false)
        _losslessServerUrl.value = sharedPreferences.getString("lossless_server_url", com.akshay.musicplayer.data.remote.lossless.LosslessMusicRepository.DEFAULT_SERVER_URL)
            ?.let { if (it.contains("clashflac.kanjijewels.com", ignoreCase = true)) com.akshay.musicplayer.data.remote.lossless.LosslessMusicRepository.DEFAULT_SERVER_URL else it }
            ?: com.akshay.musicplayer.data.remote.lossless.LosslessMusicRepository.DEFAULT_SERVER_URL
        _isStudioMasterClarityEnabled.value = sharedPreferences.getBoolean("studio_master_clarity", false)
        _isBitPerfectEnabled.value = sharedPreferences.getBoolean("bit_perfect_mode", false)
        _crossfadeEnabled.value = sharedPreferences.getBoolean("crossfade_enabled", false)
        _crossfadeSeconds.value = sharedPreferences.getInt("crossfade_seconds", 5)
        _playerLayoutStyle.value = sharedPreferences.getString("player_layout_style", "reels") ?: "reels"
    }
}
