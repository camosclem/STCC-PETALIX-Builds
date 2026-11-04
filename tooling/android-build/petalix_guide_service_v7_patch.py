"""Distinct service landings and a compact Petalix live guide."""
from pathlib import Path


def once(source, before, after, label):
    if source.count(before) != 1:
        raise RuntimeError("Petalix v7 anchor changed: " + label)
    return source.replace(before, after)


def apply(work):
    root = Path(work) / "app/src/main/java/com/nuvio/tv"
    ui = root / "petalix/ui2"
    p = ui / "Petalix2Services.kt"
    s = p.read_text()
    p.write_text(once(s, "    ArvioPetalixServiceScreen(onHome,onDetail,viewModel)",
        "    DistinctPetalixServiceScreen(onHome,onDetail,viewModel)", "service route"))

    p = ui / "Petalix2Home.kt"
    s = p.read_text()
    s = once(s, "RoundedCornerShape(8.dp)", "RoundedCornerShape(14.dp)", "home tile clip") if s.count("RoundedCornerShape(8.dp)") == 1 else s.replace("RoundedCornerShape(8.dp)", "RoundedCornerShape(14.dp)")
    p.write_text(s)

    p = ui / "PetalixServiceLayouts.kt"
    s = p.read_text().replace("RoundedCornerShape(6.dp)", "RoundedCornerShape(14.dp)")
    s = s.replace("RoundedCornerShape(5.dp)", "RoundedCornerShape(14.dp)")
    s = s.replace("RoundedCornerShape(8.dp)", "RoundedCornerShape(14.dp)")
    p.write_text(s)

    p = ui / "PetalixGuideHeader.kt"
    s = p.read_text()
    s = once(s, ".height(170.dp)", ".height(138.dp)", "compact guide header")
    a = s.index("        Box(Modifier.weight(1f).fillMaxHeight()")
    b = s.index("        if(showPreview)Box(", a)
    c = s.index("\n    }\n    DisposableEffect(Unit)", b)
    details = s[a:b]
    preview = s[b:c].replace("if(showPreview)Box", "if(showPreview)Box")
    details = details.replace('PetalixAction("Guide options",modifier=Modifier.focusRequester(gearFocus),onClick=onGear)',
        'PetalixAction("⋯",modifier=Modifier.focusRequester(gearFocus),onClick=onGear)')
    details = details.replace("fontSize=26.sp", "fontSize=22.sp").replace("maxLines=2,overflow=TextOverflow.Ellipsis", "maxLines=1,overflow=TextOverflow.Ellipsis")
    details = details.replace("verticalArrangement=Arrangement.spacedBy(8.dp)", "verticalArrangement=Arrangement.spacedBy(5.dp)")
    details = details.replace(".padding(16.dp)", ".padding(12.dp)")
    preview = preview.replace(".aspectRatio(16f/9f)", ".width(228.dp)")
    s = s[:a] + preview + "\n" + details + s[c:]
    p.write_text(s)

    p = root / "livetv/ui/LiveTvGuideScreen.kt"
    s = p.read_text()
    a = s.index('            Row(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically,')
    b = s.index('            // ---------------------------------------------------------------- header', a)
    s = s[:a] + s[b:]
    s = once(s, 'listOf(Color(0x99101D32),Color(0xC0081020))',
        'listOf(Color(0x55101D32),Color(0x77081020))', 'show artwork through the guide')
    s = once(s, 'onGroupMenu = { g -> if (!g.special) groupMenu = g },',
        'onGroupMenu = { g -> if(g.title.equals("My Locals",ignoreCase=true)) petalixLocalsOpen=true else if (!g.special) groupMenu = g },', 'locals group edit')
    s = once(s, '                        } else if (g.id == ChannelGroup.SEARCH) {',
        '                        } else if (g.title.equals("My Locals",ignoreCase=true) && g.count==0) {\n                            petalixLocalsOpen=true\n                        } else if (g.id == ChannelGroup.SEARCH) {', 'empty locals editing')
    s = once(s, 'val channelColWidth = channelColumnWidth(settings)', 'val channelColWidth = 102.dp', 'logo column width')
    a = s.index('private fun GuideRow(')
    b = s.index('\n@Composable', a)
    part = s[a:b]
    part = part.replace('val cellShape = RoundedCornerShape(liveCorner())', 'val cellShape = RoundedCornerShape(13.dp)')
    part = part.replace('if (settings.showChannelNumbers) {', 'if (false) {', 1)
    part = part.replace('if (settings.showChannelLogos) {', 'if (settings.showChannelLogos && !channel.logo.isNullOrBlank()) {', 1)
    part = part.replace('(rowHeight-8.dp).coerceAtLeast(26.dp)', '(rowHeight-3.dp).coerceAtLeast(38.dp)', 1)
    part = part.replace('(rowHeight-20.dp).coerceAtLeast(18.dp)', '(rowHeight-12.dp).coerceAtLeast(28.dp)', 1)
    part = part.replace('if (settings.showChannelNames) {', 'if (channel.logo.isNullOrBlank()) {', 1)
    part = part.replace('if (quality != null && !reorderMode', 'if (false && quality != null && !reorderMode', 1)
    part = part.replace('if (channel.catchup != null && !reorderMode', 'if (false && channel.catchup != null && !reorderMode', 1)
    s = s[:a] + part + s[b:]
    a = s.index('private fun ProgramBlock(')
    b = s.index('\n@Composable', a)
    part = s[a:b].replace('val shape = RoundedCornerShape(liveCorner())', 'val shape = RoundedCornerShape(13.dp)')
    part = part.replace('if(focused)Color(0xCC426C97) else Color(0x9934425C)',
        'if(focused)Color(0xB04A81BC) else Color(0x663A5676)')
    s = s[:a] + part + s[b:]
    a = s.index('private fun GroupItem(')
    b = s.index('\n@Composable', a)
    part = s[a:b].replace('        Spacer(Modifier.width(12.dp))\n        LiveText(\n            group.title,',
        '        Spacer(Modifier.width(12.dp))\n        if(group.title.equals("My Locals",ignoreCase=true)) {\n            LiveText("⌖",color=if(focused)Color(0xFF10203A) else Color(0xFF8EBEFF),size=gsp(18f))\n            Spacer(Modifier.width(6.dp))\n        }\n        LiveText(\n            group.title,')
    s = s[:a] + part + s[b:]
    p.write_text(s)

    p = root / "livetv/data/LiveTvPreferences.kt"
    s = p.read_text()
    s = once(s, '    suspend fun petalixSaveLocals(zip:String,keys:List<String>):String {',
        '    suspend fun petalixSaveLocals(zip:String,keys:List<String>,merge:Boolean=false):String {', 'local save mode')
    s = once(s, '((existing?.channelKeys ?: emptyList())+keys).distinct()',
        'if(merge && p[zipKey]==zip)((existing?.channelKeys ?: emptyList())+keys).distinct() else keys.distinct()', 'replace after ZIP change, merge automatic matches')
    p.write_text(s)

    p = ui / "PetalixLocalChannels.kt"
    s = p.read_text()
    s = once(s, 'fun save(zip:String,keys:List<String>) {',
        'fun save(zip:String,keys:List<String>,merge:Boolean=false) {', 'save selection mode')
    s = once(s, 'prefs.petalixSaveLocals(zip,keys)',
        'prefs.petalixSaveLocals(zip,keys,merge)', 'store selection mode')
    s = once(s, 'if(keys.isEmpty() || _state.value.saving)return',
        'if(_state.value.saving)return', 'allow empty locals category')
    s = once(s, '    val matches=remember(channels,state.stations){LocalStationMatcher.matches(channels,state.stations)}',
        '''    LaunchedEffect(state.zip,channels.size) {
        if(state.zip.matches(Regex("[0-9]{5}")) && channels.isNotEmpty() && state.location.isBlank() && !state.loading)
            viewModel.lookup(state.zip)
    }
    val matches=remember(channels,state.stations){LocalStationMatcher.matches(channels,state.stations)}''', 'saved ZIP reload after playlist load')
    s = once(s, '    val choices=if(showAll)channels.sortedBy{it.name} else matches.map{it.channel}',
        '''    // Build the group as soon as ZIP results and a playlist are present. Rebuild when the
    // playlist finishes loading so an early activation prompt cannot strand My Locals empty.
    LaunchedEffect(state.location,channels.size) {
        if(state.location.isNotBlank() && state.zip.matches(Regex("[0-9]{5}"))) {
            selected=matches.map{it.channel.key}.toSet()
            viewModel.save(state.zip,matches.map{it.channel.key},merge=true)
        }
    }
    val choices=if(showAll)channels.sortedBy{it.name} else matches.map{it.channel}''', 'automatic local matches')
    s = s.replace('"Add ${selected.size} to My Locals"', '"Save ${selected.size} to My Locals"')
    p.write_text(s)
