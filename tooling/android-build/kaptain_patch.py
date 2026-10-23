"""Add the independent Kaptain manifest option to the shared managed app source."""
import json,re
from playlist_patch import replace_once

def apply(work):
    root=work/'app/src/main/java/com/nuvio/tv'
    asset=work/'app/src/main/assets/managed-native-controls.json'
    controls=json.loads(asset.read_text())
    if any(c['key']=='addon:kaptain' for c in controls):raise RuntimeError('Kaptain manifest integration already present')
    controls.append({'key':'addon:kaptain','feature':'managed_addons','preference':'kaptain_manifest','kind':'string','type':'addon','maxLength':4000,'optional':True})
    asset.write_text(json.dumps(controls,indent=2)+'\n')
    addons=root/'data/local/AddonPreferences.kt'
    text=addons.read_text()
    method=re.search(r'    suspend fun applyManagedCncverse\(manifest:String\?\) \{.*?\n    \}',text,re.S)
    if not method:raise RuntimeError('Managed addon implementation needs review')
    extra=method.group(0).replace('applyManagedCncverse','applyManagedKaptain').replace('managed_cncverse_original','managed_kaptain_original')
    addons.write_text(text[:method.start()]+extra+'\n\n'+text[method.start():])
    native=root/'petalix/ManagedNativeSettings.kt'
    replace_once(native,'        if(values.has("addon:cncverse")) {','''        if(values.has("addon:kaptain")) {
            val url=values.getString("addon:kaptain").toHttpUrlOrNull()
            require(url!=null&&url.isHttps&&url.username.isEmpty()&&url.password.isEmpty()&&url.fragment==null&&url.encodedPath.endsWith("/manifest.json",true)) { "Invalid Kaptain manifest URL" }
        }
        if(values.has("addon:cncverse")) {''')
    needle='        addons.applyManagedCncverse(if(values.has("addon:cncverse"))values.getString("addon:cncverse") else null)'
    replace_once(native,needle,needle+'\n        addons.applyManagedKaptain(if(values.has("addon:kaptain"))values.getString("addon:kaptain") else null)')
    replace_once(root/'petalix/ManagedCoordinator.kt','"playlist-v1"','"playlist-v1","kaptain-manifest-v1"')
