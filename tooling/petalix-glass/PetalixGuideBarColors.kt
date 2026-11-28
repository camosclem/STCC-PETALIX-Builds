package com.nuvio.tv.livetv.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** Dark tinted glass keeps white programme text readable and follows the guide's selected colors. */
internal fun petalixGuideBarColor(base: Color, accent: Color, darkness: String, focused: Boolean): Color {
    val tinted = lerp(base, accent, if (focused) .52f else .32f)
    val lifted = lerp(tinted, Color.White, if (focused) .12f else .055f)
    val opacity = when (darkness) { "solid" -> .98f; "darker" -> .55f; else -> .35f }
    return lifted.copy(alpha = if (focused) when (darkness) {
        "solid" -> .98f; "darker" -> .72f; else -> .55f
    } else opacity)
}
