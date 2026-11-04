"""Use the Petalix ZIP/nearby-market station picker in the signed test app."""
from pathlib import Path
import shutil

def apply(work):
    source=Path(__file__).parent/'PetalixLocalMarkets.kt'
    target=Path(work)/'app/src/main/java/com/nuvio/tv/petalix/ui2/PetalixLocalChannels.kt'
    if not target.is_file() or 'fun PetalixLocalChannelsDialog' not in target.read_text():
        raise RuntimeError('Petalix locals source missing')
    shutil.copyfile(source,target)
    prefs=Path(work)/'app/src/main/java/com/nuvio/tv/livetv/data/LiveTvPreferences.kt'
    text=prefs.read_text()
    anchor='    suspend fun petalixLocalZip():String = store.data.first()[my(stringPreferencesKey("petalix_local_zip"))].orEmpty()'
    if text.count(anchor)!=1:raise RuntimeError('Petalix local preferences anchor changed')
    text=text.replace(anchor,anchor+'''
    suspend fun petalixLocalKeys():List<String> {
        val groups=decodeCustomGroups(store.data.first()[my(Keys.customGroups,currentProfile)])
        return groups.firstOrNull{it.id==com.nuvio.tv.livetv.model.ChannelGroup.CUSTOM_PREFIX+"petalix_locals"}?.channelKeys.orEmpty()
    }
''')
    prefs.write_text(text)
