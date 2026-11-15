package com.nuvio.tv.petalix

import com.nuvio.tv.livetv.ondemand.OnDemandStatus

/** Ending the spinner must not erase the provider's failure message. */
internal fun finishPetalixVodLoad(status: OnDemandStatus): OnDemandStatus = status.copy(loading = false)

/** A single recovery for the software AV1 renderer; unrelated failures retain native policy. */
internal fun petalixSoftwareAv1Recovery(decoderInitFailed: Boolean, detail: String): Boolean =
    decoderInitFailed && detail.contains("Libgav1VideoRenderer", ignoreCase = true)
