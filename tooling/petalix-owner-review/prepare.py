"""Prepare the owner review release without changing any panel configuration."""

from pathlib import Path

from PIL import Image, ImageDraw, ImageOps


def replace(path, old, new, count=1):
    text = path.read_text()
    if text.count(old) != count:
        raise ValueError(f"Unexpected source in {path.name}: {old[:72]}")
    path.write_text(text.replace(old, new))


SERVICE_ART = {
    "folder-WFGY1XPD": ("#170508", "#d91431", "diagonal", (.62, .36)),
    "folder-AT8KYHZO": ("#051929", "#0093cc", "sweep", (.70, .45)),
    "folder-8ZBZOMW9": ("#080e31", "#737bff", "orbit", (.50, .28)),
    "folder-LN9SPMQN": ("#071d16", "#21dc8a", "stack", (.66, .58)),
    "folder-OA4T8NYO": ("#130b29", "#9a70f5", "spotlight", (.64, .40)),
    "folder-IQTF27K8": ("#11171f", "#b0c9dc", "frame", (.57, .42)),
    "folder-9YQK1ZF1": ("#171018", "#e9715b", "ribbon", (.65, .48)),
    "folder-ILJM8PD0": ("#0b1d4d", "#79bfff", "ridge", (.57, .32)),
    "folder-petalix-britbox": ("#25121c", "#e8ad8c", "arch", (.54, .40)),
}


# Crop one part of each service's existing wide artwork so the featured
# composition is not another full-screen poster wall.
FEATURE_CROP = {
    "folder-WFGY1XPD": (.62, .20, .84, .95),
    "folder-AT8KYHZO": (.76, .09, 1.0, .81),
    "folder-8ZBZOMW9": (.69, .02, .97, .73),
    "folder-LN9SPMQN": (.62, .05, .98, .87),
    "folder-OA4T8NYO": (.75, .19, 1.0, .92),
    "folder-IQTF27K8": (.75, .08, 1.0, .85),
    "folder-9YQK1ZF1": (.64, .15, .96, .94),
    "folder-ILJM8PD0": (.63, .12, .96, .92),
    "folder-petalix-britbox": (.43, .02, .98, .41),
}


def make_artwork(assets):
    destination = assets / "service-art-review"
    destination.mkdir(exist_ok=True)
    for folder, (base, accent, shape, center) in SERVICE_ART.items():
        source = assets / "service-art-v1" / f"{folder}.jpg"
        if folder == "folder-petalix-britbox":
            source = assets / "home-art/britbox-v2.png"
        with Image.open(source) as picture:
            original = picture.convert("RGB")
        crop = FEATURE_CROP[folder]
        feature = original.crop(tuple(round(amount * original.size[index % 2]) for index, amount in enumerate(crop)))
        for label, size in [("hero", (1920, 1080)), ("card", (768, 300))]:
            width, height = size
            x = lambda fraction: round(width * fraction)
            y = lambda fraction: round(height * fraction)
            photo = ImageOps.fit(feature, size, centering=center)
            diffuse = ImageOps.fit(original, size, centering=center)
            canvas = Image.blend(diffuse, Image.new("RGB", size, base), .89 if label == "hero" else .82)
            mask = Image.new("L", size)
            draw = ImageDraw.Draw(mask)
            if shape == "diagonal":
                draw.polygon([(x(.62), 0), (width, 0), (width, height), (x(.38), height)], fill=220)
            elif shape == "sweep":
                draw.ellipse((x(.36), y(-.80), x(1.40), y(1.65)), fill=220)
            elif shape == "orbit":
                draw.ellipse((x(.44), y(-.35), x(1.10), y(1.06)), fill=205)
            elif shape == "stack":
                for left, top in [(.54, .05), (.76, .22), (.62, .53)]:
                    draw.rounded_rectangle((x(left), y(top), x(left+.26), y(top+.46)), y(.045), fill=210)
            elif shape == "spotlight":
                draw.polygon([(x(.81), 0), (width, 0), (width, height), (x(.44), height)], fill=224)
            elif shape == "frame":
                draw.rounded_rectangle((x(.47), y(.09), x(.97), y(.91)), y(.035), fill=190)
            elif shape == "ribbon":
                draw.polygon([(x(.42), y(.15)), (width, 0), (width, y(.81)), (x(.55), height)], fill=214)
            elif shape == "ridge":
                draw.polygon([(x(.42), height), (x(.69), y(.13)), (x(.77), y(.36)), (x(.88), y(.09)), (width, y(.38)), (width, height)], fill=209)
            elif shape == "arch":
                draw.rounded_rectangle((x(.49), y(.07), x(.94), y(1.47)), y(.39), fill=186)
            photo = Image.blend(photo, Image.new("RGB", size, accent), .12)
            Image.composite(photo, canvas, mask).save(destination / f"{folder}-{label}.jpg", quality=89, optimize=True)


def prepare(root: Path):
    java = root / "app/src/main/java/com/nuvio/tv"

    # A Home row should contain channels. The Live TV tab already opens the guide.
    home = java / "livetv/home/LiveTvHomeRow.kt"
    text = home.read_text()
    start = text.index('                if (persistent) item(key = "petalix-open-guide")')
    end = text.index("                itemsIndexed(entries", start)
    home.write_text(text[:start] + text[end:])
    replace(home, "if (!persistent && index == 0)", "if (index == 0)")
    replace(home, "val position = if (persistent) index + 1 else index", "val position = index")

    # A cold guide can become ready with zero channels before its cache has loaded.
    guide = java / "livetv/ui/LiveTvGuideScreen.kt"
    replace(guide, """        if (!returningToGuide && !homeCategoryRequested && !viewModel.settings.value.rememberLastGroup) {
            val loaded = viewModel.uiState.first { it.guideReady }
            if (!fullscreenComing && !fullscreen && loaded.allVisibleChannels.isNotEmpty()) viewModel.selectGroup(loaded.defaultGroupId)
        }
        viewModel.uiState.first { it.guideReady }""", """        if (!returningToGuide && !homeCategoryRequested) {
            val loaded = viewModel.uiState.first { it.guideReady && it.allVisibleChannels.isNotEmpty() }
            val rememberGroup = viewModel.settings.value.rememberLastGroup
            if (!fullscreenComing && !fullscreen &&
                (!rememberGroup || loaded.channels.isEmpty())) {
                viewModel.selectGroup(loaded.defaultGroupId)
            }
        } else {
            viewModel.uiState.first { it.guideReady }
        }""")

    model = java / "livetv/ui/LiveTvViewModel.kt"
    replace(model, """        s.showAllChannelsGroup -> ChannelGroup.ALL
        regularForGuide.any { !it.locked && it.count > 0 } ->
            regularForGuide.first { !it.locked && it.count > 0 }.id""",
            """        regularForGuide.any { !it.locked && it.count > 0 } ->
            regularForGuide.first { !it.locked && it.count > 0 }.id
        s.showAllChannelsGroup -> ChannelGroup.ALL""")

    groups = java / "petalix/PetalixGuideGroupIndex.kt"
    replace(groups, """        }
    fun canonicalId""", """        }.filterNot { !it.special && categoryTitle(it.title) == "motorsports" && it.count <= 1 }
    fun canonicalId""")

    labels = java / "petalix/ViewingLabels.kt"
    replace(labels, """    fun category(raw: String): String {""", """    fun guideCategory(raw: String): String =
        category(raw).replace(Regex(""" + '"""^[^\\p{L}\\p{N}]+"""' + """), "").trimStart()

    fun category(raw: String): String {""")
    categories = java / "livetv/ui/PetalixGlassCategories.kt"
    replace(categories, "ViewingLabels.category(e.title)", "ViewingLabels.guideCategory(e.title)")
    replace(categories, 'if (e.group?.locked == true) "🔒 " else ""', 'if (e.group?.locked == true) "Locked · " else ""')

    # A newly started automatic buffer sits a few seconds from the live edge.
    # Expose its seek panel only after the viewer pauses or moves behind live.
    player = java / "livetv/ui/LiveTvPlayerScreen.kt"
    replace(player, "shiftStartedAt != null || (ownTimeline && viewModel.playback.shiftIsBehind())",
            "(shiftStartedAt != null || ownTimeline) && viewModel.playback.shiftIsBehind()")
    replace(player, "                    shiftStartedAt != null -> PanelMode.SHIFT",
            "                    shiftStartedAt != null && viewModel.playback.shiftIsBehind() -> PanelMode.SHIFT")
    playback = java / "livetv/player/LiveTvPlaybackController.kt"
    replace(playback, "    var rewindAlways: Boolean = false", """    var rewindAlways: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (!value && alwaysBuffer) scope.launch {
                if (!rewindAlways && alwaysBuffer) goLiveFresh()
            }
        }""")

    make_artwork(root / "app/src/main/assets/petalix")
    artwork = java / "petalix/PetalixServiceArtwork.kt"
    text = artwork.read_text()
    start = text.index("private val artworkByService")
    end = text.index("internal fun petalixFullBackdrop", start)
    keys = ",\n    ".join(f'"{key}"' for key in SERVICE_ART)
    artwork.write_text(text[:start] + f"""private val serviceArtIds = setOf(
    {keys}
)

internal fun petalixServiceArtworkUri(folderId: String): String? =
    folderId.takeIf(serviceArtIds::contains)?.let {{
        "file:///android_asset/petalix/service-art-review/$it-card.jpg"
    }}

""" + text[end:])
    replace(artwork, """    configuredArtwork[folderId] ?: when {
        folderId == "folder-petalix-britbox" -> britboxArtwork
        serviceBackdropUrl(folderId) != null -> "file:///android_asset/petalix/service-art-v1/$folderId.jpg"
        else -> null
    }""", """    configuredArtwork[folderId] ?: folderId.takeIf(serviceArtIds::contains)?.let {
        "file:///android_asset/petalix/service-art-review/$it-hero.jpg"
    }""")
    rows = java / "ui/screens/home/ModernHomeRows.kt"
    replace(rows, ' && payload?.folderId in setOf("folder-OA4T8NYO", "folder-IQTF27K8", "folder-9YQK1ZF1", "folder-ILJM8PD0")', "")

    modern = java / "ui/screens/home/ModernHomeContent.kt"
    replace(modern, """                            configuredServiceArtwork[folder] ?: featureArt[folder] as? String
                                ?: com.nuvio.tv.petalix.petalixServiceHeroArtwork(folder, emptyMap())""",
            """                            com.nuvio.tv.petalix.petalixServiceHeroArtwork(folder, configuredServiceArtwork)
                                ?: featureArt[folder] as? String""")

    tests = root / "app/src/test/java/com/nuvio/tv/petalix"
    artwork_tests = tests / "PetalixArtworkTest.kt"
    replace(artwork_tests, "petalix/service-art-v1/$id.jpg", "petalix/service-art-review/$id-hero.jpg")
    replace(artwork_tests, "petalix/home-art/netflix.webp", "petalix/service-art-review/$id-card.jpg")
    group_tests = tests / "PetalixGuideGroupIndexTest.kt"
    replace(group_tests, "class PetalixGuideGroupIndexTest {", """class PetalixGuideGroupIndexTest {
    @Test
    fun singleChannelMotorsportsDoesNotCreateAGuideCategory() {
        val solo = ChannelGroup("single-race", "Motorsports", 1)
        val pool = ChannelGroup("multiple-races", "Motorsports", 2)
        assertEquals(emptyList<ChannelGroup>(), PetalixGuideGroupIndex(listOf(solo)) { emptyList() }.groups)
        assertEquals(listOf(pool), PetalixGuideGroupIndex(listOf(pool)) { emptyList() }.groups)
    }

    @Test
    fun guideKeepsFriendlyLabelsWithoutEmoji() {
        assertEquals("Sports Networks", ViewingLabels.guideCategory("Sports"))
        assertEquals("24/7 Drama", ViewingLabels.guideCategory("24/7 Drama"))
    }
""")
