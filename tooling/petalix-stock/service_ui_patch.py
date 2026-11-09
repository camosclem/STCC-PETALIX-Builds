"""Port the recovered Petalix 8.3 service interiors onto native Nuvio + IPTV 13.1.

Route selection and the Home service row are installed by prepare.py. This module
only adds the recovered UI, provider-filtered metadata support, and presentation
over the existing guarded detail/playback actions. It never replaces the guide,
settings, activation, or playback engine.
"""
from pathlib import Path
import shutil


def once(path: Path, old: str, new: str) -> None:
    source = path.read_text()
    if source.count(old) != 1:
        raise RuntimeError(f"Petalix service integration changed: {path.name}: {old[:70]!r}")
    path.write_text(source.replace(old, new))


def apply(root: Path) -> None:
    root = Path(root)
    source = root / "app/src/main/java/com/nuvio/tv"
    ui = source / "petalix/ui2"
    ui.mkdir(parents=True, exist_ok=True)
    for path in (Path(__file__).parent / "ui2").glob("*.kt"):
        shutil.copyfile(path, ui / path.name)

    api = source / "data/remote/api/TmdbApi.kt"
    once(api, "interface TmdbApi {", """interface TmdbApi {
    @GET("search/{media}")
    suspend fun petalixSearch(@Path("media") media:String,@Query("api_key") key:String,
        @Query("query") query:String,@Query("page") page:Int,
        @Query("include_adult") adult:Boolean=false):Response<okhttp3.ResponseBody>
    @GET("{media}/{id}/watch/providers")
    suspend fun petalixProviders(@Path("media") media:String,@Path("id") id:Int,
        @Query("api_key") key:String):Response<okhttp3.ResponseBody>
""")

    # Retain the original service filter while leaving other native collections'
    # default discovery behavior intact.
    once(source / "domain/model/Collection.kt",
         "val withoutWatchProviders: String? = null",
         "val withoutWatchProviders: String? = null,\n    val watchMonetizationTypes: String? = null")
    store = source / "data/local/CollectionsDataStore.kt"
    once(store, "val withoutWatchProviders: String? = null",
         "val withoutWatchProviders: String? = null,\n        val watchMonetizationTypes: String? = null")
    text = store.read_text()
    old = "withoutWatchProviders = withoutWatchProviders"
    if text.count(old) != 2:
        raise RuntimeError("Petalix service filter serialization requires review")
    store.write_text(text.replace(old, old + ",\n        watchMonetizationTypes = watchMonetizationTypes"))
    resolver = source / "core/tmdb/TmdbCollectionSourceResolver.kt"
    text = resolver.read_text()
    old = 'if (!filters.withWatchProviders.isNullOrBlank()) "flatrate|free|ads|rent|buy" else null'
    if text.count(old) != 2:
        raise RuntimeError("Petalix service provider filtering requires review")
    resolver.write_text(text.replace(old,
        'if (!filters.withWatchProviders.isNullOrBlank()) filters.watchMonetizationTypes ?: "flatrate|free|ads|rent|buy" else null'))

    details = source / "ui/screens/detail/MetaDetailsScreen.kt"
    once(details, "fun MetaDetailsScreen(\n",
         "fun MetaDetailsScreen(\n    petalixServiceId: String? = null,\n")
    once(details, "                MetaDetailsContent(\n", """                val serviceExperience = petalixServiceId?.let(com.nuvio.tv.petalix.ui2.PetalixServices::find)
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
                } else MetaDetailsContent(
""")
