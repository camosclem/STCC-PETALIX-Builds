package com.openguide.tv

import org.junit.Assert.*
import org.junit.Test

class FeedMatcherTest {
    @Test fun alternateFeedsMustBeSameChannel() {
        val primary=FeedMatcher.Feed("ESPN HD", "https://example.net/espn1")
        val input=listOf(
            FeedMatcher.Feed("ESPN 4K", "https://example.net/espn2"),
            FeedMatcher.Feed("ESPN 2", "https://example.net/espn-2"),
            FeedMatcher.Feed("NFL Network HD", "https://example.net/nfl"),
            FeedMatcher.Feed("ESPN HD", "https://example.net/espn1"))
        val result=FeedMatcher.candidates(primary,input)
        assertEquals(2,result.size)
        assertTrue(result.all {it.name.startsWith("ESPN")})
    }
    @Test fun refusesToPairGenericShortLabels() {
        assertEquals(1,FeedMatcher.candidates(FeedMatcher.Feed("TV","https://a"),listOf(FeedMatcher.Feed("TV","https://b"))).size)
    }
}
