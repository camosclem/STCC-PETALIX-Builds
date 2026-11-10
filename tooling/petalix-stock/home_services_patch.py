"""Keep service selection on native Home, including a usable offline/loading fallback.

Applies to the pinned Nuvio + IPTV 13.1 source after core Petalix branding/auth.
This does not add a Services destination or replace native catalog/Live TV rows.
"""
from pathlib import Path
import shutil

HERE = Path(__file__).resolve().parent


def once(path, old, new, count=1):
    text = path.read_text()
    found = text.count(old)
    if found != count:
        raise RuntimeError(f"Home services source anchor changed: {path.name}: expected {count}, found {found}: {old[:90]!r}")
    path.write_text(text.replace(old, new))


def apply(root):
    root = Path(root)
    source = root / "app/src/main/java/com/nuvio/tv"
    home = source / "ui/screens/home/HomeScreen.kt"
    modern = source / "ui/screens/home/ModernHomeContent.kt"
    presentation = source / "ui/screens/home/ModernHomePresentation.kt"
    nav = source / "ui/navigation/NuvioNavHost.kt"
    target = source / "petalix"
    target.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(HERE / "PetalixHomeServices.kt", target / "PetalixHomeServices.kt")

    if 'import androidx.compose.ui.zIndex' not in home.read_text():
        once(home,'import androidx.compose.ui.Modifier','import androidx.compose.ui.Modifier\nimport androidx.compose.ui.zIndex')
    # Keep folder navigation on Home; each folder opens its original service experience.
    once(home, "fun HomeScreen(\n", """fun HomeScreen(
    onPetalixLiveTv: () -> Unit = {},
    onPetalixSettings: () -> Unit = {},
""")
    once(nav, "            HomeScreen(\n", """            HomeScreen(
                onPetalixLiveTv = { navController.navigate(Screen.LiveTv.route) },
                onPetalixSettings = { navController.navigate(Screen.Settings.route) },
""")

    collection_anchor = "    val hasCollectionContent = uiState.homeRows.any { it is HomeRow.CollectionRow }\n"
    once(home, collection_anchor, collection_anchor + """    val hasPetalixServices = uiState.homeRows.any {
        it is HomeRow.CollectionRow &&
            it.collection.id == com.nuvio.tv.petalix.PETALIX_SERVICES_COLLECTION &&
            it.collection.folders.isNotEmpty()
    }
""")
    once(home, "    LaunchedEffect(Unit) {\n        // Safety timeout", """    // Local service folders are immediately usable; they do not depend on addon catalogs.
    LaunchedEffect(hasPetalixServices, modernPresentationReady) {
        if (hasPetalixServices && modernPresentationReady) homeStableGateReleased = true
    }

    LaunchedEffect(Unit) {
        // Safety timeout""")

    # Source errors must not obscure a collection already stored on the TV.
    once(home,
         "uiState.error == noAddonsError && uiState.catalogRows.isEmpty() ->",
         "uiState.error == noAddonsError && uiState.catalogRows.isEmpty() && !hasCollectionContent && !hasHeroContent ->",
         count=2)
    once(home,
         "uiState.error != null && uiState.catalogRows.isEmpty() ->",
         "uiState.error != null && uiState.catalogRows.isEmpty() && !hasCollectionContent && !hasHeroContent ->",
         count=2)
    once(home, "    val showStartupLoader = when {", """    val petalixFallback = homeStableGateReleased && (!hasAnyContent || !modernPresentationReady)
    val showStartupLoader = if (petalixFallback) false else when {""")
    once(home, "    Box(\n        modifier = Modifier.fillMaxSize()\n    ) {\n        when {", """    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // A direct My Locals entry is available even when the full Home row loaded.
        if (homeStableGateReleased && !petalixFallback) {
            val petalixContext = androidx.compose.ui.platform.LocalContext.current
            androidx.tv.material3.Button(
                onClick = {
                    petalixContext.startActivity(android.content.Intent(petalixContext, com.nuvio.tv.petalix.PetalixLocalsActivity::class.java))
                },
                modifier = Modifier.align(Alignment.TopEnd).zIndex(5f).padding(top = 20.dp, end = 28.dp)
            ) { androidx.tv.material3.Text("My Locals") }
        }
        when {""")
    once(home, "        when {\n            !uiState.layoutPreferencesReady -> {", """        when {
            petalixFallback -> com.nuvio.tv.petalix.PetalixHomeServicesFallback(
                onLiveTv = onPetalixLiveTv,
                onSettings = onPetalixSettings,
                onFolder = onNavigateToFolderDetailStable
            )
            !uiState.layoutPreferencesReady -> {""")

    once(modern,
         "    if (hasCollections && !hasCatalogs && uiState.installedAddonsCount > 0 && uiState.isLoading) {",
         """    val hasPetalixServices = uiState.homeRows.any {
        it is HomeRow.CollectionRow &&
            it.collection.id == com.nuvio.tv.petalix.PETALIX_SERVICES_COLLECTION &&
            it.collection.folders.isNotEmpty()
    }
    if (hasCollections && !hasPetalixServices && !hasCatalogs && uiState.installedAddonsCount > 0 && uiState.isLoading) {""")

    # Preserve every native row and its own relative order while putting services first.
    once(presentation,
         "    val lookups = buildCarouselRowLookups(rows)",
         "    val orderedRows = com.nuvio.tv.petalix.petalixServicesFirst(rows) { it.key }\n    val lookups = buildCarouselRowLookups(orderedRows)")
    once(presentation, "        rows = rows.asStable(),", "        rows = orderedRows.asStable(),")

    # The eight recovered logos are available before the network or video renderer is ready.
    # Keep native artwork for other folders and put native GIF/video layers above the logo.
    modern_rows = source / "ui/screens/home/ModernHomeRows.kt"
    once(modern_rows, """    val imageUrl = when {
        payload == null -> baseImageUrl
        !payload.focusGifEnabled -> baseImageUrl
        else -> baseImageUrl
    }
""", """    val petalixLogoUri = payload?.folderId?.let { com.nuvio.tv.petalix.petalixServiceLogoUri(it) }
    val imageUrl = petalixLogoUri ?: baseImageUrl
""")
    once(modern_rows,
         "                    val isPlaceholderItem = item.imageUrl.isPlaceholder()",
         "                    val isPlaceholderItem = petalixLogoUri == null && item.imageUrl.isPlaceholder()")
    once(modern_rows, """                            contentDescription = item.title,
                            modifier = Modifier.fillMaxSize(),
                            placeholder = backgroundPainter,
""", """                            contentDescription = item.title,
                            modifier = Modifier.fillMaxSize().then(
                                if (petalixLogoUri != null) Modifier.padding(16.dp) else Modifier
                            ),
                            placeholder = backgroundPainter,
""")
    once(modern_rows, """                            fallback = backgroundPainter,
                            contentScale = imageContentScale,
""", """                            fallback = backgroundPainter,
                            contentScale = if (petalixLogoUri != null) ContentScale.Fit else imageContentScale,
""")

    # The recovered service media renderer stays transparent until its first real frame.
    once(modern_rows, "                    if (shouldPlayTrailerInCard) {", """                    if (payload != null) {
                        com.nuvio.tv.petalix.PetalixServiceMediaOverlay(
                            folderId = payload.folderId,
                            focused = isFocused && payload.focusGifEnabled,
                            modifier = Modifier.matchParentSize()
                        )
                    }

                    if (shouldPlayTrailerInCard) {""")
    for relative in ("ui/components/CollectionRowSection.kt", "ui/screens/home/GridHomeContent.kt"):
        path = source / relative
        once(path,
             "            val activeImageUrl = collectionFolderCardImageUrl(folder, isFocused)",
             """            val petalixLogoUri = com.nuvio.tv.petalix.petalixServiceLogoUri(folder.id)
            val activeImageUrl = petalixLogoUri ?: collectionFolderCardImageUrl(folder, isFocused)""")
        if relative.endswith("CollectionRowSection.kt"):
            once(path, """                    contentDescription = if (folder.hideTitle) folder.title else null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape),
                    contentScale = ContentScale.FillBounds
""", """                    contentDescription = if (folder.hideTitle) folder.title else null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .then(if (petalixLogoUri != null) Modifier.padding(16.dp) else Modifier),
                    contentScale = if (petalixLogoUri != null) ContentScale.Fit else ContentScale.FillBounds
""")
        else:
            once(path, """                    model = activeImageUrl,
                    contentDescription = folder.title,
                    modifier = Modifier.fillMaxSize().clip(cardShape),
                    contentScale = ContentScale.FillBounds
""", """                    model = activeImageUrl,
                    contentDescription = folder.title,
                    modifier = Modifier.fillMaxSize().clip(cardShape)
                        .then(if (petalixLogoUri != null) Modifier.padding(16.dp) else Modifier),
                    contentScale = if (petalixLogoUri != null) ContentScale.Fit else ContentScale.FillBounds
""")
        once(path, "            if (!folder.hideTitle) {", """            com.nuvio.tv.petalix.PetalixServiceMediaOverlay(
                folderId = folder.id,
                focused = isFocused && folder.focusGifEnabled,
                modifier = Modifier.matchParentSize()
            )

            if (!folder.hideTitle) {""")

    # Fail at preparation time if an old generic Services screen remains wired into navigation.
    for path in (source / "MainActivity.kt", nav, home):
        text = path.read_text()
        for forbidden in ("PETALIX_SERVICES_ROUTE", "PetalixServicesScreen", "onPetalixServices", "PetalixEmptyHome"):
            if forbidden in text:
                raise RuntimeError(f"Separate generic Services navigation remains in {path.name}: {forbidden}")
    if nav.read_text().count("onPetalixLiveTv =") != 1 or nav.read_text().count("onPetalixSettings =") != 1:
        raise RuntimeError("Native Home navigation callbacks are incomplete")
    for path in (modern_rows, source / "ui/components/CollectionRowSection.kt", source / "ui/screens/home/GridHomeContent.kt"):
        if path.read_text().count("PetalixServiceMediaOverlay(") != 1:
            raise RuntimeError(f"Service tile media binding is incomplete in {path.name}")
        if path.read_text().count("petalixServiceLogoUri(") != 1:
            raise RuntimeError(f"Service tile offline logo binding is incomplete in {path.name}")
    return {"homeServices": "native-horizontal-row", "separateServicesNavigation": False, "nativeRowsPreserved": True, "serviceMediaBindings": 3, "serviceLogoBindings": 3}


if __name__ == "__main__":
    import argparse
    import json
    parser = argparse.ArgumentParser()
    parser.add_argument("upstream", type=Path)
    args = parser.parse_args()
    print(json.dumps(apply(args.upstream)))
