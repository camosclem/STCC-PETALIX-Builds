package com.nuvio.tv.petalix.ui2

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.core.tmdb.TmdbCollectionSourceResolver
import com.nuvio.tv.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import org.json.JSONObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** These are US catalog associations, never stream URLs or subscription entitlements. */
data class PetalixService(val id:String,val name:String,val provider:String,val accent:Color,val background:Color,val style:String,val collections:List<Pair<String,String>>) {
    fun sources():List<TmdbCollectionSource> {
        val base=TmdbCollectionFilters(watchRegion="US",withWatchProviders=provider,watchMonetizationTypes="flatrate|free|ads")
        fun source(title:String,tv:Boolean=false,genre:String?=null,sort:String="popularity.desc")=TmdbCollectionSource(TmdbCollectionSourceType.DISCOVER,title,mediaType=if(tv)TmdbCollectionMediaType.TV else TmdbCollectionMediaType.MOVIE,sortBy=sort,filters=base.copy(withGenres=genre))
        return listOf(source(if(style=="netflix")"Trending on $name" else "Featured on $name"),source("Popular Series",true),source("New Movies",sort="primary_release_date.desc")) + collections.map { source(it.first,genre=it.second) }
    }
}
object PetalixServices {
    val all=listOf(
        PetalixService("netflix","NETFLIX","8",Color(0xFFE50914),Color(0xFF080808),"netflix",listOf("Thrillers & Suspense" to "53","Documentary Films" to "99","Family Movie Night" to "10751")),
        PetalixService("prime","prime video","9",Color(0xFF00A8E1),Color(0xFF07111E),"prime",listOf("Action & Adventure" to "28|12","Sci-Fi Worlds" to "878","Comedies" to "35")),
        PetalixService("hulu","hulu","15",Color(0xFF1CE783),Color(0xFF071B16),"hulu",listOf("Comedy Spotlight" to "35","True Crime & Documentaries" to "99","Drama Picks" to "18")),
        PetalixService("disney","Disney+","337",Color(0xFF6AA8FF),Color(0xFF07143A),"disney",listOf("Animation" to "16","Epic Adventures" to "12","Family Favorites" to "10751","Nature & Discovery" to "99")),
        PetalixService("max","Max","1899",Color(0xFF9F78FF),Color(0xFF100C2B),"max",listOf("Prestige Drama" to "18","Big Screen Cinema" to "28","Documentary Spotlight" to "99")),
        PetalixService("paramount","Paramount+","531",Color(0xFF3C7FFF),Color(0xFF061A36),"paramount",listOf("Action Blockbusters" to "28","Family & Kids" to "10751","Comedy Central" to "35")),
        PetalixService("apple","Apple TV","350",Color(0xFFE4E4E7),Color(0xFF0C0C0D),"apple",listOf("Compelling Drama" to "18","Thought-Provoking Sci-Fi" to "878","Documentary Stories" to "99")),
        PetalixService("peacock","peacock","386|387",Color(0xFFFFD644),Color(0xFF080C11),"peacock",listOf("Laugh Out Loud" to "35","Crime & Suspense" to "53","Family Time" to "10751")),
        PetalixService("tubi","tubi","73",Color(0xFFF7ED36),Color(0xFF230740),"tubi",listOf("Cult Classics" to "27","Action Favorites" to "28","Comedy Picks" to "35")),
        PetalixService("pluto","pluto tv","300",Color(0xFFFEDD00),Color(0xFF0E0E0E),"pluto",listOf("Movie Night" to "28","Comedy" to "35"))
    )
    fun find(id:String)=all.firstOrNull { it.id==id }
}

@Composable
fun ServiceTile(service:PetalixService,modifier:Modifier=Modifier,onClick:()->Unit) {
    PetalixTile(modifier.width(112.dp).height(50.dp).semantics { contentDescription=service.name },PetalixBlue,onClick) {
        if(service.id=="pluto")Text(service.name,color=service.accent,fontSize=22.sp,fontWeight=FontWeight.Black,modifier=Modifier.align(Alignment.Center))
        else ServiceArtwork(service.id,Modifier.fillMaxSize())
    }
}

data class PetalixServiceState(val rows:List<CatalogRow> = emptyList(),val loading:Boolean=true,val error:String?=null,val page:Int=1,val more:Boolean=true)
@HiltViewModel
class PetalixServiceViewModel @Inject constructor(saved:SavedStateHandle,private val resolver:TmdbCollectionSourceResolver,@ApplicationContext context:Context):ViewModel() {
    val service=PetalixServices.find(saved.get<String>("serviceId").orEmpty())
    private val sources=service?.let { ServiceCatalogs.sources(context,it) }.orEmpty()
    private val _state=MutableStateFlow(PetalixServiceState())
    val state=_state.asStateFlow()
    init { load() }
    fun load(next:Boolean=false) {
        val selected=service ?: run { _state.value=PetalixServiceState(loading=false,error="This service is not configured.");return }
        if(next && (_state.value.loading || !_state.value.more))return
        val page=if(next)_state.value.page+1 else 1
        viewModelScope.launch {
            _state.update { it.copy(loading=true,error=null) }
            if(BuildConfig.TMDB_API_KEY.isBlank()) {
                _state.update { it.copy(loading=false,error="Service catalogs require the existing metadata configuration. This isolated test build has no metadata key.",more=false) };return@launch
            }
            val results=coroutineScope { sources.map { source->async { resolver.resolve(source,page).first { it !is NetworkResult.Loading } } }.awaitAll() }
            val rows=results.mapNotNull { (it as? NetworkResult.Success)?.data }
            val errors=results.count { it is NetworkResult.Error }
            _state.update { old ->
                val merged=if(!next)rows else old.rows.map { previous ->
                    val addition=rows.firstOrNull { it.catalogName==previous.catalogName }
                    if(addition==null)previous else addition.copy(items=(previous.items+addition.items).distinctBy { "${it.apiType}:${it.id}" })
                }
                old.copy(rows=merged,loading=false,page=page,more=rows.any { it.hasMore },error=when { rows.isEmpty()->"The service catalog could not be loaded. Check your connection and metadata configuration.";errors>0->"Some collections could not be loaded. You can retry.";else->null })
            }
        }
    }
}

@Composable
fun Petalix2ServiceScreen(onHome:()->Unit,onDetail:(String,String,String)->Unit,viewModel:PetalixServiceViewModel=hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val service=viewModel.service
    if(service==null) { Column(Modifier.padding(28.dp)) { Text("Service unavailable",color=Color.White);PetalixAction("Back to Petalix",onClick=onHome) };return }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf("All") }
    var collectionTitle by rememberSaveable { mutableStateOf<String?>(null) }
    val searchFocus=remember { FocusRequester() }
    BackHandler(searchOpen) { searchOpen=false;query="" }
    LaunchedEffect(searchOpen) { if(searchOpen) { withFrameNanos{};runCatching { searchFocus.requestFocus() } } }
    val all=state.rows.flatMap { it.items }.distinctBy { "${it.apiType}:${it.id}" }
    val visible=(collectionTitle?.let { title->state.rows.firstOrNull { it.catalogName==title }?.items } ?: all).filter { (kind=="All" || if(kind=="Movies")it.type==ContentType.MOVIE else it.type==ContentType.SERIES) && it.name.contains(query,true) }
    val hero=visible.firstOrNull()
    val rowTitles=emptyList<String>()
    Column(Modifier.fillMaxSize().background(service.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal=26.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            PetalixAction("Petalix",Icons.Default.ArrowBack,accent=service.accent,onClick=onHome)
            Text(service.name,color=service.accent,fontWeight=FontWeight.Black,fontSize=26.sp,modifier=Modifier.weight(1f))
            listOf("All","Movies","TV Shows").forEach { tab->PetalixAction(tab,selected=kind==tab,accent=service.accent) {kind=tab;collectionTitle=null} }
            PetalixAction("Search ${service.name}",Icons.Default.Search,accent=service.accent) {searchOpen=!searchOpen}
        }
        if(searchOpen)OutlinedTextField(value=query,onValueChange={query=it},singleLine=true,label={androidx.compose.material3.Text("Search this service's loaded catalog")},modifier=Modifier.fillMaxWidth().padding(horizontal=28.dp).focusRequester(searchFocus),colors=TextFieldDefaults.colors(focusedTextColor=Color.White,unfocusedTextColor=Color.White,focusedContainerColor=service.background,unfocusedContainerColor=service.background))
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(bottom=30.dp),verticalArrangement=Arrangement.spacedBy(22.dp)) {
            if(query.isBlank())item(key="hero") {
                val height=when(service.style){"apple"->280.dp;"disney"->240.dp;"hulu"->200.dp;"max"->260.dp;"paramount"->225.dp;"peacock"->195.dp;"tubi"->180.dp;"pluto"->170.dp;else->230.dp}
                Box(Modifier.fillMaxWidth().height(height).padding(horizontal=if(service.style in listOf("disney","prime"))24.dp else 0.dp)) {
                    AsyncImage(hero?.backdropUrl,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(service.background,service.background.copy(alpha=.7f),Color.Transparent))))
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,service.background))))
                    Column(Modifier.align(if(service.style=="apple")Alignment.BottomCenter else Alignment.CenterStart).padding(28.dp).widthIn(max=500.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text(if(service.style=="hulu")"WELCOME TO YOUR NEXT FAVORITE" else "EXPLORE ${service.name.uppercase()}",color=service.accent,fontSize=12.sp,fontWeight=FontWeight.Bold)
                        Text(hero?.name ?: service.name,color=Color.White,fontSize=36.sp,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Text(hero?.description ?: "Browse movies and series from your connected sources.",color=Color(0xFFD2D8E4),fontSize=14.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
                        if(hero!=null)PetalixAction("View Details",accent=service.accent) { onDetail(hero.id,hero.apiType,hero.sourceAddonBaseUrl.orEmpty()) }
                    }
                }
            }
            if(service.style=="disney" && query.isBlank())item(key="collections") {
                LazyRow(modifier=Modifier.focusRestorer(),contentPadding=PaddingValues(horizontal=26.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    items(state.rows.filter { it.catalogName in setOf("Disney Classics","Pixar Favorites","Marvel Studios Movies","National Geographic Series") },key={it.catalogName}) { collection ->PetalixTile(Modifier.width(195.dp).height(68.dp),service.accent,onClick={collectionTitle=collection.catalogName;query=""}) { Text(collection.catalogName,color=Color.White,fontSize=16.sp,fontWeight=FontWeight.Bold,modifier=Modifier.align(Alignment.Center)) } }
                }
            }
            if(state.loading)item(key="loading") { Text("Loading ${service.name} catalog…",color=Color.White,modifier=Modifier.padding(horizontal=28.dp)) }
            if(state.error!=null)item(key="error") { Column(Modifier.padding(horizontal=28.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) { Text(state.error.orEmpty(),color=Color(0xFFCCD6E6));PetalixAction("Retry",accent=service.accent) {viewModel.load()} } }
            if(query.isNotBlank() || kind!="All" || collectionTitle!=null)item(key="search-results") {
                PetalixSection(if(query.isBlank())collectionTitle ?: kind else "${visible.size} matches in loaded catalog") {
                    if(visible.isEmpty()&&!state.loading)Text("No matching titles in the loaded catalog.",color=Color.White,modifier=Modifier.padding(horizontal=28.dp))
                    LazyRow(modifier=Modifier.focusRestorer(),contentPadding=PaddingValues(horizontal=26.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        items(visible,key={"${it.apiType}:${it.id}"}) { meta->MetadataTile(meta,service.accent,landscape=service.style=="apple") {onDetail(meta.id,meta.apiType,meta.sourceAddonBaseUrl.orEmpty())} }
                    }
                }
            } else itemsIndexed(state.rows,key={i,row->"$i:${row.catalogName}"}) { index,row ->
                PetalixSection(rowTitles.getOrNull(index) ?: row.catalogName) {
                    LazyRow(modifier=Modifier.focusRestorer(),contentPadding=PaddingValues(horizontal=26.dp),horizontalArrangement=Arrangement.spacedBy(if(service.style=="hulu")18.dp else 12.dp)) {
                        itemsIndexed(row.items,key={_,meta->"${meta.apiType}:${meta.id}"}) { rank,meta ->MetadataTile(meta,service.accent,landscape=service.style in listOf("prime","apple","peacock","pluto") || (service.style=="hulu" && index==0) || (service.style=="max" && index%2==0),rank=if(service.style=="netflix" && index==0)rank+1 else null) {onDetail(meta.id,meta.apiType,meta.sourceAddonBaseUrl.orEmpty())} }
                    }
                }
            }
            if(state.more&&!state.loading)item(key="more") { PetalixAction("Load more ${service.name} titles",modifier=Modifier.padding(horizontal=28.dp),accent=service.accent) {viewModel.load(true)} }
            item(key="availability") { Text("Service availability describes the US catalog. Playback depends on your authorized sources. Petalix is an independent browser.",color=Color(0xFF9BAABE),fontSize=11.sp,modifier=Modifier.padding(horizontal=28.dp)) }
        }
    }
}

/** Import the saved, service-specific queries. Never broaden an empty provider result into a
 * global catalog or include rental-only titles. Collections without provider filtering are
 * deliberately omitted until availability can be checked independently. */
object ServiceCatalogs {
    fun sources(context:Context,service:PetalixService):List<TmdbCollectionSource> {
        val json=context.assets.open("petalix2/service-catalogs.json").bufferedReader().use { it.readText() }
        return parse(json,service)
    }
    fun parse(json:String,service:PetalixService):List<TmdbCollectionSource> {
        val folders=org.json.JSONArray(json).getJSONObject(0).getJSONArray("folders")
        val title=mapOf("netflix" to "Netflix","prime" to "Prime Video","disney" to "Disney+","hulu" to "Hulu","max" to "HBO Max","apple" to "Apple TV+","paramount" to "Paramount+","peacock" to "Peacock","tubi" to "Tubi TV","pluto" to "Pluto TV")[service.id]
        val folder=(0 until folders.length()).map { folders.getJSONObject(it) }.firstOrNull { it.optString("title")==title } ?: return emptyList()
        val items=folder.getJSONArray("sources")
        return (0 until items.length()).mapNotNull { index ->
            val source=items.getJSONObject(index)
            if(source.optString("tmdbSourceType")!="DISCOVER")return@mapNotNull null
            val f=source.optJSONObject("filters") ?: JSONObject()
            fun str(key:String)=f.optString(key).takeIf { it.isNotBlank() }
            TmdbCollectionSource(TmdbCollectionSourceType.DISCOVER,source.getString("title"),mediaType=if(source.optString("mediaType")=="TV")TmdbCollectionMediaType.TV else TmdbCollectionMediaType.MOVIE,sortBy=source.optString("sortBy","popularity.desc"),filters=TmdbCollectionFilters(
                watchRegion="US",withWatchProviders=service.provider,watchMonetizationTypes="flatrate|free|ads",
                withGenres=str("withGenres"),withCompanies=str("withCompanies"),withNetworks=str("withNetworks"),
                releaseDateGte=str("releaseDateGte"),releaseDateLte=str("releaseDateLte"),withOriginalLanguage=str("withOriginalLanguage"),voteCountGte=if(f.has("voteCountGte"))f.optInt("voteCountGte") else null))
        }
    }
}
