package com.nuvio.tv.petalix

/** No source is assigned unless the panel explicitly enables a manifest. */
object PenguPlayAccess {
    fun canonical(url: String): String {
        val parts = url.trim().split('?', limit = 2)
        val path = parts[0].trimEnd('/').replace(Regex("/manifest\\.json$", RegexOption.IGNORE_CASE), "").trimEnd('/')
        return path + if (parts.size == 2) "?" + parts[1] else ""
    }
    fun fingerprint(url: String): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(canonical(url).lowercase().toByteArray()).joinToString("") { "%02x".format(it) }
    fun matches(url: String, assigned: List<String>): Boolean = assigned.any { canonical(it).equals(canonical(url), ignoreCase = true) }
    fun urls(current: List<String>, assigned: List<String>, enabled: Boolean, ensure: Boolean, blocked: Set<String> = emptySet()): List<String> {
        val allowed = if (enabled) assigned else emptyList()
        val clean = current.filterNot { candidate -> (matches(candidate, assigned) && !enabled) || (fingerprint(candidate) in blocked && !matches(candidate, allowed)) }
        return if (enabled && ensure) allowed + clean.filterNot { matches(it, allowed) } else clean
    }
    fun states(current: Map<String, Boolean>, assigned: List<String>, enabled: Boolean, ensure: Boolean, blocked: Set<String> = emptySet()): Map<String, Boolean> {
        val allowed = if (enabled) assigned else emptyList()
        val clean = current.mapValues { (url, value) -> if (fingerprint(url) in blocked && !matches(url, allowed) || !enabled && matches(url, assigned)) false else if (ensure && matches(url, allowed)) true else value }
        return if (enabled && ensure) clean + allowed.associateWith { true } else clean
    }
}
