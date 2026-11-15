package com.nuvio.tv.petalix

import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.AddonStreams
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.livetv.ondemand.OnDemandStreams
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart

/** A fast addon cannot win before the provider's lookup answers; a ready VOD link starts early. */
internal fun petalixVodFirstStreams(
    source: Flow<NetworkResult<List<AddonStreams>>>,
    provider: Flow<Pair<List<Stream>, String>?>
): Flow<NetworkResult<List<AddonStreams>>> = combine(
    source.onStart { emit(NetworkResult.Loading) }, provider
) { result, providerResult ->
    if (providerResult == null) return@combine NetworkResult.Loading
    val (links, position) = providerResult
    if (links.isEmpty()) return@combine result
    val mine = AddonStreams(OnDemandStreams.GROUP_NAME, null, links)
    val other = (result as? NetworkResult.Success<List<AddonStreams>>)?.data.orEmpty()
        .filterNot { it.addonName == OnDemandStreams.GROUP_NAME }
    val ordered = when (position) {
        "FIRST" -> listOf(mine) + other
        "AFTER_FIRST" -> other.take(1) + mine + other.drop(1)
        else -> other + mine
    }
    NetworkResult.Success(ordered)
}

/** Rank only the chosen provider's links. Manual selection keeps every original stream. */
internal fun balancedPetalixStream(streams: List<Stream>): Stream? = streams.withIndex()
    .minWithOrNull(compareBy<IndexedValue<Stream>>(
        { playbackCost(it.value) },
        { it.value.behaviorHints?.videoSize?.takeIf { size -> size > 0 } ?: Long.MAX_VALUE },
        { it.index }
    ))?.value

private val resolutionLabel = Regex("(?i)(?<![a-z0-9])(2160|1080|720|576|480|360)[pi](?![a-z0-9])")
private val uhdLabel = Regex("(?i)(?<![a-z0-9])(?:4k|uhd|2160)(?![a-z0-9])")
private val remuxLabel = Regex("(?i)(?<![a-z0-9])remux(?![a-z0-9])")

private fun playbackCost(stream: Stream): Int {
    val text = listOfNotNull(stream.quality, stream.name, stream.title, stream.description,
        stream.behaviorHints?.filename, stream.clientResolve?.stream?.raw?.parsed?.resolution)
        .joinToString(" ").take(16_384)
    val resolution = stream.qualityValue.takeIf { it in setOf(2160, 1080, 720, 576, 480, 360) }
        ?: resolutionLabel.find(text)?.groupValues?.get(1)?.toIntOrNull()
        ?: if (uhdLabel.containsMatchIn(text)) 2160 else null
    val cost = when (resolution) {
        1080 -> 0
        720 -> 1
        null -> 2
        576, 480, 360 -> 3
        else -> 4
    }
    return cost + if (remuxLabel.containsMatchIn(text)) 10 else 0
}
