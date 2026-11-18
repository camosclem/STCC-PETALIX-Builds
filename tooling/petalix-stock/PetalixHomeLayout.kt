package com.nuvio.tv.petalix

import org.json.JSONObject

/** Home defaults for this build; explicit panel preferences can change each section. */
data class PetalixHomeLayout(
    val showLiveTv: Boolean = true,
    val showRecentLiveTv: Boolean = true,
    val showServices: Boolean = true,
    val charts: Set<String> = setOf("trending/movie/day", "trending/tv/day"),
    val serviceArtwork: Map<String, String> = emptyMap()
) {
    companion object {
        fun from(values: Map<String, Any>): PetalixHomeLayout {
            fun enabled(key: String, default: Boolean) = values[key] as? Boolean ?: default
            val artwork = runCatching {
                val json = JSONObject(values["petalix_home_service_artwork"] as? String ?: "{}")
                json.keys().asSequence().mapNotNull { id ->
                    val url = json.optString(id)
                    val uri = runCatching { java.net.URI(url) }.getOrNull()
                    if (!id.startsWith("folder-") || uri?.scheme != "https" || uri.host.isNullOrBlank() ||
                        uri.userInfo != null || uri.fragment != null) null else id to url
                }.toMap()
            }.getOrDefault(emptyMap())
            return PetalixHomeLayout(
                showLiveTv = enabled("petalix_home_live_tv", true),
                showRecentLiveTv = enabled("petalix_home_recent_live_tv", true),
                showServices = enabled("petalix_home_services", true),
                charts = PetalixCatalogPolicy.charts.filter {
                    enabled(it.preference, it.defaultEnabled)
                }.map { it.path }.toSet(),
                serviceArtwork = artwork
            )
        }
    }
}
