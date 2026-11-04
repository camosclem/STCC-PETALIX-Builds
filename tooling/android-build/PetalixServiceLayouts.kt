package com.nuvio.tv.petalix.ui2

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview

private enum class HubView { HOME, MOVIES, SERIES, COLLECTIONS }
private data class HubPage(
    val service: PetalixService, val rows: List<CatalogRow>, val hero: MetaPreview?,
    val open: (MetaPreview) -> Unit, val collection: (CatalogRow) -> Unit,
    val step: (Int) -> Unit
)

/** Shared catalog state and detail routes; every service below owns its landing composition. */
@Composable
fun DistinctPetalixServiceScreen(onHome: () -> Unit, onDetail: (String,String,String) -> Unit,
    viewModel: PetalixServiceViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val service = viewModel.service
    if(service==null) {
        Column(Modifier.padding(28.dp)) {
            Text("Service unavailable",color=Color.White)
            PetalixAction("Back to Petalix",onClick=onHome)
        }
        return
    }
    val trailerVm: PetalixTileTrailerViewModel = hiltViewModel()
    val focusedArt by trailerVm.focusedBackdrop.collectAsStateWithLifecycle()
    val context = LocalContext.current.applicationContext
    var view by rememberSaveable(service.id) { mutableStateOf(HubView.HOME) }
    var search by rememberSaveable(service.id) { mutableStateOf(false) }
    var query by rememberSaveable(service.id) { mutableStateOf("") }
    var collection by rememberSaveable(service.id) { mutableStateOf<String?>(null) }
    var featured by rememberSaveable(service.id) { mutableStateOf(0) }
    val searchFocus = remember { FocusRequester() }
    val all = state.rows.flatMap { it.items }.distinctBy { "${it.apiType}:${it.id}" }
    val visible = (if(search && query.isNotBlank()) searchState.items else (collection?.let { title -> state.rows.firstOrNull { it.catalogName==title }?.items } ?: all))
        .filter { (view!=HubView.MOVIES || it.type==ContentType.MOVIE) &&
            (view!=HubView.SERIES || it.type==ContentType.SERIES) }
    val hero = all.getOrNull(featured.coerceIn(0,(all.size-1).coerceAtLeast(0)))
    val page = HubPage(service,state.rows,hero,
        open={ onDetail(it.id,it.apiType,it.sourceAddonBaseUrl.orEmpty()) },
        collection={ collection=it.catalogName; query=""; search=false },
        step={ if(all.isNotEmpty()) featured=(featured+it+all.size)%all.size })
    LaunchedEffect(service.id) {
        android.util.Log.i("PetalixServices", "petalix-distinct-service-layouts-v3")
        PetalixServiceAudio.prepare(context)
        android.util.Log.i("PetalixServices","petalix-service-experience-v4")
    }
    LaunchedEffect(hero?.id) { trailerVm.setFocusedBackdrop(hero?.backdropUrl) }
    LaunchedEffect(query,search) { viewModel.search(if(search)query else "") }
    LaunchedEffect(search) { if(search) { withFrameNanos{}; runCatching {searchFocus.requestFocus()} } }
    BackHandler {
        when {
            search -> {search=false; query=""}
            collection!=null -> collection=null
            view!=HubView.HOME -> view=HubView.HOME
            else -> onHome()
        }
    }
    Box(Modifier.fillMaxSize().background(service.background)) {
        Crossfade(focusedArt ?: hero?.backdropUrl,label="distinctServiceAmbient") { art ->
            AsyncImage(art,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop,alpha=.28f)
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(service.background.copy(alpha=.76f),service.background))))
        Column(Modifier.fillMaxSize()) {
            HubNavigation(service,view,onHome,{ view=it; collection=null; search=false; query="" },{search=!search;view=HubView.HOME;collection=null})
            if(search) OutlinedTextField(query,{query=it},singleLine=true,
                label={androidx.compose.material3.Text("Search ${service.name}")},
                modifier=Modifier.fillMaxWidth().padding(horizontal=28.dp,vertical=8.dp).focusRequester(searchFocus),
                colors=TextFieldDefaults.colors(focusedTextColor=Color.White,unfocusedTextColor=Color.White,
                    focusedContainerColor=service.background,unfocusedContainerColor=service.background))
            if(search && searchState.loading)Text("Searching ${service.name}…",color=service.accent,modifier=Modifier.padding(horizontal=28.dp,vertical=8.dp))
            searchState.error?.takeIf { search }?.let { error ->
                Row(Modifier.padding(horizontal=28.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(error,color=Color.White,modifier=Modifier.weight(1f),fontSize=12.sp)
                    PetalixAction("Retry search",accent=service.accent){viewModel.search(query)}
                }
            }
            if(state.loading && all.isEmpty()) Text("Loading ${service.name}…",color=Color.White,modifier=Modifier.padding(28.dp))
            if(state.error!=null) Row(Modifier.padding(horizontal=28.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(state.error.orEmpty(),color=Color.White,modifier=Modifier.weight(1f),fontSize=12.sp)
                PetalixAction("Retry",accent=service.accent){viewModel.load()}
            }
            Box(Modifier.weight(1f)) {
                when {
                    view==HubView.COLLECTIONS && collection==null && !search -> HubCollectionBrowser(page)
                    search || view!=HubView.HOME || collection!=null -> HubResults(page,visible,
                        collection ?: if(search) if(query.isBlank())"Explore ${service.name}" else if(visible.isEmpty() && !searchState.loading)"No verified matches. Try another title or load the next page." else "${visible.size} results on ${service.name}" else if(view==HubView.MOVIES) "Movies" else "TV Shows")
                    service.id=="netflix" -> NetflixLanding(page)
                    service.id=="disney" -> DisneyLanding(page)
                    service.id=="prime" -> PrimeLanding(page)
                    service.id=="hulu" -> HuluLanding(page)
                    service.id=="max" -> MaxLanding(page)
                    service.id=="paramount" -> ParamountLanding(page)
                    service.id=="apple" -> AppleLanding(page)
                    service.id=="peacock" -> PeacockLanding(page)
                    service.id=="tubi" -> TubiLanding(page)
                    else -> PlutoLanding(page)
                }
            }
            if(search && query.isNotBlank() && searchState.more && !searchState.loading)PetalixAction("More search results",accent=service.accent,
                modifier=Modifier.padding(start=28.dp,top=4.dp,bottom=4.dp)){viewModel.search(query,true)}
            if(!search && state.more && !state.loading) PetalixAction("Load more ${service.name}",accent=service.accent,
                modifier=Modifier.padding(start=28.dp,top=4.dp,bottom=4.dp)){viewModel.load(true)}
            Text("US availability: JustWatch via TMDB • Playback through your configured sources",
                color=Color(0xFF9BAABE),fontSize=9.sp,modifier=Modifier.padding(horizontal=28.dp,vertical=4.dp))
        }
    }
}

@Composable
private fun HubNavigation(s:PetalixService,selected:HubView,home:()->Unit,choose:(HubView)->Unit,search:()->Unit) {
    val homeLabel=when(s.id){"disney","hulu"->"For You";"apple"->"Watch Now";"peacock","pluto"->"Featured";else->"Home"}
    val collectionsLabel=when(s.id){"disney"->"Brands";"hulu"->"Hubs";"prime"->"Categories";"tubi"->"Explore";else->"Collections"}
    val centered=s.id in setOf("disney","max","apple","paramount")
    Column(Modifier.fillMaxWidth().background(if(s.id=="paramount")Color(0xFF0756D8) else s.background.copy(alpha=.92f))) {
        if(centered) Row(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=5.dp),verticalAlignment=Alignment.CenterVertically) {
            PetalixAction("Petalix",Icons.Default.ArrowBack,accent=s.accent,onClick=home)
            Box(Modifier.weight(1f),contentAlignment=Alignment.Center){HubWordmark(s,124)}
            PetalixAction("Search",Icons.Default.Search,accent=s.accent,onClick=search)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=if(centered)Arrangement.Center else Arrangement.spacedBy(12.dp)) {
            if(!centered){PetalixAction("Petalix",Icons.Default.ArrowBack,accent=s.accent,onClick=home);HubWordmark(s,110)}
            listOf(homeLabel to HubView.HOME,"Movies" to HubView.MOVIES,
                (if(s.id=="netflix"||s.id=="hulu")"TV Shows" else "Series") to HubView.SERIES,collectionsLabel to HubView.COLLECTIONS).forEach { (title,destination) ->
                PetalixAction(title,selected=selected==destination,accent=s.accent,
                    modifier=Modifier.padding(horizontal=if(centered)6.dp else 0.dp)){choose(destination)}
            }
            if(!centered){Spacer(Modifier.weight(1f));PetalixAction("Search",Icons.Default.Search,accent=s.accent,onClick=search)}
        }
        if(s.id=="prime")Box(Modifier.fillMaxWidth().height(2.dp).background(Brush.horizontalGradient(listOf(s.accent,Color.Transparent))))
        if(s.id=="peacock")Row(Modifier.fillMaxWidth().height(3.dp)){listOf(0xFFFFD644,0xFFFF7300,0xFFEF174B,0xFF793DB6,0xFF00A5DF,0xFF00B56A).forEach {Box(Modifier.weight(1f).fillMaxHeight().background(Color(it)))}}
    }
}

@Composable internal fun HubWordmark(s:PetalixService,width:Int=132) {
    if(s.id=="pluto")Text("pluto tv",fontWeight=FontWeight.Black,fontSize=26.sp,color=s.accent)
    else ServiceArtwork(s.id,Modifier.width(width.dp).height(40.dp))
}
@Composable private fun HubTitle(text:String,accent:Color=Color.White) {
    Text(text,color=accent,fontSize=22.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(horizontal=28.dp,vertical=10.dp))
}
@Composable private fun HubHeroCopy(p:HubPage,center:Boolean=false,eyebrow:String="",large:Boolean=false) {
    Column(Modifier.widthIn(max=if(center)650.dp else 440.dp).padding(24.dp),
        horizontalAlignment=if(center)Alignment.CenterHorizontally else Alignment.Start,verticalArrangement=Arrangement.spacedBy(10.dp)) {
        if(eyebrow.isNotBlank())Text(eyebrow,color=p.service.accent,fontWeight=FontWeight.Bold,fontSize=12.sp,letterSpacing=2.sp)
        Text(p.hero?.name ?: p.service.name,color=Color.White,fontWeight=FontWeight.Black,fontSize=if(large)40.sp else 30.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
        p.hero?.let { meta ->
            Text(meta.description.orEmpty(),color=Color(0xFFD9DFE8),fontSize=13.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
                PetalixAction("View Details",accent=p.service.accent){p.open(meta)}
                PetalixAction("‹",accent=p.service.accent){p.step(-1)}
                PetalixAction("›",accent=p.service.accent){p.step(1)}
            }
        }
    }
}
@Composable private fun HubBackdrop(p:HubPage,modifier:Modifier=Modifier) {
    Crossfade(p.hero?.backdropUrl,modifier=modifier,label="${p.service.id}FeatureArtwork") { art ->
        AsyncImage(art,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
    }
}
@Composable private fun HubShelf(p:HubPage,row:CatalogRow,width:Int=220,ranked:Boolean=false) {
    HubTitle(row.catalogName,if(p.service.id=="tubi")p.service.accent else Color.White)
    LazyRow(Modifier.focusRestorer(),contentPadding=PaddingValues(horizontal=28.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        itemsIndexed(row.items,key={_,m->"${m.apiType}:${m.id}"}) { i,m ->
            MetadataTile(m,p.service.accent,landscape=true,rank=if(ranked)i+1 else null,previewEnabled=true,
                modifier=Modifier.width(width.dp).height((width*9f/16f).dp)){p.open(m)}
        }
    }
}
@Composable private fun HubCollections(p:HubPage,shape:String="cards",height:Int=84) {
    LazyRow(Modifier.focusRestorer(),contentPadding=PaddingValues(horizontal=28.dp,vertical=10.dp),horizontalArrangement=Arrangement.spacedBy(14.dp)) {
        items(p.rows,key={it.catalogName}) { row ->
            val circle=shape=="circles"
            PetalixTile(Modifier.width(if(circle)112.dp else if(shape=="editorial")330.dp else 184.dp).height(if(circle)112.dp else height.dp),p.service.accent,onClick={p.collection(row)}) {
                Box(Modifier.fillMaxSize().clip(if(circle)CircleShape else RoundedCornerShape(6.dp)).background(p.service.accent.copy(alpha=.18f))) {
                    if(shape !in setOf("type","circles"))AsyncImage(row.items.firstOrNull()?.backdropUrl,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,p.service.background.copy(alpha=.9f)))))
                    Text(if(shape=="type")row.catalogName.uppercase() else row.catalogName,color=if(shape=="type")p.service.accent else Color.White,
                        fontWeight=FontWeight.Bold,fontSize=if(shape=="editorial")20.sp else 13.sp,maxLines=3,
                        modifier=Modifier.align(if(circle||shape=="type")Alignment.Center else Alignment.BottomStart).padding(12.dp))
                }
            }
        }
    }
}
@Composable private fun HubDoubleFeature(p:HubPage) {
    Row(Modifier.fillMaxWidth().padding(horizontal=28.dp,vertical=10.dp),horizontalArrangement=Arrangement.spacedBy(18.dp)) {
        p.rows.take(2).forEach { row ->
            PetalixTile(Modifier.weight(1f).height(174.dp),p.service.accent,onClick={p.collection(row)}) {
                AsyncImage(row.items.firstOrNull()?.backdropUrl,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,p.service.background))))
                Text(row.catalogName,color=Color.White,fontSize=22.sp,fontWeight=FontWeight.Bold,modifier=Modifier.align(Alignment.BottomStart).padding(18.dp),maxLines=2)
            }
        }
    }
}

@Composable private fun NetflixLanding(p:HubPage) {
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=24.dp)) {
        item("netflix-billboard") {
            Box(Modifier.fillMaxWidth().height(310.dp)) {
                HubBackdrop(p,Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black,Color.Black.copy(alpha=.3f),Color.Transparent))))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,p.service.background))))
                Box(Modifier.align(Alignment.BottomStart)){HubHeroCopy(p,eyebrow=if(p.hero?.type==ContentType.SERIES)"N  SERIES" else "N  FILM",large=true)}
            }
        }
        p.rows.firstOrNull()?.let { row -> item("netflix-ranked"){HubShelf(p,row,width=252,ranked=true)} }
        item("netflix-collections"){HubCollections(p,height=96)}
        items(p.rows.drop(1),key={it.catalogName}){HubShelf(p,it)}
    }
}
@Composable private fun DisneyLanding(p:HubPage) {
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=24.dp)) {
        item("disney-carousel") {
            Box(Modifier.fillMaxWidth().padding(horizontal=36.dp,vertical=16.dp).height(265.dp).clip(RoundedCornerShape(14.dp)).border(1.dp,Color(0xFF516FA8),RoundedCornerShape(14.dp))) {
                HubBackdrop(p,Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xFF061236),Color.Transparent))))
                Box(Modifier.align(Alignment.BottomStart)){HubHeroCopy(p,eyebrow="DISNEY+ SPOTLIGHT")}
            }
        }
        item("disney-brand-deck"){HubCollections(p,height=112)}
        items(p.rows,key={it.catalogName}){HubShelf(p,it,width=248)}
    }
}
@Composable private fun PrimeLanding(p:HubPage) {
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=24.dp)) {
        item("prime-feature-split") {
            Row(Modifier.fillMaxWidth().height(286.dp).background(Color(0xFF001322)),verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.weight(.43f)){HubHeroCopy(p,eyebrow="prime video",large=true)}
                Box(Modifier.weight(.57f).fillMaxHeight()) {
                    HubBackdrop(p,Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xFF001322),Color.Transparent))))
                }
            }
        }
        item("prime-category-ribbon"){HubCollections(p,shape="type",height=48)}
        p.rows.firstOrNull()?.let{item("prime-super-carousel"){HubShelf(p,it,width=310)}}
        items(p.rows.drop(1),key={it.catalogName}){HubShelf(p,it,width=236)}
    }
}
@Composable private fun HuluLanding(p:HubPage) {
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=24.dp)) {
        item("hulu-feature-mosaic") {
            Row(Modifier.fillMaxWidth().height(288.dp).padding(24.dp),horizontalArrangement=Arrangement.spacedBy(16.dp),verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.weight(1f)){HubHeroCopy(p,eyebrow="YOUR NEXT FAVORITE",large=true)}
                Box(Modifier.weight(1.05f).fillMaxHeight().clip(RoundedCornerShape(5.dp))){HubBackdrop(p,Modifier.fillMaxSize())}
                Column(Modifier.weight(.48f).fillMaxHeight(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    p.rows.drop(1).take(2).forEach { row ->
                        PetalixTile(Modifier.weight(1f).fillMaxWidth(),p.service.accent,onClick={p.collection(row)}) {
                            AsyncImage(row.items.firstOrNull()?.backdropUrl,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black))))
                            Text(row.catalogName,color=Color.White,fontSize=12.sp,modifier=Modifier.align(Alignment.BottomStart).padding(8.dp),maxLines=2)
                        }
                    }
                }
            }
        }
        item("hulu-hubs"){HubCollections(p,shape="type",height=72)}
        items(p.rows,key={it.catalogName}){HubShelf(p,it,width=264)}
    }
}
@Composable private fun MaxLanding(p:HubPage) {
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=24.dp)) {
        item("max-theatrical") {
            Box(Modifier.fillMaxWidth().height(300.dp)) {
                HubBackdrop(p,Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color(0xFF100C2B)))))
                Box(Modifier.align(Alignment.BottomCenter)){HubHeroCopy(p,center=true,eyebrow="THE SPOTLIGHT")}
            }
        }
        item("max-editorial-duet"){HubDoubleFeature(p)}
        item("max-collections"){HubCollections(p,shape="editorial",height=130)}
        items(p.rows,key={it.catalogName}){HubShelf(p,it,width=284)}
    }
}
@Composable private fun ParamountLanding(p:HubPage) {
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=24.dp)) {
        item("paramount-marquee") {
            Box(Modifier.fillMaxWidth().height(286.dp)) {
                HubBackdrop(p,Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xFF073EAA),Color.Transparent))))
                Box(Modifier.align(Alignment.CenterStart)){HubHeroCopy(p,eyebrow="PARAMOUNT+ FEATURED",large=true)}
            }
        }
        item("paramount-brand-circles"){HubCollections(p,shape="circles")}
        items(p.rows,key={it.catalogName}){HubShelf(p,it,width=238)}
    }
}
@Composable private fun AppleLanding(p:HubPage) {
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=24.dp)) {
        item("apple-editorial-cover") {
            Box(Modifier.fillMaxWidth().height(360.dp)) {
                HubBackdrop(p,Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black))))
                Box(Modifier.align(Alignment.BottomCenter)){HubHeroCopy(p,center=true,eyebrow="APPLE TV+",large=true)}
            }
        }
        p.rows.firstOrNull()?.let{item("apple-premiere-deck"){HubShelf(p,it,width=390)}}
        item("apple-story-collections"){HubCollections(p,shape="editorial",height=160)}
        items(p.rows.drop(1),key={it.catalogName}){HubShelf(p,it,width=310)}
    }
}
@Composable private fun PeacockLanding(p:HubPage) {
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=24.dp)) {
        item("peacock-offset-feature") {
            Box(Modifier.fillMaxWidth().height(284.dp).padding(start=28.dp,top=14.dp,end=28.dp)) {
                HubBackdrop(p,Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)))
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xFF080C11),Color.Transparent))))
                Box(Modifier.align(Alignment.BottomStart)){HubHeroCopy(p,eyebrow="STREAM SOMETHING GREAT",large=true)}
            }
        }
        item("peacock-content-pillars"){HubCollections(p,shape="type",height=58)}
        item("peacock-double-spotlight"){HubDoubleFeature(p)}
        items(p.rows,key={it.catalogName}){HubShelf(p,it,width=260)}
    }
}
@Composable private fun TubiLanding(p:HubPage) {
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=24.dp)) {
        item("tubi-bold-feature") {
            Row(Modifier.fillMaxWidth().height(260.dp).background(Color(0xFF3A086C)),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(.44f).padding(start=30.dp)) {
                    Text("FIND YOUR\nNEXT RABBIT HOLE.",color=p.service.accent,fontSize=32.sp,fontWeight=FontWeight.Black)
                    p.hero?.let { meta ->
                        Text(meta.name,color=Color.White,fontSize=22.sp,fontWeight=FontWeight.Bold,maxLines=2,modifier=Modifier.padding(vertical=12.dp))
                        PetalixAction("View Details",accent=p.service.accent){p.open(meta)}
                    }
                }
                Box(Modifier.weight(.56f).fillMaxHeight()){HubBackdrop(p,Modifier.fillMaxSize())}
            }
        }
        item("tubi-explore-grid"){HubTitle("Explore the collection",p.service.accent);HubCollectionGrid(p)}
        items(p.rows,key={it.catalogName}){HubShelf(p,it,width=244)}
    }
}
@Composable private fun PlutoLanding(p:HubPage) {
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=24.dp)) {
        item("pluto-collection-dial"){HubCollections(p,shape="type",height=48)}
        item("pluto-feature-and-next") {
            Row(Modifier.fillMaxWidth().height(290.dp).padding(horizontal=28.dp),horizontalArrangement=Arrangement.spacedBy(18.dp)) {
                Box(Modifier.weight(1.6f).fillMaxHeight().border(2.dp,p.service.accent,RoundedCornerShape(8.dp))) {
                    HubBackdrop(p,Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black))))
                    Box(Modifier.align(Alignment.BottomStart)){HubHeroCopy(p,eyebrow="ON DEMAND SPOTLIGHT")}
                }
                Column(Modifier.weight(.7f).fillMaxHeight(),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("KEEP EXPLORING",color=p.service.accent,fontSize=13.sp,fontWeight=FontWeight.Black)
                    p.rows.take(3).forEach { row ->
                        PetalixTile(Modifier.fillMaxWidth().weight(1f),p.service.accent,onClick={p.collection(row)}) {
                            Row(Modifier.fillMaxSize().background(Color(0xFF222222)).padding(10.dp),verticalAlignment=Alignment.CenterVertically) {
                                Text("▶",color=p.service.accent,fontSize=22.sp)
                                Text(row.catalogName,color=Color.White,fontSize=14.sp,modifier=Modifier.padding(start=14.dp),maxLines=2)
                            }
                        }
                    }
                }
            }
        }
        items(p.rows,key={it.catalogName}){HubShelf(p,it,width=250)}
    }
}
@Composable private fun HubCollectionGrid(p:HubPage) {
    Column(Modifier.padding(horizontal=28.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        p.rows.chunked(3).forEach { group ->
            Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                group.forEach { row ->
                    PetalixTile(Modifier.weight(1f).height(104.dp),p.service.accent,onClick={p.collection(row)}) {
                        AsyncImage(row.items.firstOrNull()?.backdropUrl,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                        Box(Modifier.fillMaxSize().background(p.service.background.copy(alpha=.55f)))
                        Text(row.catalogName,color=p.service.accent,fontWeight=FontWeight.Black,fontSize=19.sp,maxLines=2,modifier=Modifier.align(Alignment.Center).padding(14.dp))
                    }
                }
                repeat(3-group.size){Spacer(Modifier.weight(1f))}
            }
        }
    }
}
@Composable private fun HubCollectionBrowser(p:HubPage) {
    LazyColumn(Modifier.fillMaxSize()) {item{HubTitle("${p.service.name} collections");HubCollectionGrid(p)}}
}
@Composable private fun HubResults(p:HubPage,titles:List<MetaPreview>,title:String) {
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=24.dp)) {
        item{HubTitle(title)}
        if(titles.isEmpty())item{Text("No matching titles in the loaded catalog.",color=Color.White,modifier=Modifier.padding(28.dp))}
        items(titles.chunked(3),key={group->group.joinToString{"${it.apiType}:${it.id}"}}) { group ->
            Row(Modifier.fillMaxWidth().padding(horizontal=28.dp,vertical=10.dp),horizontalArrangement=Arrangement.spacedBy(18.dp)) {
                group.forEach { meta ->
                    MetadataTile(meta,p.service.accent,landscape=true,previewEnabled=true,
                        modifier=Modifier.weight(1f).height(150.dp)){p.open(meta)}
                }
                repeat(3-group.size){Spacer(Modifier.weight(1f))}
            }
        }
    }
}
