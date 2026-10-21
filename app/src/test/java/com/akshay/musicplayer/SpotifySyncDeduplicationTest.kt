package com.akshay.musicplayer

import org.junit.Assert.*
import org.junit.Test

class SpotifySyncDeduplicationTest {

    private val GENERIC_CHANNEL_TERMS = setOf(
        "vevo", "records", "music", "tseries", "series", "entertainment", "channel",
        "audio", "media", "production", "productions", "studios", "official", "sound", "sounds"
    )

    private fun cleanString(input: String): String {
        return input.lowercase()
            .replace(Regex("""\s*[\(\[\{].*?[\)\]\}]"""), "")
            .replace(Regex("""\b(feat|ft|featuring|remastered|remaster|version|edit|audio|video|official|lyric|lyrics|full)\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""[^\p{L}\p{Nd}]+"""), "")
            .trim()
    }

    private fun extractWords(input: String): List<String> {
        return input.lowercase()
            .replace(Regex("""\s*[\(\[\{].*?[\)\]\}]"""), "")
            .replace(Regex("""\b(feat|ft|featuring|remastered|remaster|version|edit|audio|video|official|lyric|lyrics|full|song)\b""", RegexOption.IGNORE_CASE), " ")
            .split(Regex("""[^\p{L}\p{Nd}]+"""))
            .filter { it.length >= 2 }
    }

    private fun isTrackAlreadyPresent(
        spotifyTitle: String,
        spotifyArtist: String,
        existingTracks: List<Pair<String, String>>
    ): Boolean {
        val cleanSpotifyTitle = cleanString(spotifyTitle)
        val spotifyTitleWords = extractWords(spotifyTitle)
        val spotifyArtistWords = extractWords(spotifyArtist)

        if (cleanSpotifyTitle.isEmpty()) return false

        return existingTracks.any { (existingTitle, existingArtist) ->
            val cleanExistingTitle = cleanString(existingTitle)
            val cleanExistingArtist = cleanString(existingArtist)
            val existingArtistWords = extractWords(existingArtist)

            val isExactTitle = cleanExistingTitle == cleanSpotifyTitle
            val isSubstringTitle = (cleanSpotifyTitle.length >= 4 && cleanExistingTitle.contains(cleanSpotifyTitle)) ||
                    (cleanExistingTitle.length >= 4 && cleanSpotifyTitle.contains(cleanExistingTitle))

            val isTokenTitle = spotifyTitleWords.size >= 2 && spotifyTitleWords.all { word ->
                cleanExistingTitle.contains(word)
            }

            val titleMatched = isExactTitle || isSubstringTitle || isTokenTitle
            if (!titleMatched) return@any false

            val existingFullText = "$cleanExistingTitle $cleanExistingArtist"
            val hasArtistOverlap = spotifyArtistWords.any { artistWord ->
                artistWord.length >= 3 && existingFullText.contains(artistWord)
            }

            if (hasArtistOverlap) return@any true

            // If artist field is only a generic uploader/channel name (e.g. "T-Series", "Sony Music"), trust the title match
            val isGenericChannel = existingArtistWords.isEmpty() || existingArtistWords.all { it in GENERIC_CHANNEL_TERMS }
            if (isGenericChannel && (isExactTitle || isSubstringTitle || isTokenTitle)) {
                return@any true
            }

            // Both have specific artists and they do NOT overlap at all -> likely different songs with same title (e.g. Adele - Hello vs Lionel Richie - Hello)
            false
        }
    }

    @Test
    fun testExactMatch() {
        val existing = listOf("Blinding Lights" to "The Weeknd")
        assertTrue(isTrackAlreadyPresent("Blinding Lights", "The Weeknd", existing))
    }

    @Test
    fun testYouTubeChannelAsArtistWithArtistInTitle() {
        // Typical Indian YouTube upload where artist is "T-Series" and real artist is in the title
        val existing = listOf("Chuttamalle - Video Song | Devara | Jr NTR, Janhvi Kapoor | Anirudh" to "T-Series")
        assertTrue(isTrackAlreadyPresent("Chuttamalle", "Anirudh Ravichander, Shilpa Rao", existing))

        val existing2 = listOf("Naatu Naatu Full Video Song | RRR | NTR, Ram Charan | MM Keeravaani" to "Lahari Music | T-Series")
        assertTrue(isTrackAlreadyPresent("Naatu Naatu", "Rahul Sipligunj, Kaala Bhairava, M.M. Keeravaani", existing2))
    }

    @Test
    fun testYouTubeVevoUpload() {
        val existing = listOf("The Weeknd - Starboy ft. Daft Punk (Official Music Video)" to "TheWeekndVEVO")
        assertTrue(isTrackAlreadyPresent("Starboy", "The Weeknd, Daft Punk", existing))
    }

    @Test
    fun testRemasterAndFeatStripped() {
        val existing = listOf("Starboy" to "The Weeknd, Daft Punk")
        assertTrue(isTrackAlreadyPresent("Starboy (feat. Daft Punk)", "The Weeknd", existing))

        val existing2 = listOf("In The End" to "Linkin Park")
        assertTrue(isTrackAlreadyPresent("In The End [2020 Remaster]", "Linkin Park", existing2))
    }

    @Test
    fun testDifferentSongDetectedAsNew() {
        val existing = listOf(
            "Blinding Lights" to "The Weeknd",
            "Starboy" to "The Weeknd"
        )
        assertFalse(isTrackAlreadyPresent("Save Your Tears", "The Weeknd", existing))
        assertFalse(isTrackAlreadyPresent("Die For You", "The Weeknd", existing))
    }

    @Test
    fun testSameTitleDifferentArtist() {
        val existing = listOf("Hello" to "Lionel Richie")
        assertFalse(isTrackAlreadyPresent("Hello", "Adele", existing))
    }
}
