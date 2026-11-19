package com.nuvio.tv.livetv.ui

import com.nuvio.tv.livetv.model.EpgProgram

internal const val PETALIX_HALF_HOUR = 30 * 60_000L

internal data class PetalixAiring(val start: Long, val end: Long, val program: EpgProgram?) {
    fun contains(time: Long) = time >= start && time < end
}

/** The ruler, remote cursor, pointer targets and channel actions use the same intervals. */
internal class PetalixAirtimes(programs: List<EpgProgram>) {
    private val shows: List<PetalixAiring> = buildList {
        var occupiedUntil = Long.MIN_VALUE
        for (program in programs.sortedBy { it.startMs }) {
            val start = maxOf(occupiedUntil, program.startMs)
            if (start >= program.stopMs) continue
            add(PetalixAiring(start, program.stopMs, program))
            occupiedUntil = program.stopMs
        }
    }

    fun at(time: Long): PetalixAiring {
        val index = shows.binarySearchBy(time) { it.start }
        if (index >= 0) return shows[index]
        val next = -index - 1
        val previous = shows.getOrNull(next - 1)
        if (previous != null && previous.contains(time)) return previous
        val following = shows.getOrNull(next)
        if (previous != null && following != null) return PetalixAiring(previous.end, following.start, null)
        val start = halfHour(time)
        return PetalixAiring(maxOf(start, previous?.end ?: start),
            minOf(start + PETALIX_HALF_HOUR, shows.getOrNull(next)?.start ?: Long.MAX_VALUE), null)
    }

    fun visible(start: Long, end: Long): List<PetalixAiring> = buildList {
        var time = start
        while (time < end) {
            val airing = at(time)
            add(airing)
            time = airing.end
        }
    }

    fun step(from: Long, steps: Int, earliest: Long, latest: Long): Long {
        var time = from.coerceIn(earliest, latest)
        repeat(kotlin.math.abs(steps)) {
            val show = at(time)
            time = (if (steps > 0) show.end else show.start - 1L).coerceIn(earliest, latest)
        }
        return time
    }
}

internal fun halfHour(time: Long) = time - Math.floorMod(time, PETALIX_HALF_HOUR)

internal fun petalixWindowFor(time: Long, start: Long, duration: Long): Long = when {
    time < start -> halfHour(time)
    time >= start + duration -> halfHour(time) - duration + PETALIX_HALF_HOUR
    else -> start
}
