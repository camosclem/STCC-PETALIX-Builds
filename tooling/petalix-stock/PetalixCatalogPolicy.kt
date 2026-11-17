package com.nuvio.tv.petalix

internal object PetalixCatalogPolicy {
    data class Chart(val title: String, val path: String, val tv: Boolean = false,
        val preference: String, val defaultEnabled: Boolean = false)
    val charts = listOf(
        Chart("Trending Movies Today", "trending/movie/day", preference = "petalix_home_trending_movies", defaultEnabled = true),
        Chart("Trending TV Today", "trending/tv/day", true, "petalix_home_trending_tv", true),
        Chart("Popular Movies", "movie/popular", preference = "petalix_home_popular_movies"),
        Chart("Popular TV Shows", "tv/popular", true, "petalix_home_popular_tv"),
        Chart("In Cinemas", "movie/now_playing", preference = "petalix_home_cinemas"),
        Chart("TV Airing Today", "tv/airing_today", true, "petalix_home_airing_tv"),
        Chart("Top Rated Movies", "movie/top_rated", preference = "petalix_home_top_movies")
    )
    fun shouldRefresh(now: Long, fetchedAt: Long, failedAt: Long): Boolean =
        (fetchedAt <= 0 || now < fetchedAt || now - fetchedAt >= 30 * 60_000L) &&
        (failedAt <= 0 || now < failedAt || now - failedAt >= 5 * 60_000L)
}
