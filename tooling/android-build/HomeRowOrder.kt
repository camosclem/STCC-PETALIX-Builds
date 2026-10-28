package com.nuvio.tv.petalix

internal const val STREAMING_SERVICES_ROW = "collection_collection-streaming-services-only"

internal fun isStreamingServicesRow(key: String?): Boolean = key == STREAMING_SERVICES_ROW

/** Preserve the native rows and their relative order; promote only our service row. */
internal fun <T> streamingServicesFirst(rows: List<T>, key: (T) -> String): List<T> {
    if (rows.none { isStreamingServicesRow(key(it)) }) return rows
    return rows.filter { isStreamingServicesRow(key(it)) } +
        rows.filterNot { isStreamingServicesRow(key(it)) || key(it).startsWith("catalog_") || key(it).startsWith("collection_") }
}

internal fun liveTvBeforeRow(here: Boolean, above: Boolean?, hasContinueWatching: Boolean, key: String): Boolean =
    here && !isStreamingServicesRow(key) && (above == true || !hasContinueWatching)

internal fun liveTvAfterRow(here: Boolean, above: Boolean?, hasContinueWatching: Boolean, key: String): Boolean =
    here && if (isStreamingServicesRow(key)) !hasContinueWatching else above == false && hasContinueWatching

internal fun liveTvAboveFirstRow(above: Boolean?, available: Boolean, firstKey: String?): Boolean =
    above == true && available && !isStreamingServicesRow(firstKey)
