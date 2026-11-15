package com.nuvio.tv.petalix

import com.nuvio.tv.livetv.ondemand.OnDemandStatus

/** Ending the spinner must not erase the provider's failure message. */
internal fun finishPetalixVodLoad(status: OnDemandStatus): OnDemandStatus = status.copy(loading = false)

/** A single recovery for the software AV1 renderer; unrelated failures retain native policy. */
internal fun petalixSoftwareAv1Recovery(decoderInitFailed: Boolean, detail: String): Boolean =
    decoderInitFailed && detail.contains("Libgav1VideoRenderer", ignoreCase = true)

/** Provider event names change daily while their stream IDs and saved favorites stay stable. */
internal fun petalixSportsChannelSignature(channels: List<com.nuvio.tv.livetv.model.LiveChannel>): Pair<Int, Long> {
    var fingerprint = 17L
    for (channel in channels) {
        fingerprint = fingerprint * 31 + channel.key.hashCode()
        fingerprint = fingerprint * 31 + channel.name.hashCode()
        fingerprint = fingerprint * 31 + channel.group.hashCode()
    }
    return channels.size to fingerprint
}
