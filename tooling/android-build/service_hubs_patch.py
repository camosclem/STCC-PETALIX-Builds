"""Service-first landing, complete branded provider hubs, and stable async rows."""
import json,pathlib,shutil
from playlist_patch import replace_once

def apply(work):
    root=work/'app/src/main/java/com/nuvio/tv'
    asset=work/'app/src/main/assets/managed-native-controls.json';controls=json.loads(asset.read_text())
    controls.append({'key':'pref:layout_settings:service_hub_deduplicate','feature':'layout_settings','preference':'service_hub_deduplicate','kind':'boolean','type':'boolean','optional':True})
    asset.write_text(json.dumps(controls,indent=2)+'\n')
    coordinator=root/'petalix/ManagedCoordinator.kt'
    replace_once(coordinator,'"device-updates-v1"','"device-updates-v1","service-hubs-v1"')
    policy=root/'petalix/ManagedPolicy.kt'
    replace_once(policy,'    @Synchronized fun initialize(context:Context) {','    @Synchronized fun initialize(context:Context) {\n        ServiceHubPolicy.initialize(context)')
    replace_once(policy,'        state.value=next\n    }','        state.value=next\n        ServiceHubPolicy.save(context,values)\n    }')
    content=root/'ui/screens/home/ModernHomeContent.kt'
    replace_once(content,'    val effectiveAutoplayEnabled =\n        uiState.focusedPosterBackdropTrailerEnabled &&','''    val serviceLanding = uiState.homeRows.any { it is HomeRow.CollectionRow && it.collection.id == "collection-streaming-services-only" }
    val effectiveAutoplayEnabled =
        !serviceLanding && uiState.focusedPosterBackdropTrailerEnabled &&''')
    replace_once(content,'val collectionHeroVideoUrl = collectionHeroVideoUrlState.value','val collectionHeroVideoUrl = collectionHeroVideoUrlState.value.takeUnless { serviceLanding }')
    vm=root/'ui/screens/collection/FolderDetailViewModel.kt'
    replace_once(vm,'import kotlinx.coroutines.Job','import kotlinx.coroutines.Job\nimport kotlinx.coroutines.sync.Mutex\nimport kotlinx.coroutines.sync.withLock')
    replace_once(vm,'data class FolderDetailUiState(','data class FolderDetailUiState(\n    val isServiceHub:Boolean=false,')
    replace_once(vm,'MutableStateFlow(FolderDetailUiState())','MutableStateFlow(FolderDetailUiState(isServiceHub=collectionId=="collection-streaming-services-only"))')
    replace_once(vm,'    private var movieWatchedJob: Job? = null','''    private var presentationJob:Job?=null
    private val presentationGeneration=java.util.concurrent.atomic.AtomicLong(0)
    private val presentationMutex=Mutex()
    private var movieWatchedJob: Job? = null''')
    replace_once(vm,'        posterOptions.bind(viewModelScope)','''        posterOptions.bind(viewModelScope)
        if(collectionId=="collection-streaming-services-only")viewModelScope.launch {
            com.nuvio.tv.petalix.ServiceHubPolicy.deduplicate.collect { rebuildFollowLayoutState() }
        }''')
    replace_once(vm,'        val allRows = if (useShimmer) {','        val sourceRows = if (useShimmer) {')
    replace_once(vm,'        if (allRows.isEmpty()) return','''        val allRows=if(state.isServiceHub)
            com.nuvio.tv.petalix.uniqueServiceRows(sourceRows,{it.isLoading},{it.items},{"${it.apiType}:${it.id}"},{row,items->row.copy(items=items)},com.nuvio.tv.petalix.ServiceHubPolicy.deduplicate.value) else sourceRows
        val generation=presentationGeneration.incrementAndGet()
        presentationJob?.cancel()
        if (allRows.isEmpty()) { _uiState.update { it.copy(followLayoutHomeState=null) };return }''')
    replace_once(vm,'            viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {','''            presentationJob=viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
              presentationMutex.withLock {
                if(generation!=presentationGeneration.get())return@withLock''')
    replace_once(vm,'                _uiState.update { s ->\n                    val homeState = HomeUiState(','                _uiState.update { s ->\n                    if(generation!=presentationGeneration.get())return@update s\n                    val homeState = HomeUiState(')
    replace_once(vm,'                scheduleMdbBatchPrefetch()\n            }\n        } else {','                scheduleMdbBatchPrefetch()\n              }\n            }\n        } else {')
    screen=root/'ui/screens/collection/FolderDetailScreen.kt'
    replace_once(screen,'import androidx.compose.foundation.ExperimentalFoundationApi','import androidx.compose.foundation.background\nimport androidx.compose.foundation.ExperimentalFoundationApi')
    replace_once(screen,'        HomeLayout.MODERN -> ModernHomeContent(','        HomeLayout.MODERN -> Box(modifier=Modifier.fillMaxSize()) {\n          ModernHomeContent(')
    replace_once(screen,'            blockLeftOnFirstExpandedItem = true\n        )','''            blockLeftOnFirstExpandedItem = true
          )
          if(uiState.isServiceHub)Row(modifier=Modifier.align(Alignment.TopStart).padding(start=58.dp,top=16.dp).background(Color.Black.copy(alpha=0.5f),RoundedCornerShape(10.dp)).padding(horizontal=14.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
            AsyncImage(model=uiState.folder?.titleLogoUrl?:uiState.folder?.coverImageUrl,contentDescription=null,modifier=Modifier.width(100.dp).height(28.dp),contentScale=ContentScale.Fit)
            Spacer(modifier=Modifier.width(12.dp))
            Text(text=uiState.folder?.title.orEmpty(),style=MaterialTheme.typography.labelMedium,color=Color.White)
          }
        }''')
    # Keep rental/purchase-only results out of subscription/free service hubs.
    model=root/'domain/model/Collection.kt'
    replace_once(model,'val withoutWatchProviders: String? = null','val withoutWatchProviders: String? = null,\n    val watchMonetizationTypes: String? = null')
    store=root/'data/local/CollectionsDataStore.kt';text=store.read_text()
    if text.count('val withoutWatchProviders: String? = null')!=1 or text.count('withoutWatchProviders = withoutWatchProviders')!=2:raise RuntimeError('TMDB filter serialization needs review')
    text=text.replace('val withoutWatchProviders: String? = null','val withoutWatchProviders: String? = null,\n        val watchMonetizationTypes: String? = null').replace('withoutWatchProviders = withoutWatchProviders','withoutWatchProviders = withoutWatchProviders,\n        watchMonetizationTypes = watchMonetizationTypes')
    store.write_text(text)
    resolver=root/'core/tmdb/TmdbCollectionSourceResolver.kt';text=resolver.read_text();old='if (!filters.withWatchProviders.isNullOrBlank()) "flatrate|free|ads|rent|buy" else null'
    if text.count(old)!=2:raise RuntimeError('TMDB monetization binding needs review')
    resolver.write_text(text.replace(old,'if (!filters.withWatchProviders.isNullOrBlank()) filters.watchMonetizationTypes ?: "flatrate|free|ads|rent|buy" else null'))
    local=pathlib.Path(__file__).parent
    for name in ['ServiceHubRows.kt','ServiceHubPolicy.kt']:shutil.copyfile(local/name,root/'petalix'/name)
    tests=work/'app/src/test/java/com/nuvio/tv/petalix';tests.mkdir(parents=True,exist_ok=True);shutil.copyfile(local/'ServiceHubRowsTest.kt',tests/'ServiceHubRowsTest.kt')
