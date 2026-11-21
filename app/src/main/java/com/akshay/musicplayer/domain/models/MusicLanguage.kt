package com.akshay.musicplayer.domain.models

data class MusicLanguage(
    val code: String,
    val name: String,
    val nativeScript: String,
    val nativeName: String,
    val sampleArtists: String = ""
)

object SupportedLanguages {
    val ALL = listOf(
        MusicLanguage("auto", "Auto", "YT", "Auto (Dynamic YT Music)", "Dynamic recommendations directly from YouTube servers based on time, location & taste"),
        MusicLanguage("te", "Telugu", "తె", "తెలుగు", "SPB, Sid Sriram, DSP, Thaman"),
        MusicLanguage("hi", "Hindi", "अ", "हिंदी", "Arijit Singh, Shreya Ghoshal, Pritam"),
        MusicLanguage("ta", "Tamil", "த", "தமிழ்", "Anirudh, AR Rahman, Yuvan, Harris"),
        MusicLanguage("pa", "Punjabi", "ਪ", "ਪੰਜਾਬੀ", "Diljit Dosanjh, AP Dhillon, Sidhu Moosewala"),
        MusicLanguage("en", "English", "En", "English", "Taylor Swift, The Weeknd, Drake, Eminem"),
        MusicLanguage("ml", "Malayalam", "മ", "മലയാളം", "Sushin Shyam, KS Harisankar, Job Kurian"),
        MusicLanguage("kn", "Kannada", "ಕ", "ಕನ್ನಡ", "Sanjith Hegde, Charan Raj, Ravi Basrur"),
        MusicLanguage("bn", "Bengali", "ব", "বাংলা", "Anupam Roy, Shreya Ghoshal, Arijit Singh"),
        MusicLanguage("mr", "Marathi", "म", "मराठी", "Ajay-Atul, Swapnil Bandodkar, Avadhoot"),
        MusicLanguage("gu", "Gujarati", "ગ", "ગુજરાતી", "Sachin-Jigar, Jigardan Gadhvi, Kinjal Dave"),
        MusicLanguage("bho", "Bhojpuri", "भ", "भोजपुरी", "Pawan Singh, Khesari Lal Yadav, Shilpi Raj"),
        MusicLanguage("ko", "Korean", "한", "한국어", "BTS, BLACKPINK, NewJeans, Stray Kids"),
        MusicLanguage("es", "Spanish", "Es", "Español", "Bad Bunny, Rosalía, J Balvin, Shakira")
    )

    fun getLanguageByName(name: String?): MusicLanguage {
        if (name.isNullOrBlank() || name.trim().equals("auto", ignoreCase = true) || name.trim().equals("default", ignoreCase = true)) {
            return ALL[0] // Auto
        }
        return ALL.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) || it.code.equals(name.trim(), ignoreCase = true) }
            ?: MusicLanguage(
                code = name.trim().take(2).lowercase(),
                name = name.trim(),
                nativeScript = name.trim().take(2).replaceFirstChar { it.uppercase() },
                nativeName = name.trim(),
                sampleArtists = ""
            )
    }

    fun getNativeScript(name: String?): String {
        return getLanguageByName(name).nativeScript
    }

    fun getNativeName(name: String?): String {
        return getLanguageByName(name).nativeName
    }
}
