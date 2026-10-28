package com.nuvio.tv.petalix

import org.junit.Assert.*
import org.junit.Test

class PenguPlayAccessTest {
    private val pengu = listOf("https://pengu.example/config/secret")
    private val alias = pengu.single() + "/manifest.json"
    private val other = "https://other.example/manifest.json"

    @Test fun disabledRemovesOnlyPenguIncludingImportedAliases() {
        assertEquals(listOf(other), PenguPlayAccess.urls(listOf(alias, other, pengu.single()), pengu, false, true))
        assertEquals(listOf(other), PenguPlayAccess.urls(listOf(alias, other), pengu, false, false))
        val states = PenguPlayAccess.states(mapOf(alias to true, other to true), pengu, false, true)
        assertEquals(false, states[alias]); assertEquals(false, states[pengu.single()]); assertEquals(true, states[other])
    }
    @Test fun enableRestoresExactlyOneBundledSource() {
        assertEquals(pengu + other, PenguPlayAccess.urls(listOf(alias, other), pengu, true, true))
        assertEquals(pengu + other, PenguPlayAccess.urls(listOf(other), pengu, true, true))
        assertEquals(listOf(other), PenguPlayAccess.urls(listOf(other), pengu, true, false))
        assertEquals(true, PenguPlayAccess.states(mapOf(alias to false), pengu, true, true)[alias])
    }
    @Test fun canonicalizationKeepsConfiguredQueriesSeparate() {
        assertTrue(PenguPlayAccess.matches("https://pengu.example/config/secret/MANIFEST.JSON/?key=a", listOf(pengu.single()+"?key=a")))
        assertFalse(PenguPlayAccess.matches(pengu.single()+"?key=b", listOf(pengu.single()+"?key=a")))
        assertFalse(PenguPlayAccess.matches(other, pengu))
    }
    @Test fun repeatedRefreshesStayDisabledAcrossProfiles() {
        for (profile in listOf(listOf(alias, other), listOf(pengu.single()), emptyList())) {
            val once = PenguPlayAccess.urls(profile, pengu, false, true)
            assertEquals(once, PenguPlayAccess.urls(once, pengu, false, true))
            assertFalse(once.any { PenguPlayAccess.matches(it, pengu) })
        }
    }
}
