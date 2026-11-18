package com.nuvio.tv.petalix

import com.nuvio.tv.livetv.model.ChannelGroup

/** Navigation only: return an existing native group, never build or replace a guide. */
internal fun petalixNativeHomeGroup(target: String, groups: List<ChannelGroup>): String? {
    val nativeId = when (target) {
        "native:recent" -> ChannelGroup.RECENT
        "native:favorites" -> ChannelGroup.FAVORITES
        else -> null
    }
    if (nativeId != null) return groups.firstOrNull { it.id == nativeId }?.id
    val names = when (target) {
        "Sports" -> listOf("Sports Networks", "Sports")
        "24/7 Channels" -> listOf("24/7 Channels", "24/7", "24/7 TV")
        else -> listOf(target)
    }
    return names.firstNotNullOfOrNull { name -> groups.firstOrNull { it.title.trim().equals(name, true) }?.id }
}
