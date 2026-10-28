"""Remove the built-in Pengu source. Only explicit panel assignments install it."""
import hashlib,json,os,pathlib,re,shutil
from playlist_patch import replace_once

def apply(work):
    root=work/'app/src/main/java/com/nuvio/tv'
    asset=work/'app/src/main/assets/managed-native-controls.json'
    controls=json.loads(asset.read_text())
    if any(c['key']=='addon:penguplay' for c in controls):raise RuntimeError('Pengu panel integration already present')
    controls.extend([
        {'key':'addon:penguplay','feature':'managed_addons','preference':'penguplay_enabled','kind':'boolean','type':'boolean','optional':True},
        {'key':'addon:penguplay_manifest','feature':'managed_addons','preference':'penguplay_manifest','kind':'string','type':'addon','maxLength':4000,'optional':True}])
    asset.write_text(json.dumps(controls,indent=2)+'\n')
    legacy=os.environ.get('PETALIX_VOD_MANIFEST','').strip()
    if not legacy:raise RuntimeError('Legacy source fingerprint is required for safe upgrade removal')
    parts=legacy.split('?',1);path=re.sub(r'/manifest\.json$','',parts[0].rstrip('/'),flags=re.I).rstrip('/')
    canonical=path+('?' + parts[1] if len(parts)==2 else '')
    fingerprint=hashlib.sha256(canonical.lower().encode()).hexdigest()
    (root/'petalix/BundledAddons.kt').write_text('''package com.nuvio.tv.petalix

/** A one-way upgrade fingerprint cannot install or connect to a source. */
object BundledAddons {
    const val legacyFingerprint="FINGERPRINT"
    val urls:List<String> get()=ManagedPolicy.state.value.let { policy ->
        if(policy.penguPlay && policy.penguManifest.isNotBlank())listOf(PenguPlayAccess.canonical(policy.penguManifest)) else emptyList()
    }
    fun include(current:List<String>,assigned:List<String> = urls):List<String> = assigned + current.filter { candidate -> assigned.none { it.equals(candidate,ignoreCase=true) } }
}
'''.replace('FINGERPRINT',fingerprint))
    policy=root/'petalix/ManagedPolicy.kt'
    replace_once(policy,'val lockSources:Boolean=true)','val lockSources:Boolean=true,val penguPlay:Boolean=false,val penguManifest:String="",val penguHashes:Set<String> = emptySet())')
    replace_once(policy,'p.getBoolean("sources",true))','p.getBoolean("sources",true),p.getBoolean("penguPanelControl",false)&&p.getBoolean("penguPlay",false),p.getString("penguManifest","")?:"",(p.getStringSet("penguHashes",emptySet())?:emptySet()).toSet()+BundledAddons.legacyFingerprint)')
    replace_once(policy,'values.getBoolean("mode.lockSources"))','values.getBoolean("mode.lockSources"),state.value.penguPlay,state.value.penguManifest,state.value.penguHashes)')
    replace_once(policy,'    val setupRoutes=', '''    fun configurePengu(context:Context,values:JSONObject) {
        val p=context.getSharedPreferences("petalix_management",Context.MODE_PRIVATE)
        val enabled=values.optBoolean("addon:penguplay",false)
        val manifest=if(enabled)values.getString("addon:penguplay_manifest") else p.getString("penguManifest","")?:""
        val hashes=state.value.penguHashes+BundledAddons.legacyFingerprint+(if(manifest.isBlank())emptySet() else setOf(PenguPlayAccess.fingerprint(manifest)))
        check(p.edit().putBoolean("penguPanelControl",true).putBoolean("penguPlay",enabled).putString("penguManifest",manifest).putStringSet("penguHashes",hashes).commit())
        state.value=state.value.copy(penguPlay=enabled,penguManifest=manifest,penguHashes=hashes)
    }
    val setupRoutes=''')
    addons=root/'data/local/AddonPreferences.kt'
    replace_once(addons,'if (policy.lockSources) BundledAddons.include(current) else current','com.nuvio.tv.petalix.PenguPlayAccess.urls(current, BundledAddons.urls, policy.penguPlay, policy.lockSources, policy.penguHashes + BundledAddons.legacyFingerprint)')
    replace_once(addons,'if (policy.lockSources) current + BundledAddons.urls.associateWith { true } else current','com.nuvio.tv.petalix.PenguPlayAccess.states(current, BundledAddons.urls, policy.penguPlay, policy.lockSources, policy.penguHashes + BundledAddons.legacyFingerprint)')
    replace_once(addons,'gson.toJson(BundledAddons.include(getCurrentList(preferences)))','gson.toJson(com.nuvio.tv.petalix.PenguPlayAccess.urls(getCurrentList(preferences), BundledAddons.urls, ManagedPolicy.state.value.penguPlay, true, ManagedPolicy.state.value.penguHashes + BundledAddons.legacyFingerprint))')
    replace_once(addons,'getCurrentEnabledStates(preferences) + BundledAddons.urls.associateWith { true }','com.nuvio.tv.petalix.PenguPlayAccess.states(getCurrentEnabledStates(preferences), BundledAddons.urls, ManagedPolicy.state.value.penguPlay, true, ManagedPolicy.state.value.penguHashes + BundledAddons.legacyFingerprint)')
    coordinator=root/'petalix/ManagedCoordinator.kt'
    replace_once(coordinator,'"home-services-first-v1"','"home-services-first-v1","pengu-play-v1","pengu-play-panel-v1"')
    replace_once(coordinator,'prefs.getString("revision","")).put("settingsApplied"','(if(prefs.getBoolean("penguPanelApplied",false))prefs.getString("revision","") else "")).put("settingsApplied"')
    replace_once(coordinator,'        nativeSettings.validate(values)','        nativeSettings.validate(values)\n        ManagedPolicy.configurePengu(context,values)')
    replace_once(coordinator,'if(!prefs.getBoolean("bundledVod",false)||values.getBoolean("mode.lockSources")) {','run {')
    replace_once(coordinator,'if(nativeSettings.profileId!=prefs.getInt("managedProfile",-1)||revision!=prefs.getString("revision","")||!prefs.getBoolean("bootstrapped",false)||values.getBoolean("mode.lockSettings")) {','if(!prefs.getBoolean("penguPanelApplied",false)||nativeSettings.profileId!=prefs.getInt("managedProfile",-1)||revision!=prefs.getString("revision","")||!prefs.getBoolean("bootstrapped",false)||values.getBoolean("mode.lockSettings")) {')
    replace_once(coordinator,'.putBoolean("bootstrapped",true)', '.putBoolean("bootstrapped",true).putBoolean("penguPanelApplied",true)')
    native=root/'petalix/ManagedNativeSettings.kt'
    replace_once(native,'        if(values.has("addon:cncverse")) {','''        if(values.optBoolean("addon:penguplay",false)) {
            val url=values.getString("addon:penguplay_manifest").toHttpUrlOrNull()
            require(url!=null&&url.isHttps&&url.username.isEmpty()&&url.password.isEmpty()&&url.fragment==null&&url.encodedPath.endsWith("/manifest.json",true)) { "Invalid panel Pengu Play URL" }
        }
        if(values.has("addon:cncverse")) {''')
    local=pathlib.Path(__file__).parent
    shutil.copyfile(local/'PenguPlayAccess.kt',root/'petalix/PenguPlayAccess.kt')
    tests=work/'app/src/test/java/com/nuvio/tv/petalix';tests.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(local/'PenguPlayAccessTest.kt',tests/'PenguPlayAccessTest.kt')
