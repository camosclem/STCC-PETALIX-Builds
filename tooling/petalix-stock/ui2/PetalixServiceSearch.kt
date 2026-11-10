package com.nuvio.tv.petalix.ui2

import com.nuvio.tv.petalix.PetalixRuntimeConfig
import com.nuvio.tv.data.remote.api.TmdbApi
import com.nuvio.tv.domain.model.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject

/** Search the remote catalog, then independently check service availability for every result.
 * A search result is metadata, never proof that the customer's playback source carries it. */
data class ServiceSearchState(val query:String="", val items:List<MetaPreview> = emptyList(),
    val loading:Boolean=false, val page:Int=0, val more:Boolean=false, val error:String?=null)
class PetalixServiceSearch(private val api:TmdbApi) {
    private val providers=linkedMapOf<String,Set<String>>()
    private val requests=Semaphore(4)
    suspend fun search(service:PetalixService,query:String,page:Int):ServiceSearchState = coroutineScope {
        check(PetalixRuntimeConfig.apiKey.isNotBlank()) { "Reconnect to Petalix in Settings before searching." }
        val batches=listOf("movie","tv").map { type -> async {
            val response=api.petalixSearch(type,PetalixRuntimeConfig.apiKey,query,page)
            check(response.isSuccessful){"Search is temporarily unavailable (${response.code()})."}
            type to JSONObject(response.body()?.string() ?: error("Empty search response"))
        }}.awaitAll()
        val candidates=batches.flatMap { (type,json) ->
            val results=json.optJSONArray("results")
            (0 until (results?.length() ?: 0)).map { type to results!!.getJSONObject(it) }
        }
        val ids=service.provider.split('|').toSet()
        val matches=candidates.map { (type,item) -> async {
            requests.withPermit {
                val id=item.getInt("id")
                val key="$type:$id"
                val available=synchronized(providers){providers[key]} ?: run {
                    val response=api.petalixProviders(type,id,PetalixRuntimeConfig.apiKey)
                    check(response.isSuccessful){"Service availability could not be verified. Please retry."}
                    val country=JSONObject(response.body()?.string() ?: error("Empty availability response"))
                        .optJSONObject("results")?.optJSONObject("US")
                    val found=mutableSetOf<String>()
                    listOf("flatrate","free","ads").forEach { offer ->
                        val entries=country?.optJSONArray(offer)
                        for(i in 0 until (entries?.length() ?: 0)) found.add(entries!!.getJSONObject(i).getInt("provider_id").toString())
                    }
                    synchronized(providers) {
                        if(providers.size>=500)providers.remove(providers.keys.first())
                        providers[key]=found
                    }
                    found
                }
                if(available.none { it in ids }) null else {
                    fun value(name:String)=item.optString(name).takeIf { it.isNotBlank() && it!="null" }
                    fun art(name:String,size:String)=value(name)?.let { "https://image.tmdb.org/t/p/$size$it" }
                    val series=type=="tv"
                    MetaPreview(id="tmdb:$id",type=if(series)ContentType.SERIES else ContentType.MOVIE,
                        rawType=if(series)"series" else "movie",name=value(if(series)"name" else "title").orEmpty(),
                        poster=art("poster_path","w500"),posterShape=PosterShape.POSTER,
                        background=art("backdrop_path","w1280"),logo=null,description=value("overview"),
                        releaseInfo=value(if(series)"first_air_date" else "release_date")?.take(4),
                        imdbRating=item.optDouble("vote_average",0.0).toFloat(),genres=emptyList())
                }
            }
        }}.awaitAll().filterNotNull()
        ServiceSearchState(query,matches,loading=false,page=page,
            more=batches.any { (_,json)->page<json.optInt("total_pages",1).coerceAtMost(500) })
    }
}
