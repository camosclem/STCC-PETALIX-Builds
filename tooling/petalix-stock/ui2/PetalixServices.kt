// Service foundation recovered from panel 8.3 build 1793881218; native 13.1 route/config adapters.
package com.nuvio.tv.petalix.ui2

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.petalix.PetalixRuntimeConfig
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.core.tmdb.TmdbCollectionSourceResolver
import com.nuvio.tv.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
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
        PetalixService("netflix","Netflix","8|1796",Color(0xFFE50914),Color(0xFF080808),"netflix",listOf("Thrillers & Suspense" to "53","Documentary Films" to "99","Family Movie Night" to "10751")),
        PetalixService("prime","Prime Video","9|2100",Color(0xFF00A8E1),Color(0xFF07111E),"prime",listOf("Action & Adventure" to "28|12","Sci-Fi Worlds" to "878","Comedies" to "35")),
        PetalixService("hulu","Hulu","15",Color(0xFF1CE783),Color(0xFF071B16),"hulu",listOf("Comedy Spotlight" to "35","True Crime & Documentaries" to "99","Drama Picks" to "18")),
        PetalixService("disney","Disney+","337",Color(0xFF6AA8FF),Color(0xFF07143A),"disney",listOf("Animation" to "16","Epic Adventures" to "12","Family Favorites" to "10751","Nature & Discovery" to "99")),
        PetalixService("max","Max","1899|1825",Color(0xFF9F78FF),Color(0xFF100C2B),"max",listOf("Prestige Drama" to "18","Big Screen Cinema" to "28","Documentary Spotlight" to "99")),
        PetalixService("paramount","Paramount+","2303",Color(0xFF3C7FFF),Color(0xFF061A36),"paramount",listOf("Action Blockbusters" to "28","Family & Kids" to "10751","Comedy Central" to "35")),
        PetalixService("apple","Apple TV","350",Color(0xFFE4E4E7),Color(0xFF0C0C0D),"apple",listOf("Compelling Drama" to "18","Thought-Provoking Sci-Fi" to "878","Documentary Stories" to "99")),
        PetalixService("peacock","Peacock","387",Color(0xFFFFD644),Color(0xFF080C11),"peacock",listOf("Laugh Out Loud" to "35","Crime & Suspense" to "53","Family Time" to "10751")),
        PetalixService("britbox","BritBox","151|1852|197",Color(0xFFE51B3E),Color(0xFF110A17),"britbox",listOf("British Drama" to "18","Mysteries & Crime" to "9648|80","Comedy Classics" to "35")),
    )
    private val folderIds=mapOf(
        "folder-WFGY1XPD" to "netflix", "folder-AT8KYHZO" to "prime",
        "folder-8ZBZOMW9" to "disney", "folder-LN9SPMQN" to "hulu",
        "folder-OA4T8NYO" to "max", "folder-IQTF27K8" to "apple",
        "folder-9YQK1ZF1" to "peacock", "folder-ILJM8PD0" to "paramount",
        "folder-petalix-britbox" to "britbox"
    )
    fun find(id:String)=all.firstOrNull { it.id==id }
    fun findFolder(folderId:String)=folderIds[folderId]?.let(::find)
    fun folderId(serviceId:String)=folderIds.entries.firstOrNull { it.value==serviceId }?.key
}

data class PetalixServiceState(val rows:List<CatalogRow> = emptyList(),val loading:Boolean=true,val error:String?=null,val page:Int=1,val more:Boolean=true)
@HiltViewModel
class PetalixServiceViewModel @Inject constructor(saved:SavedStateHandle,private val resolver:TmdbCollectionSourceResolver,private val tmdbApi:com.nuvio.tv.data.remote.api.TmdbApi,@ApplicationContext context:Context):ViewModel() {
    val service=PetalixServices.find(saved.get<String>("serviceId").orEmpty())
        ?: PetalixServices.findFolder(saved.get<String>("folderId").orEmpty())
    private val sources=service?.let { selected -> ServiceCatalogs.sources(context,selected).ifEmpty { selected.sources() } }.orEmpty()
    private val _state=MutableStateFlow(PetalixServiceState())
    val state=_state.asStateFlow()
    private val searchEngine=PetalixServiceSearch(tmdbApi)
    private val _searchState=MutableStateFlow(ServiceSearchState())
    val searchState=_searchState.asStateFlow()
    private var searchJob:Job?=null
    private var loadJob:Job?=null
    fun search(query:String,next:Boolean=false) {
        val text=query.trim()
        val selected=service ?: return
        if(next && (_searchState.value.loading || !_searchState.value.more))return
        searchJob?.cancel()
        if(text.isBlank()){_searchState.value=ServiceSearchState();return}
        val previous=_searchState.value
        val page=if(next && previous.query==text)previous.page+1 else 1
        _searchState.value=if(page>1)previous.copy(loading=true,error=null) else ServiceSearchState(query=text,loading=true)
        searchJob=viewModelScope.launch {
            try {
                if(page==1)kotlinx.coroutines.delay(450)
                val found=searchEngine.search(selected,text,page)
                _searchState.value=if(page==1)found else found.copy(items=(previous.items+found.items).distinctBy { "${it.apiType}:${it.id}" })
            } catch(cancel:CancellationException){throw cancel}
              catch(error:Exception){_searchState.update { it.copy(loading=false,error=error.message ?: "Search failed. Please retry.") }}
        }
    }
    init { load() }
    fun load(next:Boolean=false) {
        val selected=service ?: run { _state.value=PetalixServiceState(loading=false,error="This service is not configured.");return }
        if(next && (_state.value.loading || !_state.value.more))return
        val page=if(next)_state.value.page+1 else 1
        loadJob?.cancel()
        _state.update { it.copy(loading=true,error=null) }
        loadJob=viewModelScope.launch {
            if(PetalixRuntimeConfig.apiKey.isBlank()) {
                _state.update { it.copy(loading=false,error="Reconnect to Petalix in Settings to load this service catalog.",more=false) };return@launch
            }
            val results=kotlinx.coroutines.supervisorScope { sources.map { source->async {
                try { kotlinx.coroutines.withTimeout(25000) { resolver.resolve(source,page).first { it !is NetworkResult.Loading } } }
                catch(e:kotlinx.coroutines.TimeoutCancellationException){null}
                catch(e:CancellationException){throw e}
                catch(e:Exception){null}
            } }.awaitAll() }
            val rows=results.mapNotNull { (it as? NetworkResult.Success)?.data }
            val errors=results.count { it == null || it is NetworkResult.Error }
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
fun PetalixServiceScreen(onHome:()->Unit,onDetail:(String,String,String)->Unit,viewModel:PetalixServiceViewModel=hiltViewModel()) {
    DistinctPetalixServiceScreen(onHome,onDetail,viewModel)
}

/** Import the saved, service-specific queries. Never broaden an empty provider result into a
 * global catalog or include rental-only titles. Collections without provider filtering are
 * deliberately omitted until availability can be checked independently. */
object ServiceCatalogs {
    fun sources(context:Context,service:PetalixService):List<TmdbCollectionSource> {
        val json=context.assets.open("petalix/streaming-services.json").bufferedReader().use { it.readText() }
        return parse(json,service)
    }
    fun parse(json:String,service:PetalixService):List<TmdbCollectionSource> {
        val folders=org.json.JSONArray(json).getJSONObject(0).getJSONArray("folders")
        val title=mapOf("netflix" to "Netflix","prime" to "Prime Video","disney" to "Disney+","hulu" to "Hulu","max" to "HBO Max","apple" to "Apple TV","paramount" to "Paramount+","peacock" to "Peacock","britbox" to "BritBox")[service.id]
        val folder=(0 until folders.length()).map { folders.getJSONObject(it) }.firstOrNull { it.optString("id")==PetalixServices.folderId(service.id) || it.optString("title")==title } ?: return emptyList()
        val items=folder.getJSONArray("sources")
        return (0 until items.length()).mapNotNull { index ->
            val source=items.getJSONObject(index)
            if(source.optString("tmdbSourceType")!="DISCOVER")return@mapNotNull null
            val f=source.optJSONObject("filters") ?: JSONObject()
            fun str(key:String)=f.optString(key).takeIf { it.isNotBlank() && it!="null" && !it.startsWith("@panel_") }
            TmdbCollectionSource(TmdbCollectionSourceType.DISCOVER,source.getString("title"),mediaType=if(source.optString("mediaType")=="TV")TmdbCollectionMediaType.TV else TmdbCollectionMediaType.MOVIE,sortBy=source.optString("sortBy","popularity.desc"),filters=TmdbCollectionFilters(
                watchRegion="US",withWatchProviders=service.provider,watchMonetizationTypes="flatrate|free|ads",
                withGenres=str("withGenres"),withCompanies=str("withCompanies"),withNetworks=str("withNetworks"),
                releaseDateGte=str("releaseDateGte"),releaseDateLte=str("releaseDateLte"),withOriginalLanguage=str("withOriginalLanguage"),voteCountGte=if(f.has("voteCountGte"))f.optInt("voteCountGte") else null))
        }
    }
}
