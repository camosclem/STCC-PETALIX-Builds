package com.nuvio.tv.petalix

/** Dedupe display rows without changing the source tabs or pagination state. */
internal fun <R, I> uniqueServiceRows(rows: List<R>, loading: (R) -> Boolean, items: (R) -> List<I>, key: (I) -> String, copy: (R, List<I>) -> R, deduplicate: Boolean = true): List<R> {
    val seen = HashSet<String>()
    return rows.mapNotNull { row ->
        if (loading(row)) row else {
            val unique = if (deduplicate) items(row).filter { seen.add(key(it)) } else items(row)
            if (unique.isEmpty()) null else copy(row, unique)
        }
    }
}
