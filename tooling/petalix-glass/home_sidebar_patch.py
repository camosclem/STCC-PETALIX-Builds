"""Return navigation to the native side blade instead of a floating top menu."""
from pathlib import Path


def replace(path: Path, old: str, new: str):
    text = path.read_text()
    if text.count(old) != 1:
        raise ValueError("Navigation source changed: " + path.name)
    path.write_text(text.replace(old, new))


def apply(root: Path):
    source = root / "app/src/main/java/com/nuvio/tv"
    replace(source / "MainActivity.kt", '''                                PetalixNavigationShell(
                                    navController = navController,
                                    startDestination = startDestination,
                                    currentRoute = currentRoute,
                                    rootRoutes = rootRoutes,
                                    items = drawerItems,
                                    showProfiles = profiles.size > 1,
                                    onSwitchProfile = handleSwitchProfile,
                                    onExit = handleExitApp,
                                    navigate = { target -> navigateToDrawerRoute(navController, currentRoute, target) }
                                )''', '''                                val hiddenNavigation = com.nuvio.tv.petalix.petalixHomeLayout().hiddenToolbarTabs
                                val sidebarItems = drawerItems.filterNot { item ->
                                    if (item.route == Screen.OnDemand.route)
                                        "movies" in hiddenNavigation && "series" in hiddenNavigation
                                    else item.route in hiddenNavigation
                                }.ifEmpty { drawerItems }
                                val selectedSidebarItem = sidebarItems.firstOrNull { it.route == currentRoute }
                                    ?: sidebarItems.first()
                                if (mainUiPrefs.modernSidebarEnabled) {
                                    ModernSidebarScaffold(
                                        longPressBackHeld = longPressBackHeld,
                                        navController = navController,
                                        startDestination = startDestination,
                                        currentRoute = currentRoute,
                                        rootRoutes = rootRoutes,
                                        drawerItems = sidebarItems,
                                        selectedDrawerRoute = selectedSidebarItem.route,
                                        selectedDrawerItem = selectedSidebarItem,
                                        sidebarCollapsed = mainUiPrefs.sidebarCollapsed,
                                        modernSidebarBlurEnabled = mainUiPrefs.modernSidebarBlurPref,
                                        hideBuiltInHeaders = true,
                                        activeProfileName = activeProfile?.name.orEmpty(),
                                        activeProfileColorHex = activeProfile?.avatarColorHex ?: "#1E88E5",
                                        activeProfileAvatarImageUrl = activeProfileAvatarImageUrl,
                                        showProfileSelector = profiles.size > 1,
                                        onSwitchProfile = handleSwitchProfile,
                                        onNavigate = { target -> navigateToDrawerRoute(navController, currentRoute, target) },
                                        onExitApp = handleExitApp
                                    )
                                } else {
                                    LegacySidebarScaffold(
                                        longPressBackHeld = longPressBackHeld,
                                        navController = navController,
                                        startDestination = startDestination,
                                        currentRoute = currentRoute,
                                        rootRoutes = rootRoutes,
                                        drawerItems = sidebarItems,
                                        selectedDrawerRoute = selectedSidebarItem.route,
                                        sidebarCollapsed = mainUiPrefs.sidebarCollapsed,
                                        hideBuiltInHeaders = true,
                                        activeProfileName = activeProfile?.name.orEmpty(),
                                        activeProfileColorHex = activeProfile?.avatarColorHex ?: "#1E88E5",
                                        activeProfileAvatarImageUrl = activeProfileAvatarImageUrl,
                                        showProfileSelector = profiles.size > 1,
                                        onSwitchProfile = handleSwitchProfile,
                                        onNavigate = { target -> navigateToDrawerRoute(navController, currentRoute, target) },
                                        onExitApp = handleExitApp
                                    )
                                }''')
    replace(source / "ui/screens/home/ModernHomeContent.kt", '''            val openHomeTab = LocalPetalixHomeTab.current
            if (openHomeTab != null) PetalixHomeNavigation(
                Modifier.align(Alignment.TopCenter).padding(top = 12.dp), contentFocusRequester, openHomeTab
            ) else PetalixCinemaHeader(Modifier.align(Alignment.TopStart).padding(start = rowHorizontalPadding, top = 20.dp))''',
            '''            PetalixCinemaHeader(Modifier.align(Alignment.TopStart).padding(start = rowHorizontalPadding, top = 20.dp))''')
