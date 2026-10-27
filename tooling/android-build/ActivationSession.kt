package com.nuvio.tv.petalix

import java.time.Instant
import kotlinx.coroutines.CancellationException

/** Verification age schedules a check; it is not the customer's access expiry. */
internal object ActivationSession {
    fun ready(hasToken:Boolean,expires:String?,now:Long):Boolean = hasToken &&
        runCatching { Instant.parse(expires).toEpochMilli()>now }.getOrDefault(false)

    /** A missed delivery receipt must not undo an otherwise saved activation. */
    suspend fun acknowledge(send:suspend ()->Unit) {
        try { send() }
        catch(e:ActivationRequired) { throw e }
        catch(e:CancellationException) { throw e }
        catch(_:Exception) { /* The next heartbeat retries the receipt. */ }
    }
}
