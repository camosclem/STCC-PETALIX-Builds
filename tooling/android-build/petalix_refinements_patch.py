"""Home focus media, post-activation locals, and translucent vertical guide."""
from pathlib import Path

def change(s,a,b,label):
    if s.count(a)!=1:raise RuntimeError('Petalix refinements anchor: '+label)
    return s.replace(a,b)

def apply(work):
    root=Path(work)/'app/src/main/java/com/nuvio/tv'
    ui=root/'petalix/ui2'
    p=ui/'ArvioServiceHub.kt';s=p.read_text()
    s=change(s,'val introActive=video!=null && !introFinished && !introSkipped && !search','val introActive=false','remove service-entry intro')
    p.write_text(s)
    p=ui/'PetalixServiceMotion.kt';s=p.read_text()
    s=change(s,'    val lift by animateFloatAsState','''    var playClip by remember(service.id) { mutableStateOf(false) }
    var clipEnded by remember(service.id) { mutableStateOf(false) }
    LaunchedEffect(focused) {
        playClip=false;clipEnded=false
        if(focused){kotlinx.coroutines.delay(350);playClip=true}
    }
    val lift by animateFloatAsState''','focus dwell')
    s=change(s,'if(focused && clip!=null)ArvioVideoHero(clip,Modifier.fillMaxSize(),muted=true,loop=true,onEnded={})','if(focused && playClip && !clipEnded && clip!=null)ArvioVideoHero(clip,Modifier.fillMaxSize(),muted=false,loop=false,onEnded={clipEnded=true})','audible home tile')
    # Every service retains visible motion, including those without licensed intro assets.
    s=change(s,'        val clip=ArvioServiceMedia.video(service.id)','''        if(focused)Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.radialGradient(
            listOf(service.accent.copy(alpha=.13f),androidx.compose.ui.graphics.Color.Transparent))))
        val clip=ArvioServiceMedia.video(service.id)''','fallback focus treatment')
    p.write_text(s)
    p=ui/'Petalix2Services.kt';s=p.read_text()
    s=change(s,'private val sources=service?.let { ServiceCatalogs.sources(context,it) }.orEmpty()',
        'private val sources=service?.let { selected -> if(selected.id in setOf("tubi","pluto"))selected.sources() else ServiceCatalogs.sources(context,selected).ifEmpty { selected.sources() } }.orEmpty()','nonempty service queries')
    s=change(s,'val results=coroutineScope { sources.map { source->async { resolver.resolve(source,page).first { it !is NetworkResult.Loading } } }.awaitAll() }',
        '''val results=kotlinx.coroutines.supervisorScope { sources.map { source->async {
                try { kotlinx.coroutines.withTimeout(25000) { resolver.resolve(source,page).first { it !is NetworkResult.Loading } } }
                catch(e:kotlinx.coroutines.TimeoutCancellationException){null}
                catch(e:CancellationException){throw e}
                catch(e:Exception){null}
            } }.awaitAll() }''','bounded service loading')
    s=s.replace('val errors=results.count { it is NetworkResult.Error }','val errors=results.count { it == null || it is NetworkResult.Error }')
    p.write_text(s)
    # This test offers only the eight service experiences with recovered intro assets.
    s=p.read_text()
    s='\n'.join(line for line in s.split('\n') if not line.lstrip().startswith(('PetalixService("tubi",','PetalixService("pluto",')))
    p.write_text(s)
    # Prompt once on the first activated Home, and again after a different successful PIN.
    p=root/'petalix/ActivationActivity.kt';s=p.read_text()
    s=change(s,'manager.activate(code);openApp()','manager.activate(code);getSharedPreferences("petalix_locals_onboarding",MODE_PRIVATE).edit().putBoolean("completed",false).apply();openApp()','PIN locals prompt')
    p.write_text(s)
    p=ui/'Petalix2Home.kt';s=p.read_text()
    s=change(s,'    val clock by live.now.collectAsStateWithLifecycle()', '''    val localContext=LocalContext.current
    val localsSetup=remember(localContext){localContext.getSharedPreferences("petalix_locals_onboarding",android.content.Context.MODE_PRIVATE)}
    var showLocalsSetup by rememberSaveable { mutableStateOf(!localsSetup.getBoolean("completed",false)) }
    fun finishLocalsSetup(){localsSetup.edit().putBoolean("completed",true).apply();showLocalsSetup=false}
    if(showLocalsSetup)PetalixLocalChannelsDialog(channels.allVisibleChannels,
        onClose={finishLocalsSetup()},onOpenGroup={group->live.selectGroup(group);finishLocalsSetup();onGuide()},onboarding=true)
    val clock by live.now.collectAsStateWithLifecycle()''','first-run locals dialog')
    p.write_text(s)
    p=ui/'PetalixLocalChannels.kt';s=p.read_text()
    s=change(s,'onOpenGroup:(String)->Unit={},viewModel:', 'onOpenGroup:(String)->Unit={},onboarding:Boolean=false,viewModel:','onboarding mode')
    s=change(s,'PetalixAction("Close",onClick=onClose)','PetalixAction(if(onboarding)"Skip for now" else "Close",onClick=onClose)','skip locals')
    s=change(s,'            Text("Nearby station candidates', '''            if(onboarding)Text("Welcome! Enter your ZIP to find channels near you. You can change this later in Live TV.",color=Color.White,fontSize=14.sp)
            Text("Nearby station candidates''','welcome ZIP')
    p.write_text(s)
    p=root/'livetv/ui/LiveTvGuideScreen.kt';s=p.read_text()
    # Remove horizontal categories and restore the existing TV remote category rail.
    a=s.index('            androidx.compose.foundation.lazy.LazyRow(',s.index('// ---------------------------------------------------------------- header'))
    b=s.index('            // ---------------------------------------------------------------- groups + guide',a)
    s=s[:a]+s[b:]
    s=change(s,'targetValue = if (groupsOpen) 190.dp * guideScale(settings).coerceAtLeast(1f) else 0.dp,','targetValue = 176.dp,','persistent category rail')
    s=change(s,'if (false) androidx.compose.runtime.CompositionLocalProvider','androidx.compose.runtime.CompositionLocalProvider','restore category controls')
    # Header aligns with the grid; the category rail occupies the left edge.
    s=change(s,'            GuideHeader(\n','            Box(Modifier.padding(start=176.dp)) { GuideHeader(\n','header inset')
    s=change(s,'status = status.message.takeIf { status.loading }\n            )','status = status.message.takeIf { status.loading }\n            ) }','header inset close')
    # Keep the single existing live player; selected program artwork forms the guide backdrop.
    s=s.replace('.background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF08182A),Color(0xFF030910))))','.background(Color(0xFF08101D))')
    s=change(s,'        // A soft glow in the theme color', '''        coil3.compose.AsyncImage(model=headerPoster ?: headerBlock?.program?.icon,contentDescription=null,
            modifier=Modifier.fillMaxSize(),contentScale=androidx.compose.ui.layout.ContentScale.Crop,alpha=.50f)
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(
            listOf(Color(0x99101D32),Color(0xC0081020)))))
        // A soft glow in the theme color''','translucent backdrop')
    s=s.replace('Color(0xFF17447B) else Color(0xFF111E30)','Color(0xCC426C97) else Color(0x9934425C)')
    s=s.replace('val idleFill = Color(0xFF111E30)','val idleFill = Color(0x6634425C)')
    # Round, inset logo badges preserve the entire channel wordmark.
    s=change(s,'                ChannelLogo(channel.logo, (rowHeight - 12.dp).coerceAtLeast(18.dp))','''                Box(Modifier.size((rowHeight-8.dp).coerceAtLeast(26.dp))
                    .clip(androidx.compose.foundation.shape.CircleShape).background(Color.White.copy(alpha=.12f))
                    .border(1.dp,Color.White.copy(alpha=.22f),androidx.compose.foundation.shape.CircleShape),contentAlignment=Alignment.Center) {
                    ChannelLogo(channel.logo,(rowHeight-20.dp).coerceAtLeast(18.dp))
                }''','round channel logos')
    # Place the details/preview inside the right column so categories span the guide height.
    a=s.index('            Box(Modifier.padding(start=176.dp)) { GuideHeader(')
    b=s.index('            // ---------------------------------------------------------------- groups + guide',a)
    header=s[a:b].replace('Box(Modifier.padding(start=176.dp)) { GuideHeader(', 'GuideHeader(').replace('            ) }','            )')
    s=s[:a]+s[b:]
    anchor='                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {'
    s=change(s,anchor,anchor+'\n'+header,'header beside categories')
    s=s.replace('tileHeight = guideRowHeight(settings) - 4.dp,','tileHeight = 44.dp,')
    # Transparent row cells and category pills retain existing focus, menus and parental locks.
    a=s.index('private fun ChannelRow(') if 'private fun ChannelRow(' in s else s.index('val cellShape = RoundedCornerShape(liveCorner())')
    b=s.index('\n@Composable',a)
    part=s[a:b].replace('else liveCellColors(focused = nameFocused, idle = guideSurface())','else liveCellColors(focused = nameFocused, idle = Color.Transparent)')
    s=s[:a]+part+s[b:]
    a=s.index('private fun GroupItem(');b=s.index('\n@Composable',a)
    part=s[a:b].replace('val shape = RoundedCornerShape(liveCorner())','val shape = RoundedCornerShape(18.dp)')
    part=part.replace('.background(colors.background)','.background(if(focused)Color(0xFFE3EEFF) else if(selected)Color(0x665D7DA5) else Color.Transparent)')
    part=part.replace('color = colors.text','color = if(focused)Color(0xFF10203A) else Color.White')
    s=s[:a]+part+s[b:]
    p.write_text(s)
    p=ui/'PetalixGuideHeader.kt';s=p.read_text().replace('Color(0xFF0A192B)','Color(0x442C405D)').replace('Color(0xFF091727),Color(0xCC091727)','Color(0x55101D32),Color(0x33101D32)')
    p.write_text(s)

    p=Path(work)/'app/src/test/java/com/nuvio/tv/petalix/Petalix2CatalogTest.kt'
    s=p.read_text()
    s=change(s,'assertEquals(10,PetalixServices.all.size)','assertEquals(setOf("netflix","prime","disney","hulu","max","paramount","peacock","apple"),PetalixServices.all.map { it.id }.toSet())','approved eight services test')
    p.write_text(s)
