"""Pinned original Home sources and independent source-preservation checks."""
import hashlib,pathlib,zipfile

RECIPE='home-baseline-pin-v1'
RELEASE_ID='425841470'
UPSTREAM='50e713e33b9ed6fc48bd3bdec5a773e0a552d160'
TAG='panel-a169ba3b-3792-473a-8b3a-b0ade721fdd9'
FILES={
 'petalix':('Petalix-1793124972-Source.zip','3131640e3324b5d2fc3fa5e016379aee834266fcf2900ed2c8066af9184ad518'),
 'stcc':('STCC-1793124973-Source.zip','bcf3d4e801bdf2ba483bd562f83b98d55f04dd9923bb213794f7375d6180bc2b'),
}
PREFIX='app/src/main/java/com/nuvio/tv/petalix/'
TEST_PREFIX='app/src/test/java/com/nuvio/tv/petalix/'
ALLOWED={'app/build.gradle.kts',*(PREFIX+name for name in ['ManagedCoordinator.kt','ActivationActivity.kt','PanelConnectionPolicy.kt','ActivationSession.kt']),*(TEST_PREFIX+name for name in ['PanelConnectionPolicyTest.kt','ActivationSessionTest.kt'])}

def baseline_url(brand):
 return 'https://github.com/camosclem/STCC-PETALIX-Builds/releases/download/'+TAG+'/'+FILES[brand][0]

def verify_archive(archive,brand):
 if hashlib.sha256(pathlib.Path(archive).read_bytes()).hexdigest()!=FILES[brand][1]:raise RuntimeError('Original Home source checksum mismatch')

def source_files(archive):
 result={}
 with zipfile.ZipFile(archive) as source:
  for member in source.infolist():
   if member.is_dir():continue
   parts=pathlib.PurePosixPath(member.filename).parts
   if len(parts)<2 or any(p in {'..','.git'} for p in parts):raise RuntimeError('Invalid corresponding-source path')
   name='/'.join(parts[1:])
   if name in result:raise RuntimeError('Duplicate corresponding-source path')
   result[name]=hashlib.sha256(source.read(member)).hexdigest()
 return result

def verify_preserved_source(original,updated,brand):
 verify_archive(original,brand)
 before,after=source_files(original),source_files(updated)
 changed={name for name in before.keys()|after.keys() if before.get(name)!=after.get(name)}
 unexpected=changed-ALLOWED
 if unexpected:raise RuntimeError('The original Home source changed outside the PIN repair: '+', '.join(sorted(unexpected)))
 required={PREFIX+'ManagedCoordinator.kt',PREFIX+'ActivationActivity.kt',PREFIX+'PanelConnectionPolicy.kt','app/build.gradle.kts'}
 if not required<=changed:raise RuntimeError('The complete PIN repair was not applied')
 return FILES[brand][1]
