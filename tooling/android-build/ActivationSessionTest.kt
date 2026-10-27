package com.nuvio.tv.petalix

import java.io.IOException
import java.net.SocketTimeoutException
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ActivationSessionTest {
    private val activated=Instant.parse("2026-10-01T12:00:00Z").toEpochMilli()
    private val expires="2026-11-01T12:00:00Z"
    private val day=86_400_000L

    @Test fun savedActivationStillOpensAfterTheOldDailyCutoff() {
        listOf(day-1,day,day+1,2*day,14*day).forEach { elapsed ->
            assertTrue("Saved access at $elapsed ms",ActivationSession.ready(true,expires,activated+elapsed))
        }
    }
    @Test fun subscriptionExpiryStillStopsCachedAccess() {
        val expiry=Instant.parse(expires).toEpochMilli()
        assertTrue(ActivationSession.ready(true,expires,expiry-1))
        assertFalse(ActivationSession.ready(true,expires,expiry))
        assertFalse(ActivationSession.ready(true,expires,expiry+day))
    }
    @Test fun missingTokenOrUnusableExpiryCannotGrantCachedAccess() {
        assertFalse(ActivationSession.ready(false,expires,activated))
        listOf(null,"","invalid","2020-01-01T00:00:00Z").forEach {
            assertFalse(ActivationSession.ready(true,it,activated))
        }
    }
    @Test fun renewalCanUseTheExistingTokenAndNewExpiry() {
        val afterExpiry=Instant.parse(expires).toEpochMilli()+day
        assertFalse(ActivationSession.ready(true,expires,afterExpiry))
        assertTrue(ActivationSession.ready(true,"2026-12-01T12:00:00Z",afterExpiry))
    }
    @Test fun missedReceiptDoesNotMakeSuccessfulActivationFail() = runTest {
        listOf(IOException("Offline"),SocketTimeoutException("Timeout"),IllegalStateException("Service unavailable")).forEach { failure ->
            ActivationSession.acknowledge { throw failure }
        }
        var sent=0
        ActivationSession.acknowledge { sent++ }
        assertEquals(1,sent)
    }
    @Test fun rejectedAccessAndCancellationAreNeverSwallowed() = runTest {
        val rejected=ActivationRequired()
        try { ActivationSession.acknowledge { throw rejected };fail("Rejected access was ignored") }
        catch(e:ActivationRequired) { assertSame(rejected,e) }
        val cancelled=CancellationException("Stopped")
        try { ActivationSession.acknowledge { throw cancelled };fail("Cancellation was ignored") }
        catch(e:CancellationException) { assertSame(cancelled,e) }
    }
}
