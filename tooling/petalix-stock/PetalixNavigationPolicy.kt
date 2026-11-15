package com.nuvio.tv.petalix

internal const val PETALIX_ZIP_LOCALS = "custom:petalix_locals"
private val localGuideLabel = Regex("(?i)(?:^|[^a-z])(?:locals?|local stations|local tv|ota|affiliates?)(?:$|[^a-z])")

/** Hide duplicate group entries, never their channels or favorite identities. */
internal fun showPetalixGuideGroup(id: String, title: String, hasZipSelection: Boolean): Boolean =
    !hasZipSelection || id == PETALIX_ZIP_LOCALS || !localGuideLabel.containsMatchIn(title)

/** A fast VOD response must not race ahead of Pengu; fallback begins after lookup completes. */
internal fun <T> preferredPetalixPlayback(
    playable: List<T>, lookupComplete: Boolean, isPengu: (T) -> Boolean, isVod: (T) -> Boolean
): T? = playable.firstOrNull(isPengu)
    ?: if (lookupComplete) playable.firstOrNull(isVod) else null
