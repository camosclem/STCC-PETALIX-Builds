package com.nuvio.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.navigation.NavHostController
import com.nuvio.tv.livetv.home.LiveTvHomeFocus
import com.nuvio.tv.livetv.ondemand.OnDemandHomeTab
import com.nuvio.tv.livetv.ondemand.VodKind
import com.nuvio.tv.livetv.ui.LiveTvFullscreen
import com.nuvio.tv.ui.navigation.NuvioNavHost
import com.nuvio.tv.ui.navigation.Screen
import com.nuvio.tv.ui.screens.home.PetalixDestination

private data class Destination(val label: String, val route: String, val kind: VodKind? = null)

@Composable
internal fun PetalixNavigationShell(
    navController: NavHostController,
    startDestination: String,
    currentRoute: String?,
    rootRoutes: Set<String>,
    items: List<DrawerItem>,
    showProfiles: Boolean,
    onSwitchProfile: () -> Unit,
    onExit: () -> Unit,
    navigate: (String) -> Unit
) {
    var expanded by remember(currentRoute) { mutableStateOf(false) }
    var remoteGuide by remember(currentRoute) { mutableStateOf(false) }
    val contentFocus = remember { FocusRequester() }
    val fullscreen by LiveTvFullscreen.active.collectAsState()
    val openMenu = {
        if (!expanded) {
            LiveTvHomeFocus.menuOpened()
            expanded = true
        }
    }
    val closeMenu = {
        expanded = false
        if (currentRoute == Screen.Home.route) LiveTvHomeFocus.menuClosed()
    }
    BackHandler(enabled = currentRoute in rootRoutes && !fullscreen && !expanded) { openMenu() }
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(
            LocalOpenSidebar provides openMenu,
            LocalSidebarExpanded provides expanded,
            LocalContentFocusRequester provides contentFocus
        ) {
            NuvioNavHost(
                navController = navController, startDestination = startDestination,
                hideBuiltInHeaders = false, discoverIsRoot = Screen.Discover.route in rootRoutes
            )
        }
        if (remoteGuide) PetalixRemoteGuide(onClose = { remoteGuide = false })
        if (expanded && !remoteGuide) {
            val destinations = remember(items) {
                items.flatMap { item ->
                    if (item.route == Screen.OnDemand.route) listOf(
                        Destination("Movies", item.route, VodKind.MOVIE),
                        Destination("Series", item.route, VodKind.SERIES)
                    ) else listOf(Destination(item.label, item.route))
                }
            }
            val active = destinations.firstOrNull { it.route == currentRoute && (it.kind == null || it.kind == OnDemandHomeTab.last) }
            val initial = active ?: destinations.firstOrNull()
            val focus = remember { FocusRequester() }
            Popup(alignment = Alignment.TopCenter, onDismissRequest = closeMenu,
                properties = PopupProperties(focusable = true)) {
                Row(
                    Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                        .background(Color(0xFA161719), RoundedCornerShape(22.dp))
                        .horizontalScroll(rememberScrollState()).padding(5.dp)
                        .onPreviewKeyEvent {
                            if (it.key == Key.DirectionDown) {
                                if (it.type == KeyEventType.KeyUp) closeMenu()
                                true
                            } else false
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    destinations.forEach { item ->
                        PetalixDestination(item.label, item == active,
                            if (item == initial) Modifier.focusRequester(focus) else Modifier) {
                            closeMenu()
                            item.kind?.let { OnDemandHomeTab.last = it }
                            navigate(item.route)
                        }
                    }
                    PetalixDestination("Remote guide", false) { remoteGuide = true }
                    if (showProfiles) PetalixDestination("Profiles", false) { closeMenu(); onSwitchProfile() }
                    PetalixDestination("Exit", false) { onExit() }
                }
                LaunchedEffect(Unit) { withFrameNanos { }; if (initial != null) focus.requestFocus() }
            }
        }
    }
}
