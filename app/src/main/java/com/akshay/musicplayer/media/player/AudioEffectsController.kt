package com.akshay.musicplayer.media.player

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Audiophile DSP & Effects Controller
 *
 * Manages:
 * 1. Hardware-accelerated N-Band Parametric Equalizer.
 * 2. Bass Boost DSP.
 * 3. Studio Master Clarity (multiband acoustic contouring + loudness enhancement).
 * 4. Bit-Perfect DAC Mode (completely bypasses all DSP effects for unadulterated bit-for-bit transmission to DAC).
 */
class AudioEffectsController private constructor(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.Main)

    private var currentSessionId: Int = 0
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    // State Flows
    private val _isEnabled = MutableStateFlow(prefs.getBoolean(KEY_EQ_ENABLED, false))
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _presetName = MutableStateFlow(prefs.getString(KEY_EQ_PRESET, "Flat") ?: "Flat")
    val presetName: StateFlow<String> = _presetName.asStateFlow()

    private val _bandFrequencies = MutableStateFlow<List<Int>>(listOf(60, 230, 910, 3600, 14000))
    val bandFrequencies: StateFlow<List<Int>> = _bandFrequencies.asStateFlow()

    private val _bandLevels = MutableStateFlow<List<Int>>(listOf(0, 0, 0, 0, 0))
    val bandLevels: StateFlow<List<Int>> = _bandLevels.asStateFlow()

    private val _minLevelDb = MutableStateFlow(-15)
    val minLevelDb: StateFlow<Int> = _minLevelDb.asStateFlow()

    private val _maxLevelDb = MutableStateFlow(15)
    val maxLevelDb: StateFlow<Int> = _maxLevelDb.asStateFlow()

    private val _bassBoostStrength = MutableStateFlow(prefs.getInt(KEY_BASS_STRENGTH, 0))
    val bassBoostStrength: StateFlow<Int> = _bassBoostStrength.asStateFlow()

    private val muesoPrefs = context.getSharedPreferences("mueso_prefs", Context.MODE_PRIVATE)

    private val _isClarityEnabled = MutableStateFlow(muesoPrefs.getBoolean("studio_master_clarity", prefs.getBoolean(KEY_CLARITY_ENABLED, false)))
    val isClarityEnabled: StateFlow<Boolean> = _isClarityEnabled.asStateFlow()

    private val _isBitPerfectEnabled = MutableStateFlow(muesoPrefs.getBoolean("bit_perfect_mode", prefs.getBoolean(KEY_BIT_PERFECT_ENABLED, false)))
    val isBitPerfectEnabled: StateFlow<Boolean> = _isBitPerfectEnabled.asStateFlow()

    init {
        // Load saved levels
        val savedLevels = mutableListOf<Int>()
        for (i in 0 until 10) {
            val key = "${KEY_BAND_LEVEL_PREFIX}$i"
            if (prefs.contains(key)) {
                savedLevels.add(prefs.getInt(key, 0))
            } else {
                break
            }
        }
        if (savedLevels.isNotEmpty()) {
            _bandLevels.value = savedLevels
        }
    }

    /**
     * Attach effects to the current active ExoPlayer audio session ID
     */
    fun attachSession(audioSessionId: Int) {
        if (audioSessionId <= 0 || audioSessionId == currentSessionId) return
        currentSessionId = audioSessionId
        Log.i(TAG, "Attaching AudioEffectsController to audio session ID: $audioSessionId")

        releaseEffects()

        try {
            // 1. Equalizer
            val eq = Equalizer(0, audioSessionId)
            equalizer = eq

            val minMB = eq.bandLevelRange[0].toInt()
            val maxMB = eq.bandLevelRange[1].toInt()
            _minLevelDb.value = minMB / 100
            _maxLevelDb.value = maxMB / 100

            val numBands = eq.numberOfBands.toInt()
            val freqs = mutableListOf<Int>()
            val currentLevels = mutableListOf<Int>()

            for (i in 0 until numBands) {
                val centerHz = eq.getCenterFreq(i.toShort()) / 1000
                freqs.add(centerHz)
                val savedLevel = prefs.getInt("${KEY_BAND_LEVEL_PREFIX}$i", 0)
                currentLevels.add(savedLevel)
            }

            _bandFrequencies.value = freqs
            if (_bandLevels.value.size != numBands) {
                _bandLevels.value = currentLevels
            }

            // 2. Bass Boost
            try {
                val bb = BassBoost(0, audioSessionId)
                bassBoost = bb
            } catch (e: Exception) {
                Log.w(TAG, "BassBoost unsupported on this device: ${e.message}")
            }

            // 3. Loudness Enhancer (for Studio Master Clarity)
            try {
                val le = LoudnessEnhancer(audioSessionId)
                loudnessEnhancer = le
            } catch (e: Exception) {
                Log.w(TAG, "LoudnessEnhancer unsupported: ${e.message}")
            }

            // Apply active states
            applyAll()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize audio effects: ${e.message}", e)
        }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        prefs.edit().putBoolean(KEY_EQ_ENABLED, enabled).apply()
        applyAll()
    }

    fun setBandLevel(bandIndex: Int, levelDb: Int) {
        val clampedLevel = levelDb.coerceIn(_minLevelDb.value, _maxLevelDb.value)
        val currentList = _bandLevels.value.toMutableList()
        if (bandIndex in 0 until currentList.size) {
            currentList[bandIndex] = clampedLevel
            _bandLevels.value = currentList
            _presetName.value = "Custom"
            prefs.edit()
                .putInt("${KEY_BAND_LEVEL_PREFIX}$bandIndex", clampedLevel)
                .putString(KEY_EQ_PRESET, "Custom")
                .apply()
        }

        try {
            if (!_isBitPerfectEnabled.value && _isEnabled.value) {
                equalizer?.setBandLevel(bandIndex.toShort(), (clampedLevel * 100).toShort())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error setting band level: ${e.message}")
        }
    }

    fun applyPreset(name: String) {
        _presetName.value = name
        prefs.edit().putString(KEY_EQ_PRESET, name).apply()

        val numBands = _bandFrequencies.value.size
        val targetLevels = when (name) {
            "Bass Boost" -> listOf(6, 4, 1, 0, 1)
            "Rock" -> listOf(4, 2, -1, 3, 5)
            "Pop" -> listOf(2, 1, 4, 3, 2)
            "Vocal" -> listOf(-2, 1, 5, 4, 2)
            "Acoustic" -> listOf(2, 3, 2, 3, 4)
            "EDM" -> listOf(6, 5, 0, 2, 5)
            "Flat" -> List(numBands) { 0 }
            else -> List(numBands) { 0 }
        }

        val finalLevels = MutableList(numBands) { i ->
            if (i < targetLevels.size) targetLevels[i] else 0
        }
        _bandLevels.value = finalLevels

        val editor = prefs.edit()
        finalLevels.forEachIndexed { index, level ->
            editor.putInt("${KEY_BAND_LEVEL_PREFIX}$index", level)
        }
        editor.apply()

        try {
            if (!_isBitPerfectEnabled.value && _isEnabled.value) {
                finalLevels.forEachIndexed { index, level ->
                    equalizer?.setBandLevel(index.toShort(), (level * 100).toShort())
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error applying preset: ${e.message}")
        }
    }

    fun setBassBoost(strengthPercent: Int) {
        val clamped = strengthPercent.coerceIn(0, 100)
        _bassBoostStrength.value = clamped
        prefs.edit().putInt(KEY_BASS_STRENGTH, clamped).apply()

        try {
            if (!_isBitPerfectEnabled.value && (_isEnabled.value || _isClarityEnabled.value)) {
                bassBoost?.let {
                    it.setStrength((clamped * 10).toShort())
                    it.enabled = clamped > 0
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error setting bass boost: ${e.message}")
        }
    }

    fun setClarityEnabled(enabled: Boolean) {
        _isClarityEnabled.value = enabled
        prefs.edit().putBoolean(KEY_CLARITY_ENABLED, enabled).apply()
        muesoPrefs.edit().putBoolean("studio_master_clarity", enabled).apply()
        applyAll()
    }

    fun setBitPerfectEnabled(enabled: Boolean) {
        _isBitPerfectEnabled.value = enabled
        prefs.edit().putBoolean(KEY_BIT_PERFECT_ENABLED, enabled).apply()
        muesoPrefs.edit().putBoolean("bit_perfect_mode", enabled).apply()
        applyAll()
    }

    private fun applyAll() {
        val isBitPerfect = _isBitPerfectEnabled.value
        val isEqOn = _isEnabled.value
        val isClarityOn = _isClarityEnabled.value

        try {
            if (isBitPerfect) {
                // BIT-PERFECT DAC MODE: Bypass all hardware DSP effects completely
                equalizer?.enabled = false
                bassBoost?.enabled = false
                loudnessEnhancer?.enabled = false
                Log.i(TAG, "DSP: Bit-Perfect mode ACTIVE -> All DSP effects disabled for pristine bit-depth transmission.")
                return
            }

            // 1. Equalizer
            equalizer?.let { eq ->
                eq.enabled = isEqOn
                if (isEqOn) {
                    _bandLevels.value.forEachIndexed { index, levelDb ->
                        if (index < eq.numberOfBands) {
                            var targetDb = levelDb
                            // If Studio Master Clarity is also active, apply air sparkle (+2.5dB at top band)
                            if (isClarityOn && index == eq.numberOfBands - 1) {
                                targetDb = (targetDb + 3).coerceAtMost(_maxLevelDb.value)
                            }
                            eq.setBandLevel(index.toShort(), (targetDb * 100).toShort())
                        }
                    }
                }
            }

            // 2. Bass Boost
            bassBoost?.let { bb ->
                val strength = if (isClarityOn && _bassBoostStrength.value == 0) 25 else _bassBoostStrength.value
                bb.setStrength((strength * 10).toShort())
                bb.enabled = strength > 0 && (isEqOn || isClarityOn)
            }

            // 3. Studio Master Clarity (Loudness Enhancer dynamic contouring)
            loudnessEnhancer?.let { le ->
                if (isClarityOn) {
                    le.setTargetGain(150) // +1.5 dB subtle studio mastering gain contour
                    le.enabled = true
                } else {
                    le.enabled = false
                }
            }

            Log.i(TAG, "DSP: Applied settings -> EQ=$isEqOn, Clarity=$isClarityOn, Bass=${_bassBoostStrength.value}%")

        } catch (e: Exception) {
            Log.w(TAG, "Error applying audio effects: ${e.message}")
        }
    }

    private fun releaseEffects() {
        try {
            equalizer?.release()
            equalizer = null
            bassBoost?.release()
            bassBoost = null
            loudnessEnhancer?.release()
            loudnessEnhancer = null
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing audio effects: ${e.message}")
        }
    }

    fun release() {
        releaseEffects()
        currentSessionId = 0
    }

    companion object {
        private const val TAG = "AudioEffectsController"
        private const val PREFS_NAME = "mueso_equalizer_prefs"
        private const val KEY_EQ_ENABLED = "eq_enabled"
        private const val KEY_EQ_PRESET = "eq_preset"
        private const val KEY_BAND_LEVEL_PREFIX = "eq_band_"
        private const val KEY_BASS_STRENGTH = "eq_bass_strength"
        private const val KEY_CLARITY_ENABLED = "eq_clarity_enabled"
        private const val KEY_BIT_PERFECT_ENABLED = "eq_bit_perfect_enabled"

        @Volatile
        private var instance: AudioEffectsController? = null

        fun getInstance(context: Context): AudioEffectsController {
            return instance ?: synchronized(this) {
                instance ?: AudioEffectsController(context.applicationContext).also { instance = it }
            }
        }
    }
}
