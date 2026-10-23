#!/usr/bin/env python3
"""Trusted runner entry points. Gradle source is built in a separate, keyless job."""
import base64, hashlib, json, os, pathlib, re, shutil, subprocess, sys, tempfile, urllib.parse, urllib.request, urllib.error
ORIGIN='https://petalix-control.pinkgiant5.chatgpt.site'
REPOSITORY=os.environ.get('GITHUB_REPOSITORY','')
CERTIFICATE='b6809a3733b57474d01a7b77bcddef1241b7f85f35f04142d52ed68071cd1ba1'
def verify_certificate(output):
    # New apksigner versions prefix signer lines with the Android SDK range.
    digests=set(re.findall(r'certificate SHA-256 digest:\s*([a-f0-9]{64})\b',output,re.I))
    if digests!={CERTIFICATE}:
        raise RuntimeError('App signing certificate mismatch: '+(', '.join(sorted(digests)) or 'no SHA-256 certificate reported'))
def http_json(url,body=None,headers=None):
    req=urllib.request.Request(url,data=None if body is None else json.dumps(body).encode(),headers={'User-Agent':'Mozilla/5.0 STCC-Petalix-Builder/1.0',**({'Content-Type':'application/json'} if body is not None else {}),**(headers or {})})
    try:
        with urllib.request.urlopen(req,timeout=90) as response:return json.load(response)
    except urllib.error.HTTPError as error:
        if url.startswith(ORIGIN+'/api/build/'):
            try:
                reason=json.loads(error.read()).get('reason','unknown')
                if re.fullmatch(r'[A-Za-z]{1,40}',str(reason)):print('Build authentication check: '+str(reason),flush=True)
            except (ValueError,TypeError):pass
        raise
def panel(action,body):
    endpoint=os.environ['ACTIONS_ID_TOKEN_REQUEST_URL']+'&audience='+urllib.parse.quote(ORIGIN,safe='')
    token=http_json(endpoint,headers={'Authorization':'Bearer '+os.environ['ACTIONS_ID_TOKEN_REQUEST_TOKEN']})['value']
    return http_json(ORIGIN+'/api/build/'+action,body,{'X-Build-Identity':token})
def job():return json.loads(os.environ['PANEL_JOB'])
def run(args,**kw):return subprocess.run([str(v) for v in args],check=True,**kw)
def claim():
    value=panel('claim',{})['job']
    with open(os.environ['GITHUB_OUTPUT'],'a') as output:
        output.write('has_job='+('true' if value else 'false')+'\n')
        output.write('job='+json.dumps(value,separators=(',',':'))+'\n')
    print('Build runner authorized. '+('A queued build was claimed.' if value else 'No queued build.'))
def fail():
    item=job()
    if item:panel('failed',{'id':item['id'],'error':'Build or verification failed. Open the build log for the failing check; no customer update was deployed.'})
def defaults():
    item=job();value=panel('defaults',{'id':item['id']})
    manifest=value.get('manifest','')
    if not manifest:raise RuntimeError('The shared app addon configuration is unavailable.')
    print('::add-mask::'+manifest)
    with open(os.environ['GITHUB_ENV'],'a') as output:output.write('PETALIX_VOD_MANIFEST='+manifest+'\n')
def android_tools():
    root=pathlib.Path(os.environ.get('ANDROID_HOME') or os.environ['ANDROID_SDK_ROOT'])/'build-tools'
    choices=sorted(root.glob('*/apksigner'),key=lambda p:[int(x) if x.isdigit() else 0 for x in p.parent.name.split('.')])
    if not choices:raise RuntimeError('Android build tools are unavailable.')
    return choices[-1].parent

def sign_and_release():
    item=job();out=pathlib.Path('release');out.mkdir(exist_ok=True)
    # This runs on a fresh signing runner, which has not executed upstream source.
    credentials=panel('credentials',{'id':item['id']});cfg=credentials['config']
    for field in ['store_password','key_password','alias']:print('::add-mask::'+str(cfg[field]))
    env=dict(os.environ,SIGN_STORE_PASSWORD=cfg['store_password'],SIGN_KEY_PASSWORD=cfg['key_password'])
    tools=android_tools();artifacts=pathlib.Path('artifacts')
    with tempfile.TemporaryDirectory(prefix='android-signing-') as private:
        key=pathlib.Path(private)/'release.jks';key.write_bytes(base64.b64decode(credentials['key']));key.chmod(0o600)
        for brand,label,package,offset in [('petalix','Petalix','com.petalix.iptv.preview',0),('stcc','STCC','com.stcc.iptv',1)]:
            number=item['versionCode']+offset;version='1.1.0-'+brand+'.panel'+str(number)
            apk=out/(label+'-'+str(number)+'.apk');aligned=pathlib.Path(private)/(brand+'-aligned.apk')
            candidates=list(artifacts.rglob(brand+'-unsigned.apk'))
            if len(candidates)!=1:raise RuntimeError('Expected exactly one APK for '+brand)
            run([tools/'zipalign','-f','-P','16','4',candidates[0],aligned])
            run([tools/'apksigner','sign','--ks',key,'--ks-key-alias',cfg['alias'],'--ks-pass','env:SIGN_STORE_PASSWORD','--key-pass','env:SIGN_KEY_PASSWORD','--out',apk,aligned],env=env)
            verified=run([tools/'apksigner','verify','--verbose','--print-certs',apk],capture_output=True,text=True).stdout.lower()
            verify_certificate(verified)
            badging=run([tools/'aapt','dump','badging',apk],capture_output=True,text=True).stdout
            if not all(v in badging for v in ["name='"+package+"'","versionCode='"+str(number)+"'","versionName='"+version+"'"]):raise RuntimeError('App identity or version mismatch')
            if 'management-v3' not in run(['unzip','-p',apk,'classes.dex'],capture_output=True).stdout.decode('latin1'):
                # R8 can place the managed client in another dex file; inspect all dex strings.
                import zipfile
                with zipfile.ZipFile(apk) as z:
                    if not any(b'management-v3' in z.read(n) for n in z.namelist() if re.fullmatch(r'classes\d*\.dex',n)):raise RuntimeError('Managed client capability missing')
            source_candidates=list(artifacts.rglob(brand+'-source.zip'))
            if len(source_candidates)!=1:raise RuntimeError('Corresponding source missing')
            source=out/(label+'-'+str(number)+'-Source.zip');shutil.copyfile(source_candidates[0],source)
            meta={'packageName':package,'certificateSha256':CERTIFICATE,'versionCode':number,'versionName':version,'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'sizeBytes':apk.stat().st_size,'minSdk':24,'assetName':apk.name,'sourceAssetName':source.name,'upstreamTag':item['source']['tag'] or 'commit-'+item['source']['sha'][:10],'upstreamCommit':item['source']['sha'],'notes':'Prepared from '+item['source']['sha']+' for '+label+'. Activation and shared panel controls retained. Physical TV playback still requires testing.'}
            (out/(brand+'-release.json')).write_text(json.dumps(meta,indent=2)+'\n')
    tag='panel-'+item['id'];assets=[str(p) for p in out.iterdir()]
    notes=out/'release-notes.md';notes.write_text('STCC and Petalix builds from upstream commit '+item['source']['sha']+'.\n\nThese builds are prepared for testing. Deploy each service from the shared panel when ready. Matching GPL source archives are included.\n')
    existing=subprocess.run(['gh','release','view',tag,'--repo',REPOSITORY,'--json','id'],capture_output=True,text=True)
    if existing.returncode==0:raise RuntimeError('This build release already exists. Verify its state before retrying publication.')
    run(['gh','release','create',tag,'--repo',REPOSITORY,'--title','STCC + Petalix · '+(item['source']['tag'] or item['source']['sha'][:10]),'--notes-file',notes,'--prerelease',*assets])
    release=json.loads(run(['gh','api','repos/'+REPOSITORY+'/releases/tags/'+tag],capture_output=True,text=True).stdout)
    panel('complete',{'id':item['id'],'release':release,'metadata':{brand:json.loads((out/(brand+'-release.json')).read_text()) for brand in ['petalix','stcc']}})
    print('Both signed builds were published for review. Customer deployment remains a separate panel action.')
def recover():
    identifier=os.environ.get('RECOVER_BUILD_ID','')
    if not re.fullmatch(r'[a-f0-9-]{36}',identifier):raise RuntimeError('Choose the exact completed panel build identifier.')
    tag='panel-'+identifier
    release=json.loads(run(['gh','api','repos/'+REPOSITORY+'/releases/tags/'+tag],capture_output=True,text=True).stdout)
    tools=android_tools()
    with tempfile.TemporaryDirectory(prefix='verify-published-') as directory:
        root=pathlib.Path(directory)
        run(['gh','release','download',tag,'--repo',REPOSITORY,'--dir',root,'--pattern','*-release.json','--pattern','*.apk'])
        metadata={brand:json.loads((root/(brand+'-release.json')).read_text()) for brand in ['petalix','stcc']}
        for brand,meta in metadata.items():
            name=meta['assetName']
            if pathlib.Path(name).name!=name:raise RuntimeError('Invalid APK filename')
            apk=root/name
            if apk.stat().st_size!=meta['sizeBytes'] or hashlib.sha256(apk.read_bytes()).hexdigest()!=meta['sha256']:raise RuntimeError('Published APK checksum mismatch')
            verify_certificate(run([tools/'apksigner','verify','--print-certs',apk],capture_output=True,text=True).stdout.lower())
            info=run([tools/'aapt','dump','badging',apk],capture_output=True,text=True).stdout
            if not all(v in info for v in ["name='"+meta['packageName']+"'","versionCode='"+str(meta['versionCode'])+"'","versionName='"+meta['versionName']+"'"]):raise RuntimeError('Published app identity mismatch')
        panel('recover',{'id':identifier,'release':release,'metadata':metadata})
    print('Both published APK signatures and checksums verified. Panel downloads and build status restored.')
def sync_current():
    tag='panel-current-7.5'
    found=subprocess.run(['gh','release','view',tag,'--repo',REPOSITORY,'--json','id'],capture_output=True,text=True)
    if found.returncode==0:
        print('Current signed builds are already registered.');return
    sources={'petalix':('Petalix',207502,'com.petalix.iptv.preview','b3fbfcbb73708a51e3a6222e35b520d52c9970a68a3ae6d07ec68af8c530b8c0',141680180,'fb9383b8078cd82b9271c8ca0474329a032e6e055fc91f35300a47e50eb79a77'),
             'stcc':('STCC',207501,'com.stcc.iptv','42d809f3b127e8986e9673cf9136eae2cd7be3bf8b147bf409b9d01c67579642',142256532,'af42529c12a412e7f47dee89b02cc43a5e80d880447c2071f3002eed0b82b3dd')}
    out=pathlib.Path('current-release');out.mkdir(exist_ok=True);tools=android_tools()
    for brand,(label,number,package,expected,size,source_sha) in sources.items():
        for kind in ['apk','source']:
            original=label+'-7.5-Controls-'+('Preview.apk' if kind=='apk' else 'Source.zip')
            destination=out/(label+'-'+str(number)+('.apk' if kind=='apk' else '-Source.zip'))
            req=urllib.request.Request(ORIGIN+'/downloads/'+original,headers={'User-Agent':'Mozilla/5.0 STCC-Petalix-Builder/1.0'})
            with urllib.request.urlopen(req,timeout=180) as response,destination.open('wb') as target:shutil.copyfileobj(response,target)
            actual=hashlib.sha256(destination.read_bytes()).hexdigest()
            if actual!=(expected if kind=='apk' else source_sha):raise RuntimeError('Current '+brand+' '+kind+' checksum mismatch')
        apk=out/(label+'-'+str(number)+'.apk')
        verified=run([tools/'apksigner','verify','--print-certs',apk],capture_output=True,text=True).stdout.lower()
        verify_certificate(verified)
        info=run([tools/'aapt','dump','badging',apk],capture_output=True,text=True).stdout
        version='1.1.0-'+brand+'.7.5-controls1'
        if not all(v in info for v in ["name='"+package+"'","versionCode='"+str(number)+"'","versionName='"+version+"'"]):raise RuntimeError('Current app identity mismatch')
        meta={'packageName':package,'certificateSha256':CERTIFICATE,'versionCode':number,'versionName':version,'sha256':expected,'sizeBytes':size,'minSdk':24,'assetName':apk.name,'sourceAssetName':label+'-'+str(number)+'-Source.zip','upstreamTag':'1.1.0-beta.7.5','upstreamCommit':'39aa0cd17a417497d1e6a77479a12fa21bdc0f5c','notes':'Current verified '+label+' build. 277 shared panel settings and options. Matching GPL source included.'}
        (out/(brand+'-release.json')).write_text(json.dumps(meta,indent=2)+'\n')
    assets=[str(p) for p in out.iterdir()]
    notes=out/'notes.md';notes.write_text('Current STCC and Petalix builds, with their original verified signatures and matching GPL source. Publishing this GitHub release does not offer an update to customers; deploy each service from the shared panel when ready.\n')
    run(['gh','release','create',tag,'--repo',REPOSITORY,'--title','STCC + Petalix · current 7.5 builds','--notes-file',notes,'--prerelease',*assets])
    print('Current signed builds registered for panel deployment.')
if __name__=='__main__':
    if not re.fullmatch(r'[A-Za-z0-9_-][A-Za-z0-9_.-]*/[A-Za-z0-9_-][A-Za-z0-9_.-]*',REPOSITORY):raise RuntimeError('Run this workflow from the dedicated panel build repository.')
    {'claim':claim,'defaults':defaults,'sign':sign_and_release,'failed':fail,'sync-current':sync_current,'recover':recover}[sys.argv[1]]()
