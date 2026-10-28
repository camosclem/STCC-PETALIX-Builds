package com.nuvio.tv.petalix

import java.net.URI

/** Keep authenticated API requests on the two verified origins of this panel. */
internal object PanelConnectionPolicy {
    const val currentOrigin="https://petalix-control.caroliinaconsultant.chatgpt.site"
    private val hosts=setOf("petalix-control.caroliinaconsultant.chatgpt.site","petalix-control.pinkgiant5.chatgpt.site")
    fun redirected(from:String,location:String):String?=runCatching {
        val source=URI(from);val target=source.resolve(location)
        if(source.host !in hosts || target.host !in hosts || target.scheme!="https" || target.port !in setOf(-1,443) || target.userInfo!=null || target.rawQuery!=null || target.rawFragment!=null || target.path!=source.path || !target.path.startsWith("/api/device/"))null else target.toASCIIString()
    }.getOrNull()
}
