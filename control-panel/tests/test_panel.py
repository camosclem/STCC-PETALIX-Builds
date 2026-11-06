"""End-to-end API tests with an isolated SQLite database and fake streams (no provider account)."""
import importlib
import io
import os
from pathlib import Path
import sys
import tempfile

from cryptography.fernet import Fernet
from fastapi.testclient import TestClient

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
root=tempfile.TemporaryDirectory()
os.environ['PETALIX_DB_PATH']=str(Path(root.name)/'petalix.sqlite3')
os.environ['PETALIX_ADMIN_KEY']='test-admin-key-must-remain-private-not-in-build'
os.environ['PETALIX_PIN_SECRET']='test-pin-secret-of-at-least-32-chars-only-in-test-code'
os.environ['PETALIX_FERNET_KEY']=Fernet.generate_key().decode()
mod=importlib.import_module('panel.app')
cli=TestClient(mod.app)
H={'Authorization':'Bearer '+os.environ['PETALIX_ADMIN_KEY']}

M3U='''#EXTM3U
#EXTINF:-1 tvg-id="wbtv" group-title="Local TV" tvg-logo="https://example.org/wbtv.png",WBTV Charlotte CBS
https://example.org/live/wbtv.m3u8
#EXTINF:-1 tvg-id="wsoc" group-title="Local TV",WSOC Charlotte ABC
https://example.org/live/wsoc.m3u8
#EXTINF:-1 group-title="Sports",Sports Network
https://example.org/live/sports.m3u8
#EXTINF:-1 group-title="Movies",Example Movie
https://example.org/movie/1.mp4
'''


def run():
    assert cli.get('/health').json()['ok']
    assert cli.get('/api/admin/summary').status_code==401
    master=cli.post('/api/admin/masters',json={'name':'Petalix Beta Master'},headers=H).json()
    mid=master['id'];assert mid
    imported=cli.post(f'/api/admin/masters/{mid}/import',headers=H,
        files={'file':('test.m3u',M3U.encode(),'text/plain')}).json()
    assert imported['imported']==4,imported
    ch=cli.get(f'/api/admin/masters/{mid}/channels',headers=H).json()
    assert ch['total']==4
    assert 'url' not in ch['items'][0] and 'stream_secret' not in ch['items'][0]
    local=cli.post('/api/admin/markets',headers=H,json={'name':'Charlotte, NC','zipcodes':['28601','28602']}).json()
    market_id=local['id'];assert market_id
    for x,net in [(ch['items'][0],'CBS'),(ch['items'][1],'ABC')]:
        assert cli.patch('/api/admin/channels/'+str(x['id']),headers=H,
            json={'market_id':market_id,'network':net}).status_code==200
    # A fresh master upload must preserve the admin's station curation and channel IDs.
    imported_again=cli.post(f'/api/admin/masters/{mid}/import',headers=H,
        files={'file':('test.m3u',M3U.encode(),'text/plain')})
    assert imported_again.status_code==200, imported_again.text
    after=cli.get(f'/api/admin/masters/{mid}/channels',headers=H).json()['items']
    assert [x['id'] for x in after[:2]]==[x['id'] for x in ch['items'][:2]]
    assert [x['network'] for x in after[:2]]==['CBS','ABC']
    assert all(x['market_id']==market_id for x in after[:2])
    new=cli.post('/api/admin/customers',json={'name':'Beta tester','master_id':mid,'max_devices':1},headers=H).json()
    pin=new['pin'];assert len(pin)==8 and pin.isdigit()
    good=cli.post('/api/device/activate',json={'pin':pin,'device_id':'android-shield-001'}).json()
    token=good['device_token'];th={'x-device-token':token}
    assert good['customer']=='Beta tester'
    first=cli.get('/api/device/catalog?kind=live&limit=1',headers=th).json()
    assert len(first['items'])==1 and first['next_offset']==1
    all_live=cli.get('/api/device/catalog?kind=live&limit=10',headers=th).json()
    assert len(all_live['items'])==3
    assert all_live['items'][0]['url']=='https://example.org/live/wbtv.m3u8'
    assert len(cli.get('/api/device/catalog?kind=movie&limit=100',headers=th).json()['items'])==1
    loc=cli.get('/api/device/locals?zipcode=28601',headers=th).json()
    assert loc['market']=='Charlotte, NC' and len(loc['items'])==2,loc
    assert [x['network'] for x in loc['items']]==['ABC','CBS'],loc
    unknown=cli.get('/api/device/locals?zipcode=99999',headers=th).json()
    assert unknown['needs_market_mapping'] and unknown['items']==[]
    assert cli.get('/api/device/locals?zipcode=28',headers=th).status_code==400
    bad=cli.post('/api/device/activate',json={'pin':'00000000','device_id':'android-shield-001'})
    assert bad.status_code==401
    full=cli.post('/api/device/activate',json={'pin':pin,'device_id':'android-onn-television-002'})
    assert full.status_code==403
    reset=cli.post(f"/api/admin/customers/{new['customer_id']}/regenerate-pin",headers=H,json={}).json()
    assert reset['devices_reset'] and len(reset['pin'])==8
    assert cli.get('/api/device/catalog',headers=th).status_code==401
    assert cli.post('/api/device/activate',json={'pin':pin,'device_id':'android-shield-001'}).status_code==401
    reactivated=cli.post('/api/device/activate',json={'pin':reset['pin'],'device_id':'android-shield-001'})
    assert reactivated.status_code==200
    csv_result=cli.post('/api/admin/markets/import-csv',headers=H,files={'file':('zips.csv',b'zipcode,market\n90210,Los Angeles\n10001,New York\n','text/csv')})
    assert csv_result.status_code==200,csv_result.text
    assert csv_result.json()['zipcodes_imported']==2
    # Malformed CSV should not import silently.
    assert cli.post('/api/admin/markets/import-csv',headers=H,files={'file':('bad.csv',b'zip,market\n99999,Nope\n','text/csv')}).status_code==400
    print('PASS: admin auth, master import, encryption, pagination, market filtering, PIN activation, device limits, reset/revoke and CSV ZIP import')

if __name__=='__main__':run()
