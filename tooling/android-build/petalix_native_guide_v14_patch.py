"""Restore the verified Nuvio guide and remove Petalix's ZIP/local-channel UI."""
from pathlib import Path
import zipfile
import hashlib


GUIDE_FILES = (
    "livetv/ui/LiveTvGuideScreen.kt",
    "livetv/ui/GuideRowStrip.kt",
    "livetv/ui/LiveTvViewModel.kt",
)


def one(text, old, new, label):
    if text.count(old) != 1:
        raise RuntimeError("Native guide restore needs review: " + label)
    return text.replace(old, new)


def apply(work, archive):
    from home_baseline import verify_archive
    verify_archive(archive, "petalix")
    root = Path(work) / "app/src/main/java/com/nuvio/tv"
    hashes = {}
    with zipfile.ZipFile(archive) as source:
        for name in GUIDE_FILES:
            suffix = "/app/src/main/java/com/nuvio/tv/" + name
            found = [n for n in source.namelist() if n.endswith(suffix)]
            if len(found) != 1:
                raise RuntimeError("Verified native guide file missing: " + name)
            data = source.read(found[0])
            (root / name).write_bytes(data)
            hashes[name] = hashlib.sha256(data).hexdigest()

    # Keep Home's category buttons useful without changing the native guide's
    # rendering, channel list, key handlers, focus requesters, or Back behavior.
    p = root / GUIDE_FILES[0]
    s = p.read_text()
    if "import kotlinx.coroutines.flow.first\n" not in s:
        package, rest = s.split("\n", 1)
        s = package + "\n\nimport kotlinx.coroutines.flow.first\n" + rest
    s = one(s, "    val ui by viewModel.uiState.collectAsStateWithLifecycle()", '''    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val petalixCategory = remember { com.nuvio.tv.petalix.ui2.PetalixCategoryLaunch.consume() }
    LaunchedEffect(petalixCategory) {
        android.util.Log.i("PetalixGuide", "petalix-native-guide-v14")
        if (petalixCategory != null) {
            val loaded = kotlinx.coroutines.withTimeoutOrNull(10000L) {
                viewModel.uiState.first { it.allVisibleChannels.isNotEmpty() }
            }
            val terms = com.nuvio.tv.petalix.ui2.PetalixCategoryLaunch.terms[petalixCategory].orEmpty()
            loaded?.groups?.firstOrNull { g -> !g.special && terms.any { g.title.contains(it, true) } }
                ?.let { viewModel.selectGroup(it.id) }
        }
    }''', "Home category handoff")
    p.write_text(s)

    p = root / "petalix/ui2/Petalix2Home.kt"
    s = p.read_text()
    start = s.index("    val liveStatus by live.status.collectAsStateWithLifecycle()")
    end = s.index("    val clock by live.now.collectAsStateWithLifecycle()", start)
    s = s[:start] + s[end:]
    s = one(s, '"Kids","Local News","Lifestyle"', '"Kids","Lifestyle"', "remove Home locals tile")
    s = one(s, 'Text(if(name=="Local News")"My Locals" else name,', 'Text(name,', "remove locals label override")
    s = one(s, '"Local News" to listOf("local","locals","news"),', '', "remove ZIP category route")
    s = one(s, 'CategoryArtwork(i,Modifier.fillMaxSize())', 'CategoryArtwork(if(i>=4)i+1 else i,Modifier.fillMaxSize())', "preserve remaining category artwork")
    if any(term in s for term in ("PetalixLocalChannelsDialog", "showLocalsSetup", "My Locals")):
        raise RuntimeError("A locals entry point remains on Home")
    p.write_text(s)

    # Do not merge the retired per-device local selection into the native group
    # list. Retain stored data without presenting or changing the user's playlist.
    p = root / "livetv/data/LiveTvPreferences.kt"
    s = p.read_text()
    s = one(s, "            customGroups = petalixGroups(p0,id),", '''            customGroups = decodeCustomGroups(p[Keys.customGroups]).filterNot {
                it.id == com.nuvio.tv.livetv.model.ChannelGroup.CUSTOM_PREFIX + "petalix_locals"
            },''', "native custom groups without retired locals")
    s = one(s, "            petalixGroups(p,id).forEach { g -> if (g.id !in hiddenGroups) kept += g.channelKeys }",
        "            decodeCustomGroups(v[Keys.customGroups]).forEach { g -> if (g.id !in hiddenGroups) kept += g.channelKeys }", "native EPG visibility")
    p.write_text(s)

    for path in (root / "petalix/ui2/PetalixLocalChannels.kt", root / "petalix/ui2/PetalixGuideHeader.kt",
                 Path(work) / "app/src/main/assets/petalix2/local-stations.json"):
        path.unlink(missing_ok=True)
    tests = Path(work) / "app/src/test/java/com/nuvio/tv/petalix"
    for name in ("PetalixLocalStationMatcherTest.kt", "PetalixLocalSuggestionsTest.kt", "PetalixLocalMarketsTest.kt"):
        for path in tests.rglob(name):
            path.unlink()
    (tests / "PetalixLocalsPersistenceTest.kt").unlink(missing_ok=True)

    # Guard that the native list and row renderer stay byte-for-byte original.
    for name in GUIDE_FILES[1:]:
        if hashlib.sha256((root / name).read_bytes()).hexdigest() != hashes[name]:
            raise RuntimeError("Native guide source changed: " + name)
