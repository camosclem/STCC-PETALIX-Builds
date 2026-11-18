package com.nuvio.tv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

internal enum class HomeTab(val label: String) {
    HOME("Home"), SEARCH("Search"), LIVE("Live TV"), SPORTS("Sports"), LIBRARY("Library"), MOVIES("Movies"), SERIES("Series"), SETTINGS("Settings")
}

internal val LocalPetalixHomeTab = staticCompositionLocalOf<((HomeTab) -> Unit)?> { null }

@Composable
internal fun PetalixHomeNavigation(modifier: Modifier, contentFocus: FocusRequester, open: (HomeTab) -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Row(modifier.clip(shape).background(Color(0xDD0A101B)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        HomeTab.entries.forEach { tab ->
            var focused by remember { mutableStateOf(false) }
            val home = tab == HomeTab.HOME
            Text(
                tab.label, fontSize = 14.sp, fontWeight = if (home || focused) FontWeight.Bold else FontWeight.Medium,
                color = if (focused) Color(0xFF101725) else Color.White,
                modifier = Modifier.clip(shape)
                    .background(when { focused -> Color(0xFFF1F3F7); home -> Color(0xFF333D4B); else -> Color.Transparent })
                    .semantics { selected = home }
                    .onFocusChanged { focused = it.isFocused }
                    .onPreviewKeyEvent {
                        if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionDown)
                            runCatching { contentFocus.requestFocus(FocusDirection.Enter) }.getOrDefault(false)
                        else false
                    }
                    .clickable { if (home) runCatching { contentFocus.requestFocus() } else open(tab) }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }
    }
}
