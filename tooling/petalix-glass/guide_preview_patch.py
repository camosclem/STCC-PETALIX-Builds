"""Use exclusive preview assets, safe insets and guide-colored programme bars."""
from pathlib import Path
from shutil import copy2

HERE = Path(__file__).resolve().parent


def replace(path: Path, old: str, new: str):
    source = path.read_text()
    if source.count(old) != 1:
        raise ValueError("Guide source changed: " + path.name + ": " + old[:70])
    path.write_text(source.replace(old, new))


def apply(root: Path):
    java = root / "app/src/main/java/com/nuvio/tv"
    ui = java / "livetv/ui"
    artwork = ui / "PetalixGuideArtwork.kt"
    source = artwork.read_text()
    marker = "@Composable\ninternal fun PetalixGuideProgramArt("
    if source.count(marker) != 1:
        raise ValueError("Expected the original preview artwork renderer")
    artwork.write_text(source[:source.index(marker)].rstrip() + "\n")
    for name in ("PetalixGuideProgramArt.kt", "PetalixGuideBarColors.kt"):
        copy2(HERE / name, ui / name)
    copy2(HERE / "PetalixGuideBarColorsTest.kt", root / "app/src/test/java/com/nuvio/tv/petalix/PetalixGuideBarColorsTest.kt")

    surface = ui / "PetalixGlassSurface.kt"
    replace(surface, "private val cellShape = RoundedCornerShape(11.dp)",
            "private val cellShape = RoundedCornerShape(5.dp)")
    # Equal top, right and bottom safe insets for both the real video surface and artwork.
    replace(surface, "BoxWithConstraints(modifier.fillMaxWidth().clipToBounds()) {",
            "BoxWithConstraints(modifier.fillMaxWidth().padding(end = 24.dp, top = 12.dp, bottom = 12.dp).clipToBounds()) {")
    replace(surface, '''    val tile = GuidePanel.copy(alpha = when (settings.guideInfoBackground) {
        "solid" -> 0.96f
        "darker" -> 0.78f
        else -> 0.56f
    })''', '''    val tile = petalixGuideTileColor(settings.guideBackdrop, settings.guideInfoBackground)
    val focusedTile = petalixGuideTileColor(settings.guideBackdrop, settings.guideInfoBackground, focused = true)''')
    replace(surface, "selected -> Color(0xFF363638)", "selected -> focusedTile")

    components = ui / "LiveTvComponents.kt"
    replace(components, '''        return GuidePanel.copy(alpha = alpha)''',
            '''        return petalixGuideTileColor(LocalPetalixBackdrop.current, LocalGuideDarkness.current)''')
    replace(components, '''        return Color(0xFF303033).copy(alpha = alpha)''',
            '''        return petalixGuideTileColor(LocalPetalixBackdrop.current, LocalGuideDarkness.current, focused = true)''')
    replace(components, '''        LiveCellColors(Color(0xFF343437), GuideSilver, Color.White)
    else LiveCellColors(GuidePanel, GuideSilver, Color.White)''',
            '''        LiveCellColors(petalixGuideTileColor(LocalPetalixBackdrop.current, LocalGuideDarkness.current, focused = true), GuideSilver, Color.White)
    else LiveCellColors(petalixGuideTileColor(LocalPetalixBackdrop.current, LocalGuideDarkness.current), GuideSilver, Color.White)''')
    components.write_text(components.read_text() + '''
internal val LocalPetalixBackdrop = androidx.compose.runtime.staticCompositionLocalOf { "petalix" }

@Composable
internal fun petalixGuideTileColor(style: String, darkness: String, focused: Boolean = false): Color {
    val resolved = resolveBackdrop(style)
    return petalixGuideBarColor(backdropBase(resolved), accentOf(resolved).first, darkness, focused)
}
''')
    replace(ui / "LiveTvGuideScreen.kt", "LocalGuideDarkness provides settings.guideInfoBackground) {",
            "LocalGuideDarkness provides settings.guideInfoBackground, LocalPetalixBackdrop provides settings.guideBackdrop) {")
    # Match IMD's guard during the focus transfer into its category rail.
    replace(ui / "PetalixCableGuide.kt", '''    fun guideKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        val key = event.key''', '''    fun guideKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (categoriesOpen) return true
        val key = event.key''')
    replace(ui / "PetalixCableGuide.kt", "Key.DirectionDown -> controls.moveRow(1)",
            "Key.DirectionDown -> if (row < channels.lastIndex) controls.moveRow(1) else Unit")
    replace(ui / "LiveTvGuideScreen.kt", '''            val visible = info.visibleItemsInfo
            if (visible.isEmpty()) return@launch
            // The first row wholly on screen:''', '''            val visible = info.visibleItemsInfo
            if (visible.isEmpty()) return@launch
            if (!editingGuide) {
                // IMD keeps the focused channel one whole row below the top.
                listState.scrollToItem((target - 1).coerceAtLeast(0))
                return@launch
            }
            // The first row wholly on screen:''')
