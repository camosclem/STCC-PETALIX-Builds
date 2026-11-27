package com.nuvio.tv.petalix

internal const val GUIDE_LOCAL_CHOICES = "petalix_guide_local_choices_v1"

/** Only fields actually changed by this operation may be written. */
internal fun guideSettingChanges(before: Map<String, Any>, after: Map<String, Any>): Set<String> =
    (before.keys + after.keys).filterTo(LinkedHashSet()) { before[it] != after[it] }

internal fun guideWritableSettings(changed: Set<String>, local: Set<String>, userEdit: Boolean): Set<String> =
    if (userEdit) changed else changed - local
