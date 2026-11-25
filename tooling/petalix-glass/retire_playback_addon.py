"""Remove the retired playback source and clean up saved profile assignments."""

from pathlib import Path


def change(path, old, new):
    source = path.read_text()
    if source.count(old) != 1:
        raise ValueError(f"Unexpected retirement anchor in {path.name}")
    path.write_text(source.replace(old, new))


def remove_block(path, start, end, replacement):
    source = path.read_text()
    left = source.index(start)
    right = source.index(end, left)
    change(path, source[left:right], replacement)


def apply(root: Path):
    source = root / "app/src/main/java/com/nuvio/tv"
    preferences = source / "data/local/AddonPreferences.kt"
    remove_block(
        preferences,
        "    private val managedPenguKey",
        "    suspend fun applyManagedMetadata",
        '''    private val retiredAssignmentKey = stringPreferencesKey("managed_panel_pengu")
    internal val managedPenguUrl: Flow<String?> = kotlinx.coroutines.flow.flowOf(null)

    private fun isRetiredPlaybackUrl(url: String, assigned: String?): Boolean =
        canonicalizeUrl(url) == assigned ||
            url.lowercase().filter(Char::isLetterOrDigit).contains("penguplay")

    suspend fun removeRetiredPlaybackSource() {
        removeRetiredPlaybackSource(effectiveProfileIdFlow.first())
    }

    private suspend fun removeRetiredPlaybackSource(profileId: Int) {
        store(profileId).edit { preferences ->
            val assigned = preferences[retiredAssignmentKey]?.let(::canonicalizeUrl)
            val urls = getCurrentList(preferences)
            val retained = urls.filterNot { isRetiredPlaybackUrl(it, assigned) }
            if (retained != urls) {
                preferences[orderedUrlsKey] = gson.toJson(retained)
                preferences.remove(legacyUrlsKey)
            }
            val states = getCurrentEnabledStates(preferences)
            val retainedStates = states.filterKeys { !isRetiredPlaybackUrl(it, assigned) }
            if (retainedStates != states) preferences[addonEnabledStatesKey] = gson.toJson(retainedStates)
            preferences.remove(retiredAssignmentKey)
        }
    }

''',
    )
    change(preferences, "}.onStart { installBetterPosters(pid) }", '''}.onStart {
            removeRetiredPlaybackSource(pid)
            installBetterPosters(pid)
        }''')
    managed = source / "petalix/ManagedNativeSettings.kt"
    remove_block(managed, '        if (values.optBoolean("addon:penguplay")) {', "        values.keys()", "")
    remove_block(
        managed, "    suspend fun applyPenguAssignment", "    suspend fun apply(values:",
        "    suspend fun removeRetiredPlaybackSource() = addons.removeRetiredPlaybackSource()\n\n",
    )
    settings = source / "petalix/PetalixPanelSettings.kt"
    remove_block(settings, '        val penguKey =', '        if (stamp ==', '        native.removeRetiredPlaybackSource()\n')
    repo = source / "data/repository/StreamRepositoryImpl.kt"
    remove_block(repo, "                val penguMessage", "                val errorMessage", "")
    change(repo, "val errorMessage = penguMessage ?: buildAggregateFailureMessage(", "val errorMessage = buildAggregateFailureMessage(")
    coordinator = source / "petalix/PetalixManagedCoordinator.kt"
    change(coordinator, ', "pengu-play-panel-v1", "pengu-play-v1"', "")
