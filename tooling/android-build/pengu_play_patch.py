"""Make the bundled Pengu Play source optional, independent of source locking."""
import json,pathlib,shutil
from playlist_patch import replace_once

def apply(work):
    root=work/'app/src/main/java/com/nuvio/tv'
    asset=work/'app/src/main/assets/managed-native-controls.json'
    controls=json.loads(asset.read_text())
    if any(c['key']=='addon:penguplay' for c in controls):raise RuntimeError('Pengu Play access integration already present')
    controls.append({'key':'addon:penguplay','feature':'managed_addons','preference':'penguplay_enabled','kind':'boolean','type':'boolean','optional':True})
    asset.write_text(json.dumps(controls,indent=2)+'\n')
    policy=root/'petalix/ManagedPolicy.kt'
    replace_once(policy,'val lockSources:Boolean=true)','val lockSources:Boolean=true,val penguPlay:Boolean=true)')
    replace_once(policy,'p.getBoolean("sources",true))','p.getBoolean("sources",true),p.getBoolean("penguPlay",true))')
    replace_once(policy,'values.getBoolean("mode.lockSources"))','values.getBoolean("mode.lockSources"),values.optBoolean("addon:penguplay",true))')
    replace_once(policy,'.putBoolean("sources",next.lockSources).commit()', '.putBoolean("sources",next.lockSources).putBoolean("penguPlay",next.penguPlay).commit()')
    addons=root/'data/local/AddonPreferences.kt'
    replace_once(addons,'if (policy.lockSources) BundledAddons.include(current) else current','com.nuvio.tv.petalix.PenguPlayAccess.urls(current, BundledAddons.urls, policy.penguPlay, policy.lockSources)')
    replace_once(addons,'if (policy.lockSources) current + BundledAddons.urls.associateWith { true } else current','com.nuvio.tv.petalix.PenguPlayAccess.states(current, BundledAddons.urls, policy.penguPlay, policy.lockSources)')
    replace_once(addons,'suspend fun ensureBundledAddon() {','suspend fun ensureBundledAddon(enabled:Boolean=true) {')
    replace_once(addons,'gson.toJson(BundledAddons.include(getCurrentList(preferences)))','gson.toJson(com.nuvio.tv.petalix.PenguPlayAccess.urls(getCurrentList(preferences), BundledAddons.urls, enabled, true))')
    replace_once(addons,'getCurrentEnabledStates(preferences) + BundledAddons.urls.associateWith { true }','com.nuvio.tv.petalix.PenguPlayAccess.states(getCurrentEnabledStates(preferences), BundledAddons.urls, enabled, true)')
    coordinator=root/'petalix/ManagedCoordinator.kt'
    replace_once(coordinator,'"home-services-first-v1"','"home-services-first-v1","pengu-play-v1"')
    replace_once(coordinator,'if(nativeSettings.profileId!=prefs.getInt("managedProfile",-1)||revision!=prefs.getString("revision","")||!prefs.getBoolean("bootstrapped",false)||values.getBoolean("mode.lockSettings")) {','if(!prefs.getBoolean("penguPlayPolicy",false)||nativeSettings.profileId!=prefs.getInt("managedProfile",-1)||revision!=prefs.getString("revision","")||!prefs.getBoolean("bootstrapped",false)||values.getBoolean("mode.lockSettings")) {')
    replace_once(coordinator,'.putBoolean("bootstrapped",true)', '.putBoolean("bootstrapped",true).putBoolean("penguPlayPolicy",true)')
    replace_once(coordinator,'if(!prefs.getBoolean("bundledVod",false)||values.getBoolean("mode.lockSources")) {\n            addons.ensureBundledAddon()', 'run {\n            addons.ensureBundledAddon(values.optBoolean("addon:penguplay",true))')
    local=pathlib.Path(__file__).parent
    shutil.copyfile(local/'PenguPlayAccess.kt',root/'petalix/PenguPlayAccess.kt')
    tests=work/'app/src/test/java/com/nuvio/tv/petalix';tests.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(local/'PenguPlayAccessTest.kt',tests/'PenguPlayAccessTest.kt')
