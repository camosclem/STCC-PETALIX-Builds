package com.nuvio.tv.petalix

/** Apply the access rule to every profile, including URL aliases and later imports. */
object PenguPlayAccess {
    private fun canonical(url: String): String {
        val parts = url.trim().split('?', limit = 2)
        val path = parts[0].trimEnd('/').replace(Regex("/manifest\\.json$", RegexOption.IGNORE_CASE), "").trimEnd('/')
        return path + if (parts.size == 2) "?" + parts[1] else ""
    }
    fun matches(url: String, bundled: List<String>): Boolean =
        bundled.any { canonical(it).equals(canonical(url), ignoreCase = true) }

    fun urls(current: List<String>, bundled: List<String>, enabled: Boolean, ensure: Boolean): List<String> =
        if (!enabled) current.filterNot { matches(it, bundled) }
        else if (ensure) bundled + current.filterNot { matches(it, bundled) }
        else current

    fun states(current: Map<String, Boolean>, bundled: List<String>, enabled: Boolean, ensure: Boolean): Map<String, Boolean> =
        if (!enabled) current.mapValues { (url, value) -> if (matches(url, bundled)) false else value } + bundled.associateWith { false }
        else if (ensure) current.mapValues { (url, value) -> if (matches(url, bundled)) true else value } + bundled.associateWith { true }
        else current
}
