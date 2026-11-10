package com.nuvio.tv.petalix

/** A customer's provider stream ID survives display-name, guide-ID and group edits. */
internal object PanelChannelIdentity {
    fun streamId(url: String): String? = runCatching {
        val path = java.net.URI(url).path.orEmpty()
        Regex("/live/[^/]+/[^/]+/([A-Za-z0-9_.-]+)\\.(?:ts|m3u8)$", RegexOption.IGNORE_CASE).find(path)?.groupValues?.get(1)
    }.getOrNull()
    fun key(source: String, url: String, fallback: String): String =
        if (source == "petalix-managed") streamId(url)?.let { "$source|stream:$it" } ?: fallback else fallback
    fun providerKey(id: String) = "petalix-managed|stream:$id"
}
