package com.nuvio.tv.petalix

internal const val PETALIX_ZIP_LOCALS = "custom:petalix_locals"
private val localGuideLabel = Regex("(?i)(?:^|[^a-z])(?:locals?|local stations|local tv|ota|affiliates?)(?:$|[^a-z])")

/** Hide duplicate group entries, never their channels or favorite identities. */
internal fun showPetalixGuideGroup(id: String, title: String, hasZipSelection: Boolean): Boolean =
    !hasZipSelection || id == PETALIX_ZIP_LOCALS || !localGuideLabel.containsMatchIn(title)
