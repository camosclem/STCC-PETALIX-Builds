package com.nuvio.tv.petalix

internal object PetalixCatalogPolicy {
    data class Chart(val title: String, val path: String, val tv: Boolean = false)
    val charts = listOf(
        Chart("Trending Movies Today", "trending/movie/day"),
        Chart("Trending TV Today", "trending/tv/day", true),
        Chart("Popular Movies", "movie/popular"),
        Chart("Popular TV Shows", "tv/popular", true),
        Chart("In Cinemas", "movie/now_playing"),
        Chart("TV Airing Today", "tv/airing_today", true),
        Chart("Top Rated Movies", "movie/top_rated")
    )
    fun shouldRefresh(now: Long, fetchedAt: Long, failedAt: Long): Boolean =
        (fetchedAt <= 0 || now < fetchedAt || now - fetchedAt >= 30 * 60_000L) &&
        (failedAt <= 0 || now < failedAt || now - failedAt >= 5 * 60_000L)
}
