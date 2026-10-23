"""Apply the versioned shared playlist client to either saved managed brand source."""
import pathlib,re,shutil

def replace_once(path,old,new):
    text=path.read_text()
    if text.count(old)!=1:raise RuntimeError('Playlist integration needs review: '+path.name)
    path.write_text(text.replace(old,new))

def apply(work,brand):
    root=work/'app/src/main/java/com/nuvio/tv'
    matches=re.findall(r'PlaylistSource\(id="([^"]+)"', (root/'petalix/ManagedCoordinator.kt').read_text())
    if len(matches)!=1 or matches[0] not in {'petalix-managed','stcc-managed'}:raise RuntimeError('Managed playlist identity needs review')
    source=matches[0]
    local=pathlib.Path(__file__).resolve().parent
    shutil.copyfile(local/'PanelPlaylist.kt',root/'petalix/PanelPlaylist.kt')
    tests=work/'app/src/test/java/com/nuvio/tv/petalix';tests.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(local/'PanelPlaylistTest.kt',tests/'PanelPlaylistTest.kt')
    prefs=root/'livetv/data/LiveTvPreferences.kt'
    replace_once(prefs,'if (id <= 1) return p','if (id <= 1) return com.nuvio.tv.petalix.PanelPlaylist.view(p,"'+source+'")')
    replace_once(prefs,'        return m\n    }','        return com.nuvio.tv.petalix.PanelPlaylist.view(m,"'+source+'")\n    }')
    replace_once(prefs,'val settings: Flow<LiveTvSettings> = store.data.map { p ->','val settings: Flow<LiveTvSettings> = store.data.map { raw ->\n        val p=com.nuvio.tv.petalix.PanelPlaylist.view(raw,"'+source+'")')
    coordinator=root/'petalix/ManagedCoordinator.kt'
    replace_once(coordinator,'import android.content.Context','import androidx.datastore.preferences.core.edit\nimport android.content.Context')
    replace_once(coordinator,'"management-v2","management-v3"','"management-v2","management-v3","playlist-v1"')
    old='    private suspend fun acknowledge(){post("settings-ack",report(),token())}'
    new='''    private suspend fun acknowledge(){
        val data=report().put("playlistRevision",prefs.getString("playlistRevision",""))
        val channels=repository.channels.value.filter { it.sourceId=="SOURCE" }.take(10000)
        val array=JSONArray()
        for(c in channels)array.put(JSONObject().put("id",c.key.substringAfter('|')).put("name",c.name).put("group",c.group))
        val raw=array.toString()
        val digest=java.security.MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString(""){"%02x".format(it)}
        val send=channels.isNotEmpty()&&raw.length<1_500_000&&prefs.getString("playlistCatalogHash","")!=digest
        if(send)data.put("playlistCatalog",array)
        post("settings-ack",data,token())
        if(send)prefs.edit().putString("playlistCatalogHash",digest).commit()
    }'''.replace('SOURCE',source)
    replace_once(coordinator,old,new)
    needle='        if(changed)repository.reloadAfterSync(setOf("'+source+'"))'
    replacement='''        doc.optJSONObject("playlist")?.let { playlist ->
            live.managedPreferences().edit { PanelPlaylist.store(it,playlist) }
            check(prefs.edit().putString("playlistRevision",playlist.optString("revision")).commit())
        }
'''+needle
    replace_once(coordinator,needle,replacement)
