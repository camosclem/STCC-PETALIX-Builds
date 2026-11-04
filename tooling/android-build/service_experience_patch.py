"""Carry service identity through remote search, details, episodes and return navigation."""
from pathlib import Path
import shutil

def once(text,old,new,label):
    if text.count(old)!=1:raise RuntimeError('Service experience integration changed: '+label)
    return text.replace(old,new)

def apply(work):
    source=Path(work)/'app/src/main/java/com/nuvio/tv'
    ui=source/'petalix/ui2'
    for name in ('PetalixServiceSearch.kt','PetalixServiceDetails.kt'):
        shutil.copyfile(Path(__file__).with_name(name),ui/name)
    p=source/'data/remote/api/TmdbApi.kt'
    s=p.read_text()
    s=once(s,'interface TmdbApi {','''interface TmdbApi {
    @GET("search/{media}")
    suspend fun petalixSearch(@Path("media") media:String,@Query("api_key") key:String,
        @Query("query") query:String,@Query("page") page:Int,
        @Query("include_adult") adult:Boolean=false):Response<okhttp3.ResponseBody>
    @GET("{media}/{id}/watch/providers")
    suspend fun petalixProviders(@Path("media") media:String,@Path("id") id:Int,
        @Query("api_key") key:String):Response<okhttp3.ResponseBody>
''','remote service search API')
    p.write_text(s)
    p=ui/'Petalix2Services.kt';s=p.read_text()
    s=once(s,'private val resolver:TmdbCollectionSourceResolver,@ApplicationContext','private val resolver:TmdbCollectionSourceResolver,private val tmdbApi:com.nuvio.tv.data.remote.api.TmdbApi,@ApplicationContext','search dependency')
    s=once(s,'    init { load() }',SEARCH_VIEWMODEL+'    init { load() }','search state')
    p.write_text(s)
    p=source/'ui/navigation/NuvioNavHost.kt';s=p.read_text()
    s=once(s,'onDetail = { id,type,url -> navController.navigate(Screen.Detail.createRoute(id,type,url)) }','''onDetail = { id,type,url ->
                    val serviceId=navController.currentBackStackEntry?.arguments?.getString("serviceId")
                    navController.navigate(Screen.Detail.createRoute(id,type,url))
                    navController.currentBackStackEntry?.savedStateHandle?.set("petalixServiceId",serviceId)
                }''','service details context')
    s=once(s,'            MetaDetailsScreen(\n','''            MetaDetailsScreen(
                petalixServiceId = savedState.get<String>("petalixServiceId"),
''','detail service argument')
    p.write_text(s)
    p=source/'ui/screens/detail/MetaDetailsScreen.kt';s=p.read_text()
    s=once(s,'fun MetaDetailsScreen(\n','fun MetaDetailsScreen(\n    petalixServiceId: String? = null,\n','detail signature')
    s=once(s,'                MetaDetailsContent(\n','''                val serviceExperience = petalixServiceId?.let(com.nuvio.tv.petalix.ui2.PetalixServices::find)
                if(serviceExperience!=null && !uiState.isTrailerPlaying && !uiState.isSharedTrailerOverlayVisible) {
                    com.nuvio.tv.petalix.ui2.PetalixServiceDetails(
                        service=serviceExperience,state=uiState,
                        playLabel=if(isSeries) { if(uiState.nextToWatch!=null) "Continue watching" else "Watch episode" } else "Watch movie",
                        canPlay=if(playOnLoadVideo!=null)playbackAvailability.canStream(meta.apiType,playOnLoadVideo.id,meta.id,playOnLoadVideo)
                            else playbackAvailability.canStream(meta.apiType,meta.id,meta.id),
                        onPlay={ if(isSeries && playOnLoadVideo!=null)playEpisode(playOnLoadVideo) else if(!isSeries)playTitle(meta.id)
                            else Toast.makeText(context,"No available episodes for this title.",Toast.LENGTH_SHORT).show() },
                        onEpisode=playEpisode,
                        onSeason={viewModel.onEvent(MetaDetailsEvent.OnSeasonSelected(it))},
                        onLibrary={viewModel.onEvent(MetaDetailsEvent.OnToggleLibrary)},
                        onBack=onBackPress,returnSeason=returnFocusSeason,returnEpisode=returnFocusEpisode,
                        onReturnConsumed=onReturnFocusConsumed)
                    return@Box
                }
                MetaDetailsContent(
''','service detail presentation with existing guarded playback')
    p.write_text(s)
    p=ui/'PetalixServiceMotion.kt';s=p.read_text()
    a=s.index('    LaunchedEffect(focused) {');b=s.index('    PetalixTile(',a)
    s=s[:a]+s[b:]
    s=s.replace('modifier.width(148.dp).height(78.dp)','modifier.width(234.dp).height(132.dp)')
    s=s.replace('onClick = { PetalixServiceAudio.play(context, service.id); onClick() }','onClick = onClick')
    p.write_text(s)

SEARCH_VIEWMODEL='''    private val searchEngine=PetalixServiceSearch(tmdbApi)
    private val _searchState=MutableStateFlow(ServiceSearchState())
    val searchState=_searchState.asStateFlow()
    private var searchJob:kotlinx.coroutines.Job?=null
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
'''
