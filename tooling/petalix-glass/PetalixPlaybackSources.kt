package com.nuvio.tv.petalix

/** A retired cached Pengu addon must not become a source when no account assignment exists. */
internal fun petalixPlaybackAddonAllowed(id: String, name: String, url: String, assignedPengu: String?): Boolean {
    val pengu = listOf(id, name, url).any { label ->
        val compact = label.lowercase().filter(Char::isLetterOrDigit)
        compact == "pengu" || compact.contains("penguplay") ||
            Regex("(?i)(^|[./:_-])pengu([./:_-]|$)").containsMatchIn(label)
    }
    return !pengu || (!assignedPengu.isNullOrBlank() && url == assignedPengu)
}
