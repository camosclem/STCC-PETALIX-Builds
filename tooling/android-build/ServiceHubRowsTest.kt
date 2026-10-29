package com.nuvio.tv.petalix

import org.junit.Assert.*
import org.junit.Test

class ServiceHubRowsTest {
    data class Item(val id:String,val type:String)
    data class Row(val id:String,val items:List<Item>,val loading:Boolean=false)
    private fun dedupe(rows:List<Row>)=uniqueServiceRows(rows,{it.loading},{it.items},{"${it.type}:${it.id}"},{row,items->row.copy(items=items)})
    @Test fun repeatsAreRemovedAcrossProviderRowsWithoutMixingMoviesAndShows() {
        val movie=Item("10","movie");val show=Item("10","series")
        val original=listOf(Row("popular",listOf(movie)),Row("rated",listOf(movie,show,Item("20","movie"))))
        val next=dedupe(original)
        assertEquals(listOf(show,Item("20","movie")),next[1].items)
        assertEquals(3,original[1].items.size)
    }
    @Test fun loadingRowsDoNotReserveRealIdsAndEmptyRowsAreOmitted() {
        val item=Item("10","movie")
        val next=dedupe(listOf(Row("loading",listOf(item),true),Row("ready",listOf(item)),Row("duplicate",listOf(item))))
        assertEquals(listOf("loading","ready"),next.map{it.id})
    }
    @Test fun emptyRowsStayHiddenWhenDuplicateReductionIsOff() {
        val item=Item("10","movie")
        val next=uniqueServiceRows(listOf(Row("first",listOf(item)),Row("empty",emptyList()),Row("repeat",listOf(item))),{it.loading},{it.items},{"${it.type}:${it.id}"},{r,items->r.copy(items=items)},false)
        assertEquals(listOf("first","repeat"),next.map{it.id})
    }
    @Test fun homeKeepsServiceTilesAndViewingHistoryWhileRemovingRepeatedDiscoveryRows() {
        val rows=listOf("catalog_movie",STREAMING_SERVICES_ROW,"continue_watching","catalog_shows","collection_old","upcoming_section")
        assertEquals(listOf(STREAMING_SERVICES_ROW,"continue_watching","upcoming_section"),streamingServicesFirst(rows){it})
    }
}
