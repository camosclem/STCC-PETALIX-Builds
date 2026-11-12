package com.nuvio.tv.petalix

internal fun isPetalixVodType(type: String): Boolean = type.lowercase() in setOf("movie", "series", "tv")

internal fun canLookUpPlayback(type: String, videoId: String, contentId: String, episode: Int?): Boolean =
    videoId.isNotBlank() && (type.equals("movie", true) || (isPetalixVodType(type) && (episode != null || videoId != contentId)))

private val tmdbVideo = Regex("tmdb:([0-9]+)(?::([0-9]+):([0-9]+))?", RegexOption.IGNORE_CASE)
private val imdbVideo = Regex("(tt[0-9]+)(?::([0-9]+):([0-9]+))?")

internal fun tmdbPlaybackId(videoId: String): Int? =
    tmdbVideo.matchEntire(videoId)?.groupValues?.get(1)?.toIntOrNull()

internal fun isImdbPlaybackId(videoId: String): Boolean = imdbVideo.matches(videoId)

internal fun alternatePlaybackId(videoId: String, type: String, target: String, season: Int?, episode: Int?): String? {
    val original = tmdbVideo.matchEntire(videoId) ?: imdbVideo.matchEntire(videoId) ?: return null
    return when (type.lowercase()) {
        "movie" -> target.takeIf { original.groupValues[2].isEmpty() }
        "series", "tv" -> {
            val s = original.groupValues[2].toIntOrNull() ?: season ?: return null
            val e = original.groupValues[3].toIntOrNull() ?: episode ?: return null
            "$target:$s:$e"
        }
        else -> null
    }
}
