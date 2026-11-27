"""Reduce empty-guide startup time on the preserved 4172 guide recipe."""
from pathlib import Path


def apply(root: Path):
    java = root / "app/src/main/java/com/nuvio/tv"
    changes = {
        java / "petalix/PetalixManagedCoordinator.kt": [(
            """        profiles.activeProfileReady.first { it }
        profiles.activeProfileId.collectLatest { profileId ->""",
            """        profiles.activeProfileReady.first { it }
        // Read saved channels while Home is on screen, before the guide is opened.
        // Fresh installs still fetch immediately; cached installs defer routine updates.
        repository.ensureLoaded(updateCheckDelayMs = 20_000L)
        profiles.activeProfileId.collectLatest { profileId ->""",
        )],
        java / "livetv/data/LiveTvRepository.kt": [(
            "if (updateCheckDelayMs > 0) delay(updateCheckDelayMs)",
            "if (updateCheckDelayMs > 0 && _channels.value.isNotEmpty()) delay(updateCheckDelayMs)",
        ), (
            """        if (_epgLogos.value.isEmpty()) _epgLogos.value = withContext(Dispatchers.IO) { readEpgLogos() }
        mutex.withLock { rebuildLocked() }""",
            """        // Channel rows do not depend on the optional saved logo index.
        mutex.withLock { rebuildLocked() }
        if (_epgLogos.value.isEmpty()) _epgLogos.value = withContext(Dispatchers.IO) { readEpgLogos() }""",
        )],
        java / "livetv/ui/LiveTvViewModel.kt": [(
            """    /** A playlist group's own channels plus channels copied into it. */
    fun groupChannels(groupId: String): List<LiveChannel> {
        val own = visible.filter { it.groupId == groupId }""",
            """    // Index once: merged categories must not scan the entire playlist per group.
    val visibleByGroup = visible.groupBy { it.groupId }
    /** A playlist group's own channels plus channels copied into it. */
    fun groupChannels(groupId: String): List<LiveChannel> {
        val own = visibleByGroup[groupId].orEmpty()""",
        )],
    }
    prepared = {}
    for path, replacements in changes.items():
        source = path.read_text()
        for old, new in replacements:
            if source.count(old) != 1:
                raise ValueError(f"4172 guide startup anchor changed: {path.name}: {old[:70]!r}")
            source = source.replace(old, new, 1)
        prepared[path] = source
    for path, source in prepared.items():
        path.write_text(source)


if __name__ == "__main__":
    import sys
    apply(Path(sys.argv[1]).resolve())
