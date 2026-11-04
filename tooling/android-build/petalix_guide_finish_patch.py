"""Finish the Petalix TV guide composition before requesting another APK."""
from pathlib import Path
import shutil

def one(s,a,b,label):
    if s.count(a)!=1:raise RuntimeError("Petalix guide finish anchor: "+label)
    return s.replace(a,b)

def apply(work):
    root=Path(work)/"app/src/main/java/com/nuvio/tv"
    ui=root/"petalix/ui2"
    asset=Path(work)/"app/src/main/assets/petalix2/service-logos/apple.svg"
    asset.parent.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(Path(__file__).with_name("service-logos")/"apple.svg",asset)
    p=ui/"ArvioServiceHub.kt";s=p.read_text()
    s=one(s,'private val logos=setOf("netflix","disney","prime","hulu","max","paramount","peacock")',
        'private val logos=setOf("netflix","disney","prime","hulu","max","paramount","apple","peacock")',
        "Apple TV vector logo")
    p.write_text(s)
    p=ui/"PetalixGuideHeader.kt";s=p.read_text()
    s=one(s,'.height(138.dp).padding(horizontal=24.dp,vertical=10.dp)',
        '.height(172.dp).padding(horizontal=18.dp,vertical=10.dp)',"guide feature height")
    s=one(s,'horizontalArrangement=Arrangement.spacedBy(22.dp)',
        'horizontalArrangement=Arrangement.spacedBy(14.dp)',"preview spacing")
    s=one(s,'if(showPreview)Box(Modifier.fillMaxHeight().width(228.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black)',
        'if(showPreview)Box(Modifier.fillMaxHeight().width(280.dp).clip(RoundedCornerShape(18.dp)).background(Color.Black)',
        "larger left live preview")
    s=one(s,'Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(Color(0x442C405D))) {',
        'Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(Color(0x66324A69))) {',
        "round glass details")
    s=one(s,'''            AsyncImage(poster ?: program?.icon,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop,alpha=.3f)
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0x55101D32),Color(0x33101D32),Color.Transparent))))''',
        '''            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
                listOf(Color(0xBB10243B),Color(0x663F5C7B),Color(0x44101D32)))))''',
        "no still in details")
    s=one(s,'Modifier.width(72.dp).height(30.dp)',
        'Modifier.width(100.dp).height(40.dp)',"header channel logo")
    s=one(s,'fontSize=22.sp,fontWeight=FontWeight.Bold,maxLines=1',
        'fontSize=24.sp,fontWeight=FontWeight.Bold,maxLines=1',"program title")
    p.write_text(s)
    p=root/"livetv/ui/LiveTvGuideScreen.kt";s=p.read_text()
    s=one(s,'targetValue = 176.dp,','targetValue = 198.dp,',"category rail")
    s=one(s,'tileHeight = 44.dp,','tileHeight = 52.dp,',"category tile height")
    s=one(s,'val channelColWidth = 102.dp','val channelColWidth = 128.dp',"channel mark column")
    s=one(s,'val rowHeight = guideRowHeight(settings)',
        'val rowHeight = guideRowHeight(settings).coerceAtLeast(58.dp)',"comfortable guide rows")
    s=one(s,'val shape = RoundedCornerShape(13.dp)\n    Box(\n        modifier = Modifier\n            .offset(x = x)',
        'val shape = RoundedCornerShape(18.dp)\n    Box(\n        modifier = Modifier\n            .offset(x = x)',"program corner")
    s=one(s,'.clip(shape).background(if(focused)Color(0xB04A81BC) else Color(0x663A5676)).border(if(focused)2.dp else 1.dp,if(focused)Color(0xFF4D9DFF) else Color(0xFF24344A),shape)',
        '.clip(shape).background(if(focused)Color(0xAA5B8CC1) else Color(0x77465F7D)).border(if(focused)2.dp else 1.dp,if(focused)Color(0xFF70B6FF) else Color(0x557F9AB9),shape)',
        "translucent EPG blocks")
    a=s.index('private fun ProgramBlock(');b=s.index('\n@Composable',a)
    part=s[a:b]
    start=part.index('            subtitle?.let {')
    end=part.index('\n        }\n        progress?.let',start)
    part=part[:start]+part[end:]
    s=s[:a]+part+s[b:]
    s=one(s,'val cellShape = RoundedCornerShape(13.dp)',
        'val cellShape = RoundedCornerShape(18.dp)',"channel corners")
    s=one(s,'Box(Modifier.size((rowHeight-3.dp).coerceAtLeast(38.dp))',
        'Box(Modifier.size((rowHeight-6.dp).coerceAtLeast(48.dp))',"round channel badges")
    s=one(s,'ChannelLogo(channel.logo,(rowHeight-12.dp).coerceAtLeast(28.dp))',
        'ChannelLogo(channel.logo,(rowHeight-15.dp).coerceAtLeast(40.dp))',"larger channel logos")
    s=one(s,'    FrameMeter("Guide")',
        '    LaunchedEffect(Unit) { android.util.Log.i("PetalixGuide","petalix-guide-finish-v10") }\n    FrameMeter("Guide")',
        "verified guide generation")
    # Keep selection focused during D-pad travel; the rail does not switch the EPG
    # behind the focus ring until OK is pressed (the v9 fix).
    if 'onEditLocals = { petalixLocalsOpen=true }' not in s or 'guideAmbient' not in s:
        raise RuntimeError("Local News and dynamic guide background must be present")
    p.write_text(s)
