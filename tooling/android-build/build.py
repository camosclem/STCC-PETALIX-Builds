#!/usr/bin/env python3
"""Merge a pinned upstream tree with the saved managed app source, then build without release keys."""
import hashlib,json,os,pathlib,re,shutil,subprocess,sys,tarfile,tempfile,urllib.request,zipfile
from runner import ORIGIN,run
BASE='39aa0cd17a417497d1e6a77479a12fa21bdc0f5c'
SOURCES={'petalix':('Petalix-7.5-Controls-Source.zip','fb9383b8078cd82b9271c8ca0474329a032e6e055fc91f35300a47e50eb79a77'),'stcc':('STCC-7.5-Controls-Source.zip','af42529c12a412e7f47dee89b02cc43a5e80d880447c2071f3002eed0b82b3dd')}
def download(url,path):
    req=urllib.request.Request(url,headers={'User-Agent':'Mozilla/5.0 STCC-Petalix-Builder/1.0'})
    with urllib.request.urlopen(req,timeout=180) as response,path.open('wb') as target:shutil.copyfileobj(response,target)
def safe_path(root,name):
    target=(root/name).resolve()
    if not target.is_relative_to(root.resolve()):raise RuntimeError('Unsafe archive path')
    return target
def untar(archive,root):
    with tarfile.open(archive) as source:
        for member in source:
            parts=pathlib.PurePosixPath(member.name).parts[1:]
            if not parts or '.git' in parts:continue
            path=safe_path(root,pathlib.Path(*parts))
            if member.isdir():path.mkdir(parents=True,exist_ok=True)
            elif member.isfile():
                path.parent.mkdir(parents=True,exist_ok=True)
                with source.extractfile(member) as src,path.open('wb') as dst:shutil.copyfileobj(src,dst)
                path.chmod(member.mode&0o777)
def unzip(archive,root):
    with zipfile.ZipFile(archive) as source:
        for member in source.infolist():
            parts=pathlib.PurePosixPath(member.filename).parts[1:]
            if not parts or '.git' in parts:continue
            path=safe_path(root,pathlib.Path(*parts))
            if member.is_dir():path.mkdir(parents=True,exist_ok=True)
            else:
                path.parent.mkdir(parents=True,exist_ok=True)
                with source.open(member) as src,path.open('wb') as dst:shutil.copyfileobj(src,dst)
def restore_base_build_sources(root):
    # Restore original source packages omitted by the earlier export's build-dir filter.
    unzip(pathlib.Path(__file__).with_name('base-build-sources.zip'),root)

def adapt_frame_rate_control(root):
    # New upstream releases store three modes. Legacy enabled meant START_STOP.
    preferences=root/'app/src/main/java/com/nuvio/tv/livetv/data/LiveTvPreferences.kt'
    coordinator=root/'app/src/main/java/com/nuvio/tv/petalix/ManagedCoordinator.kt'
    if 'frameRateMode = p[Keys.frameRateMode]' not in preferences.read_text():return
    text=coordinator.read_text()
    old='matchFrameRate=values.getBoolean("play.frameRate")'
    new='frameRateMode=if(values.getBoolean("play.frameRate"))"START_STOP" else "OFF"'
    if new in text:return
    if text.count(old)!=1:raise RuntimeError('Managed frame-rate binding needs review for this upstream version.')
    coordinator.write_text(text.replace(old,new))

def clear(root):
    for p in root.iterdir():
        if p.name=='.git':continue
        if p.is_dir():shutil.rmtree(p)
        else:p.unlink()
def prepare_home_baseline(item,brand,work,cache):
    from home_baseline import FILES,UPSTREAM,baseline_url,verify_archive
    if item['source']['sha']!=UPSTREAM:raise RuntimeError('The original Home build needs its exact upstream revision.')
    filename,_=FILES[brand];archive=cache/filename
    if not archive.exists():download(baseline_url(brand),archive)
    verify_archive(archive,brand)
    work.mkdir(parents=True,exist_ok=True)
    if any(work.iterdir()):raise RuntimeError('The original Home build needs an empty work directory.')
    unzip(archive,work)
    gradle=work/'app/build.gradle.kts';text=gradle.read_text();number=item['versionCode']+(brand=='stcc');version='1.1.0-'+brand+'.panel'+str(number)
    text,n=re.subn(r'android\.defaultConfig\.versionCode\s*=\s*\d+','android.defaultConfig.versionCode = '+str(number),text)
    if n!=1:raise RuntimeError('Original Home version code binding needs review')
    text,n=re.subn(r'android\.defaultConfig\.versionName\s*=\s*"[^"]+"','android.defaultConfig.versionName = "'+version+'"',text)
    if n!=1:raise RuntimeError('Original Home version name binding needs review')
    gradle.write_text(text)
    tmdb=os.environ.get('TMDB_API_KEY','')
    if not re.fullmatch('[a-fA-F0-9]{32}',tmdb):raise RuntimeError('TMDB configuration is required for this build.')
    (work/'local.properties').write_text('sdk.dir='+os.environ.get('ANDROID_HOME',os.environ.get('ANDROID_SDK_ROOT',''))+'\nTMDB_API_KEY='+tmdb+'\n')
    (work/'gradlew').chmod(0o755)
    from activation_patch import apply as apply_activation
    apply_activation(work,brand)
    from panel_connection_patch import apply as apply_panel_connection
    apply_panel_connection(work)
    return version
def prepare(item,brand,work,cache):
    from home_baseline import RECIPE
    from seek_previews_patch import RECIPE as SEEK_RECIPE,apply as apply_seek
    from logo_trial_patch import RECIPE as BRAND_RECIPE,apply as apply_logo
    if item.get('recipeRevision') in [RECIPE,SEEK_RECIPE,BRAND_RECIPE]:
        version=prepare_home_baseline(item,brand,work,cache)
        if item.get('recipeRevision') in [SEEK_RECIPE,BRAND_RECIPE]:
            from service_hubs_patch import apply as apply_hubs
            apply_hubs(work)
            apply_seek(work,cache)
            vm=work/'app/src/main/java/com/nuvio/tv/ui/screens/collection/FolderDetailViewModel.kt'
            vm.write_text(vm.read_text().replace('collectionId=="collection-streaming-services-only"','collectionId in setOf("collection-streaming-services-only","collection-service-templates-test-v1")'))
            if item.get('recipeRevision')==BRAND_RECIPE:apply_logo(work,brand)
        return version
    sha=item['source']['sha']
    if not re.fullmatch('[a-f0-9]{40}',sha):raise RuntimeError('Invalid source commit')
    filename,expected=SOURCES[brand];custom=cache/filename
    if not custom.exists():download(ORIGIN+'/downloads/'+filename,custom)
    if hashlib.sha256(custom.read_bytes()).hexdigest()!=expected:raise RuntimeError('Managed source archive checksum mismatch')
    for revision in set([BASE,sha]):
        archive=cache/(revision+'.tar.gz')
        if not archive.exists():download('https://api.github.com/repos/homelessbrian/NuvioPlusIPTV/tarball/'+revision,archive)
    work.mkdir(parents=True,exist_ok=True)
    if sha==BASE:
        # Keep the current app byte-for-byte except for the explicit panel integration below.
        unzip(custom,work)
        restore_base_build_sources(work)
        # Earlier source exports omitted source directories named build; restore those files.
        with tarfile.open(cache/(BASE+'.tar.gz')) as archive:
            for member in archive:
                parts=pathlib.PurePosixPath(member.name).parts[1:]
                if member.isfile() and len(parts)>5 and parts[:2]==('app','src') and 'build' in parts[4:] and member.name.endswith(('.kt','.java')):
                    target=safe_path(work,pathlib.Path(*parts))
                    if not target.exists():
                        target.parent.mkdir(parents=True,exist_ok=True)
                        with archive.extractfile(member) as src,target.open('wb') as dst:shutil.copyfileobj(src,dst)
    else:
        work.mkdir(parents=True,exist_ok=True);untar(cache/(BASE+'.tar.gz'),work)
        def git(*args,**kwargs):return run(['git','-C',work,*args],**kwargs)
        git('init','-q');git('config','user.email','build@stcc-petalix.invalid');git('config','user.name','STCC + Petalix Builder');git('add','-Af');git('commit','-qm','Pinned upstream base '+BASE)
        base=git('rev-parse','HEAD',capture_output=True,text=True).stdout.strip()
        clear(work);unzip(custom,work);restore_base_build_sources(work);git('add','-Af');git('commit','-qm','Managed '+brand+' application');custom_ref=git('rev-parse','HEAD',capture_output=True,text=True).stdout.strip()
        git('checkout','-q','-B','selected-upstream',base);clear(work);untar(cache/(sha+'.tar.gz'),work);git('add','-Af');git('commit','--allow-empty','-qm','Selected upstream '+sha)
        merge=subprocess.run(['git','-C',str(work),'merge','--no-commit','--no-edit',custom_ref],capture_output=True,text=True)
        if merge.returncode:
            # Branding touches translated strings and README, which upstream edits too.
            # Keep upstream translations/documentation and reapply the brand below.
            conflicts=git('diff','--name-only','--diff-filter=U',capture_output=True,text=True).stdout.splitlines()
            for name in conflicts:
                if name=='README.md' or re.fullmatch(r'app/src/main/res/values[^/]*/strings\.xml',name):
                    git('checkout','--ours','--',name);git('add','--',name)
                else:
                    from upstream_policy_patch import resolve
                    resolve(work,name)
            conflicts=git('diff','--name-only','--diff-filter=U',capture_output=True,text=True).stdout
            if conflicts:print('Source merge needs review:\n'+conflicts,flush=True)
            if conflicts:raise RuntimeError('Upstream changes conflict with managed app code. Resolve the listed files before building this version.')
    gradle=work/'app/build.gradle.kts';text=gradle.read_text();number=item['versionCode']+(brand=='stcc');version='1.1.0-'+brand+'.panel'+str(number)
    text,n=re.subn(r'android\.defaultConfig\.versionCode\s*=\s*\d+', 'android.defaultConfig.versionCode = '+str(number),text)
    if n!=1:raise RuntimeError('Managed version code override missing')
    text,n=re.subn(r'android\.defaultConfig\.versionName\s*=\s*"[^"]+"','android.defaultConfig.versionName = "'+version+'"',text)
    if n!=1:raise RuntimeError('Managed version name override missing')
    gradle.write_text(text)
    from branding_patch import apply as apply_branding
    apply_branding(work,brand)
    tmdb=os.environ.get('TMDB_API_KEY','')
    if not re.fullmatch('[a-fA-F0-9]{32}',tmdb):raise RuntimeError('TMDB configuration is required for this build.')
    (work/'local.properties').write_text('sdk.dir='+os.environ.get('ANDROID_HOME',os.environ.get('ANDROID_SDK_ROOT',''))+'\nTMDB_API_KEY='+tmdb+'\n')
    (work/'gradlew').chmod(0o755)
    manifest=work/'app/src/main/AndroidManifest.xml'
    original=manifest.read_text();original,count=re.subn(r'(\<application\b[^>]*?android:label=)[\"][^\"]*[\"]',r'\g<1>"'+('STCC' if brand=='stcc' else 'Petalix')+'"',original,count=1,flags=re.S)
    if count!=1:raise RuntimeError('Application label binding missing')
    manifest.write_text(original)
    adapt_frame_rate_control(work)
    from playlist_patch import apply
    apply(work,brand)
    from kaptain_patch import apply as apply_kaptain
    apply_kaptain(work)
    from device_updates_patch import apply as apply_device_updates
    apply_device_updates(work,brand)
    from activation_patch import apply as apply_activation
    apply_activation(work,brand)
    from home_row_patch import apply as apply_home_row
    apply_home_row(work)
    from pengu_play_patch import apply as apply_pengu_play
    apply_pengu_play(work)
    from service_hubs_patch import apply as apply_service_hubs
    apply_service_hubs(work)
    from panel_connection_patch import apply as apply_panel_connection
    apply_panel_connection(work)
    required=['ManagedCoordinator.kt','ManagedNativeSettings.kt','ManagedPolicy.kt','ActivationActivity.kt']
    for file in required:
        if not list((work/'app/src').rglob(file)):raise RuntimeError('Managed client source missing: '+file)
    return version
def main():
    item=json.loads(os.environ['PANEL_JOB']);brand=sys.argv[1]
    if brand not in SOURCES:raise RuntimeError('Unknown service')
    cache=pathlib.Path('android-cache');cache.mkdir(exist_ok=True);work=pathlib.Path('android-work').resolve();out=pathlib.Path('out');out.mkdir(exist_ok=True)
    prepare(item,brand,work,cache)
    env=dict(os.environ,CI_USE_DEBUG_SIGNING='true',PETALIX_VOD_MANIFEST='')
    command=[work/'gradlew',':app:testFullReleaseUnitTest','--tests','com.nuvio.tv.petalix.*',*(['--tests','com.nuvio.tv.core.player.thumbnail.*'] if item.get('recipeRevision') in ['seek-service-templates-test-v1','seek-service-templates-branding-test-v2'] else []),':app:assembleFullRelease','-PlivetvTestBuild=true','--no-daemon','--max-workers=1','-Pkotlin.compiler.execution.strategy=in-process','-Dorg.gradle.jvmargs=-Xmx4608m -XX:MaxMetaspaceSize=768m','-x',':app:lintVitalAnalyzeFullRelease','-x',':app:lintVitalReportFullRelease','-x',':app:lintVitalFullRelease']
    run(command,cwd=work,env=env)
    apks=list((work/'app/build/outputs/apk/full/release').glob('*.apk'))
    if len(apks)!=1:raise RuntimeError('Expected one universal release APK')
    shutil.copyfile(apks[0],out/(brand+'-unsigned.apk'))
    with zipfile.ZipFile(out/(brand+'-source.zip'),'w',zipfile.ZIP_DEFLATED) as archive:
        for path in work.rglob('*'):
            relative=path.relative_to(work)
            if not path.is_file() or (any(p in {'.git','.gradle','.kotlin','.idea','.cxx'} for p in relative.parts) or relative.parts[0]=='build' or (len(relative.parts)>1 and relative.parts[1]=='build')) or path.name in {'local.properties','local.dev.properties'} or path.suffix in {'.jks','.keystore'}:continue
            archive.write(path,str(pathlib.Path(brand+'-source')/relative))
    from home_baseline import RECIPE,FILES,verify_preserved_source
    if item.get('recipeRevision')==RECIPE:verify_preserved_source(cache/FILES[brand][0],out/(brand+'-source.zip'),brand)
    if item.get('recipeRevision') in ['seek-service-templates-test-v1','seek-service-templates-branding-test-v2']:
        from seek_previews_patch import verify_trial_source
        verify_trial_source(cache/FILES[brand][0],out/(brand+'-source.zip'),brand,branding=item.get('recipeRevision')=='seek-service-templates-branding-test-v2')
    print('Managed tests and APK build passed for '+brand,flush=True)
if __name__=='__main__':main()
