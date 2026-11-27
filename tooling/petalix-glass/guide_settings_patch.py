"""Keep device guide choices across concurrent writes and managed settings deliveries."""
from pathlib import Path
from shutil import copy2

HERE = Path(__file__).resolve().parent


def apply(root: Path):
    java = root / "app/src/main/java/com/nuvio/tv"
    path = java / "livetv/data/LiveTvPreferences.kt"
    old = '''    suspend fun updateSettings(transform: (LiveTvSettings) -> LiveTvSettings) {
        val next = transform(currentSettings())
        store.edit { p -> writeSettings(p, next) }
    }'''
    new = '''    suspend fun updateSettings(transform: (LiveTvSettings) -> LiveTvSettings) =
        updateGuideSettings(false, transform)

    suspend fun updateUserSettings(transform: (LiveTvSettings) -> LiveTvSettings) =
        updateGuideSettings(true, transform)

    private suspend fun updateGuideSettings(userEdit: Boolean, transform: (LiveTvSettings) -> LiveTvSettings) {
        // Decode and transform inside the same transaction: simultaneous saves cannot
        // restore an old copy of another setting (for example Compact header).
        store.edit { p ->
            val current = buildSettings(withMyMenu(p, currentProfile))
            val before = p.toMutablePreferences()
            writeSettings(before, current)
            val after = before.toMutablePreferences()
            writeSettings(after, transform(current))
            val changed = com.nuvio.tv.petalix.guideSettingChanges(
                before.asMap().mapKeys { it.key.name }, after.asMap().mapKeys { it.key.name })
            val localKey = stringSetPreferencesKey(com.nuvio.tv.petalix.GUIDE_LOCAL_CHOICES)
            val local = p[localKey].orEmpty()
            val writable = com.nuvio.tv.petalix.guideWritableSettings(changed, local, userEdit)
            for (key in before.asMap().keys + after.asMap().keys) {
                if (key.name !in writable) continue
                @Suppress("UNCHECKED_CAST")
                val typed = key as Preferences.Key<Any>
                val value = after[typed]
                if (value == null) p.remove(typed) else p[typed] = value
            }
            if (userEdit) p[localKey] = local + changed
        }
    }'''
    source = path.read_text()
    if source.count(old) != 1:
        raise ValueError("4172 preference transaction anchor changed")
    source = source.replace(old, new)
    source = source.replace("import androidx.datastore.preferences.core.edit", "import androidx.datastore.preferences.core.toMutablePreferences\nimport androidx.datastore.preferences.core.edit", 1)
    prepared = {path: source}
    for name in ("LiveTvViewModel.kt", "LiveTvSettingsViewModel.kt"):
        target = java / "livetv/ui" / name
        text = target.read_text()
        if "prefs.updateSettings" not in text:
            raise ValueError(f"Guide user settings anchor changed: {name}")
        prepared[target] = text.replace("prefs.updateSettings", "prefs.updateUserSettings")
    target = java / "petalix/ManagedNativeSettings.kt"
    old = '''    for(s in specs) {
        if(values.has(s.key)) {'''
    new = '''    val localGuideKeys = old[stringSetPreferencesKey(GUIDE_LOCAL_CHOICES)].orEmpty()
    for(s in specs) {
        // Both mapped panel controls and raw controls use this same preference name.
        if (s.feature == "live_tv" && s.preference in localGuideKeys) {
            before.remove(s.key)
            after.remove(s.key)
            continue
        }
        if(values.has(s.key)) {'''
    text = target.read_text()
    if text.count(old) != 1:
        raise ValueError("Managed native preference anchor changed")
    prepared[target] = text.replace(old, new, 1)
    for target, text in prepared.items():
        target.write_text(text)
    copy2(HERE / "GuideSettingsPolicy.kt", java / "petalix/GuideSettingsPolicy.kt")
    tests = root / "app/src/test/java/com/nuvio/tv/petalix"
    tests.mkdir(parents=True, exist_ok=True)
    copy2(HERE / "GuideSettingsPolicyTest.kt", tests / "GuideSettingsPolicyTest.kt")
