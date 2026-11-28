package com.nuvio.tv.petalix

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.nuvio.tv.livetv.ui.petalixGuideBarColor
import org.junit.Assert.*
import org.junit.Test

class PetalixGuideBarColorsTest {
    @Test fun guideBarsFollowTheirAccentAndKeepWhiteTextReadable() {
        val blue = petalixGuideBarColor(Color(0xFF050D1A), Color(0xFF2B5FB0), "darker", false)
        val green = petalixGuideBarColor(Color(0xFF05130F), Color(0xFF1FA58B), "darker", false)
        assertNotEquals(blue, green)
        assertTrue(blue.blue > blue.red)
        assertTrue(green.green > green.red)
        for (color in listOf(blue, green)) assertTrue(1.05f / (color.luminance() + .05f) > 4.5f)
    }

    @Test fun focusIsVisibleAndTheUsersDarknessChoiceChangesTheGlass() {
        val base = Color(0xFF050D1A)
        val accent = Color(0xFF2B5FB0)
        val normal = petalixGuideBarColor(base, accent, "standard", false)
        val solid = petalixGuideBarColor(base, accent, "solid", false)
        val focused = petalixGuideBarColor(base, accent, "standard", true)
        assertTrue(normal.alpha < solid.alpha)
        assertTrue(focused.luminance() > normal.luminance())
        assertTrue(1.05f / (focused.luminance() + .05f) > 4.5f)
    }
}
