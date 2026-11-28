"""Keep unassigned retired playback sources out of provider title resolution."""
from pathlib import Path
from shutil import copy2

HERE = Path(__file__).resolve().parent


def apply(root: Path):
    source = root / "app/src/main/java/com/nuvio/tv"
    repo = source / "data/repository/StreamRepositoryImpl.kt"
    text = repo.read_text()
    changes = [
        ('''        val usePengu = !isNativeServerRequest && !continuePluginOnly &&
            com.nuvio.tv.petalix.isPetalixVodType(type)''', '''        val usePengu = !isNativeServerRequest && !continuePluginOnly &&
            com.nuvio.tv.petalix.isPetalixVodType(type) &&
            !addonPreferences?.managedPenguUrl?.first().isNullOrBlank()'''),
        ('''            val installed = addonRepository.getInstalledAddons().first()''', '''            val installed = addonRepository.getInstalledAddons().first().filter {
                com.nuvio.tv.petalix.petalixPlaybackAddonAllowed(it.id, it.displayName, it.baseUrl, penguUrl)
            }'''),
        ('''                val penguMessage = if (usePengu && attemptedAddonNames.isEmpty()) when {
                    penguUrl == null -> "Pengu Play is not assigned. Check Settings → Account → Refresh account."
                    addons.none { addon -> addon.baseUrl == penguUrl && addon.resources.any { it.name == "stream" } } ->
                        "Could not load Pengu Play. Refresh your account and retry."
                    else -> "Pengu Play has no compatible source for this title."
                } else null''', '''                val penguMessage = if (attemptedAddonNames.isEmpty() &&
                    com.nuvio.tv.petalix.isPetalixVodType(type)) when {
                    !usePengu || penguUrl.isNullOrBlank() ->
                        "No playable source was found for this title. Refresh your playlist and try again."
                    addons.none { addon -> addon.baseUrl == penguUrl && addon.resources.any { it.name == "stream" } } ->
                        "Could not load the assigned playback source. Refresh your account and retry."
                    else -> "The assigned playback source has no compatible stream for this title."
                } else null'''),
    ]
    for old, new in changes:
        if text.count(old) != 1:
            raise ValueError("Playback source changed: " + old.splitlines()[0])
        text = text.replace(old, new)
    repo.write_text(text)
    copy2(HERE / "PetalixPlaybackSources.kt", source / "petalix/PetalixPlaybackSources.kt")
    copy2(HERE / "PetalixPlaybackSourcesTest.kt", root / "app/src/test/java/com/nuvio/tv/petalix/PetalixPlaybackSourcesTest.kt")
