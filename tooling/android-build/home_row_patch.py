"""Put the single service collection before native rows in Nuvio Modern Home."""
import pathlib,shutil

def replace(text,old,new):
    if text.count(old)!=1:raise RuntimeError('Native Home row binding needs review: '+old[:90])
    return text.replace(old,new,1)

def apply(work):
    root=work/'app/src/main/java/com/nuvio/tv'
    presentation=root/'ui/screens/home/ModernHomePresentation.kt'
    text=presentation.read_text()
    text=replace(text,'    val lookups = buildCarouselRowLookups(rows)','    val orderedRows = com.nuvio.tv.petalix.streamingServicesFirst(rows) { it.key }\n    val lookups = buildCarouselRowLookups(orderedRows)')
    text=replace(text,'        rows = rows.asStable(),','        rows = orderedRows.asStable(),')
    presentation.write_text(text)
    rows=root/'ui/screens/home/ModernHomeRowsList.kt';text=rows.read_text()
    text=replace(text,'liveTvHere && (liveTvRowAbove == true || !hasContinueWatching)','com.nuvio.tv.petalix.liveTvBeforeRow(liveTvHere, liveTvRowAbove, hasContinueWatching, row.key)')
    text=replace(text,'liveTvHere && liveTvRowAbove == false && hasContinueWatching','com.nuvio.tv.petalix.liveTvAfterRow(liveTvHere, liveTvRowAbove, hasContinueWatching, row.key)')
    text=replace(text,'liveTvRowAbove == true && com.nuvio.tv.livetv.home.LiveTvHomeReveal.available.value','com.nuvio.tv.petalix.liveTvAboveFirstRow(liveTvRowAbove, com.nuvio.tv.livetv.home.LiveTvHomeReveal.available.value, firstRowKey)')
    rows.write_text(text)
    coordinator=root/'petalix/ManagedCoordinator.kt';text=coordinator.read_text()
    text=replace(text,'"kaptain-manifest-v1"','"kaptain-manifest-v1","home-services-first-v1"')
    coordinator.write_text(text)
    local=pathlib.Path(__file__).parent
    target=root/'petalix';target.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(local/'HomeRowOrder.kt',target/'HomeRowOrder.kt')
    tests=work/'app/src/test/java/com/nuvio/tv/petalix';tests.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(local/'HomeRowOrderTest.kt',tests/'HomeRowOrderTest.kt')
