"""Petalix Control BETA. Independent API; do not mount a production database here.

For authorized M3U/Xtream sources only. Requires PETALIX_ADMIN_KEY,
PETALIX_PIN_SECRET, PETALIX_FERNET_KEY at startup.
"""
from __future__ import annotations

import hashlib
import hmac
import io
import ipaddress
import json
import os
import re
import secrets
import socket
import sqlite3
import threading
import time
import urllib.parse
from contextlib import contextmanager
from datetime import datetime, timezone
from pathlib import Path

from cryptography.fernet import Fernet
from fastapi import FastAPI, HTTPException, Header, UploadFile, File, Depends, Request
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field

ROOT = Path(__file__).resolve().parent
DB_PATH = Path(os.getenv('PETALIX_DB_PATH', str(ROOT / 'petalix-beta.sqlite3')))
ADMIN_KEY = os.getenv('PETALIX_ADMIN_KEY', '')
PIN_SECRET = os.getenv('PETALIX_PIN_SECRET', '')
FERNET_KEY = os.getenv('PETALIX_FERNET_KEY', '')
if len(ADMIN_KEY) < 24 or len(PIN_SECRET) < 32 or not FERNET_KEY:
    raise RuntimeError('Configure long PETALIX_ADMIN_KEY, PETALIX_PIN_SECRET, and PETALIX_FERNET_KEY (see .env.example)')
FERNET = Fernet(FERNET_KEY.encode())
DB_PATH.parent.mkdir(parents=True, exist_ok=True)

app = FastAPI(title='Petalix Control BETA', version='1.0.0', docs_url=None, redoc_url=None)
_rate: dict[str, list[float]] = {}
_rate_lock = threading.Lock()

@contextmanager
def db():
    conn = sqlite3.connect(str(DB_PATH), timeout=30, isolation_level=None)
    conn.row_factory = sqlite3.Row
    conn.execute('PRAGMA journal_mode=WAL')
    conn.execute('PRAGMA foreign_keys=ON')
    try:
        yield conn
    finally:
        conn.close()

SCHEMA = """
CREATE TABLE IF NOT EXISTS masters (
 id INTEGER PRIMARY KEY, name TEXT NOT NULL, kind TEXT NOT NULL DEFAULT 'm3u',
 source_secret TEXT, last_import INTEGER, revision INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS markets (id INTEGER PRIMARY KEY, name TEXT NOT NULL UNIQUE);
CREATE TABLE IF NOT EXISTS market_zips (zipcode TEXT PRIMARY KEY, market_id INTEGER NOT NULL REFERENCES markets(id) ON DELETE CASCADE);
CREATE TABLE IF NOT EXISTS channels (
 id INTEGER PRIMARY KEY, master_id INTEGER NOT NULL REFERENCES masters(id) ON DELETE CASCADE,
 stable_key TEXT NOT NULL,
 kind TEXT NOT NULL, name TEXT NOT NULL, group_name TEXT NOT NULL,
 tvg_id TEXT, logo TEXT, stream_secret TEXT NOT NULL,
 market_id INTEGER REFERENCES markets(id) ON DELETE SET NULL,
 network TEXT, hidden INTEGER NOT NULL DEFAULT 0, priority INTEGER NOT NULL DEFAULT 100
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_channels_master_stable ON channels(master_id,stable_key);
CREATE INDEX IF NOT EXISTS idx_channels_master_kind ON channels(master_id,kind,hidden,id);
CREATE INDEX IF NOT EXISTS idx_channels_market ON channels(master_id,market_id,hidden);
CREATE TABLE IF NOT EXISTS customers (
 id INTEGER PRIMARY KEY, name TEXT NOT NULL, master_id INTEGER NOT NULL REFERENCES masters(id),
 pin_digest TEXT NOT NULL UNIQUE, pin_hint TEXT NOT NULL, enabled INTEGER NOT NULL DEFAULT 1,
 max_devices INTEGER NOT NULL DEFAULT 1, expires_at INTEGER NOT NULL,
 created_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS devices (
 id INTEGER PRIMARY KEY, customer_id INTEGER NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
 device_digest TEXT NOT NULL, token_digest TEXT NOT NULL UNIQUE,
 created_at INTEGER NOT NULL, last_seen INTEGER NOT NULL,
 UNIQUE(customer_id,device_digest)
);
"""
with db() as conn:
    conn.executescript(SCHEMA)


def now(): return int(time.time())
def digest(value: str, prefix: str) -> str:
    return hmac.new(PIN_SECRET.encode(), (prefix + value).encode(), hashlib.sha256).hexdigest()
def encrypt(value: str) -> str: return FERNET.encrypt(value.encode('utf-8')).decode()
def decrypt(value: str) -> str: return FERNET.decrypt(value.encode()).decode('utf-8')


def need_admin(authorization: str = Header(default='')):
    if not authorization.startswith('Bearer ') or not hmac.compare_digest(authorization[7:], ADMIN_KEY):
        raise HTTPException(status_code=401, detail='Admin authentication required')


def need_device(x_device_token: str = Header(default='')) -> sqlite3.Row:
    if not x_device_token:
        raise HTTPException(status_code=401, detail='Pair this device first')
    token_hash = digest(x_device_token, 'TOKEN:')
    with db() as c:
        device = c.execute('''SELECT d.id AS did,d.customer_id,c.name,c.master_id,c.enabled,c.expires_at,m.revision
            FROM devices d JOIN customers c ON d.customer_id=c.id
            JOIN masters m ON m.id=c.master_id WHERE d.token_digest=?''',(token_hash,)).fetchone()
        if device is None or not device['enabled'] or device['expires_at'] <= now():
            raise HTTPException(status_code=401, detail='Device activation expired or revoked')
        c.execute('UPDATE devices SET last_seen=? WHERE id=?',(now(),device['did']))
        return dict(device)


def rate_check(key: str, limit: int = 6, window: int = 60):
    with _rate_lock:
        t = time.monotonic()
        arr = [v for v in _rate.get(key,[]) if v > t-window]
        if len(arr)>=limit:
            raise HTTPException(status_code=429, detail='Too many PIN attempts. Try again later.')
        arr.append(t); _rate[key] = arr
        if len(_rate)>10000: _rate.clear()

class MasterCreate(BaseModel):
    name: str = Field(min_length=1,max_length=80)
    kind: str = Field(default='m3u',pattern=r'^(m3u|xtream)$')

class CustomerCreate(BaseModel):
    name: str = Field(min_length=1,max_length=100)
    master_id: int
    max_devices: int = Field(default=2,ge=1,le=20)
    days: int = Field(default=365,ge=1,le=3650)

class CustomerUpdate(BaseModel):
    enabled: bool | None = None
    max_devices: int | None = Field(default=None,ge=1,le=20)
    master_id: int | None = None
    days_remaining: int | None = Field(default=None,ge=1,le=3650)

class MarketCreate(BaseModel):
    name: str = Field(min_length=2,max_length=80)
    zipcodes: list[str] = Field(default_factory=list,max_length=400)

class ZipAssign(BaseModel):
    zipcodes: list[str] = Field(max_length=500)

class ChannelPatch(BaseModel):
    hidden: bool | None = None
    group: str | None = Field(default=None,max_length=100)
    market_id: int | None = None
    network: str | None = Field(default=None,pattern=r'^(ABC|CBS|NBC|FOX|PBS|CW|UNI|TELEMUNDO|OTHER|)$')
    priority: int | None = Field(default=None,ge=0,le=1000)

class Activate(BaseModel):
    pin: str = Field(pattern=r'^\d{8}$')
    device_id: str = Field(min_length=12,max_length=180)


def new_pin(c) -> tuple[str, str]:
    for _ in range(20):
        pin = f'{secrets.randbelow(100000000):08d}'
        ph = digest(pin, 'PIN:')
        if c.execute('SELECT 1 FROM customers WHERE pin_digest=?',(ph,)).fetchone() is None:
            return pin, ph
    raise HTTPException(500, 'PIN generation failed')

@app.get('/health')
def health(): return {'ok':True,'service':'Petalix Control BETA','production':False}

@app.get('/api/admin/summary',dependencies=[Depends(need_admin)])
def summary():
    with db() as c:
        masters = [dict(r) for r in c.execute('''SELECT m.id,m.name,m.kind,m.revision,m.last_import,COUNT(ch.id) channel_count
            FROM masters m LEFT JOIN channels ch ON ch.master_id=m.id GROUP BY m.id ORDER BY m.id DESC''')]
        customers = [dict(r) for r in c.execute('''SELECT c.id,c.name,c.master_id,c.pin_hint,c.enabled,c.max_devices,c.expires_at,
            (SELECT COUNT(*) FROM devices d WHERE d.customer_id=c.id) AS devices FROM customers c ORDER BY c.id DESC LIMIT 500''')]
        markets = [dict(r) for r in c.execute('''SELECT m.id,m.name,COUNT(z.zipcode) zipcode_count FROM markets m
            LEFT JOIN market_zips z ON m.id=z.market_id GROUP BY m.id ORDER BY m.name''')]
    return {'masters':masters,'customers':customers,'markets':markets}

@app.post('/api/admin/masters',dependencies=[Depends(need_admin)])
def create_master(data:MasterCreate):
    with db() as c:
        cur=c.execute('INSERT INTO masters(name,kind) VALUES(?,?)',(data.name.strip(),data.kind))
        return {'id':cur.lastrowid,'name':data.name,'kind':data.kind}

# Do not store provider URLs/logins in public GitHub; import an authorized M3U through this authenticated panel.
# Text files are streamed into the database in bounded batches. Keep streams encrypted at rest.
ATTR=re.compile(r'([A-Za-z][\w-]*)="([^"]*)"')

def infer_kind(group:str,url:str)->str:
    s=group.lower();path=urllib.parse.urlparse(url).path.lower()
    if 'series' in s or 'tv shows' in s or '/series/' in path:return 'show'
    if re.search(r'movies?|films?|cinema|vod',s) or '/movie/' in path or path.endswith(('.mp4','.mkv','.avi','.mov')):return 'movie'
    return 'live'

def save_batch(c,batch):
    c.executemany('INSERT OR IGNORE INTO seen_streams(key) VALUES(?)',[(item[1],) for item in batch])
    c.executemany('''INSERT INTO channels(master_id,stable_key,kind,name,group_name,tvg_id,logo,stream_secret)
        VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(master_id,stable_key) DO UPDATE SET
        kind=excluded.kind,name=excluded.name,group_name=excluded.group_name,
        tvg_id=excluded.tvg_id,logo=excluded.logo,stream_secret=excluded.stream_secret''',batch)

def parse_m3u(stream,master_id:int,c) -> dict:
    total=0
    type_count={'live':0,'movie':0,'show':0}
    batch=[];title='';group='';logo='';tvg=''
    keys_seen={}
    c.execute('BEGIN IMMEDIATE')
    try:
        c.execute('CREATE TEMP TABLE IF NOT EXISTS seen_streams (key TEXT PRIMARY KEY)')
        c.execute('DELETE FROM seen_streams')
        for raw in stream:
            if isinstance(raw,bytes): raw=raw.decode('utf-8','replace')
            line=raw.strip()
            if line.startswith('#EXTINF:'):
                a=dict(ATTR.findall(line))
                title=line.split(',',1)[1].strip() if ',' in line else 'Channel'
                group=a.get('group-title','Live TV')[:100]
                logo=a.get('tvg-logo','')[:600]
                tvg=a.get('tvg-id','')[:180]
            elif line.startswith('#EXTGRP:'):
                group=line.split(':',1)[1].strip()[:100]
            elif line.startswith(('http://','https://')):
                kind=infer_kind(group,line)
                identity=(tvg.strip().lower() if tvg.strip() else (title+'|'+group).strip().lower())
                rawkey=hashlib.sha256((kind+'|'+identity).encode()).hexdigest()
                seq=keys_seen.get(rawkey,0);keys_seen[rawkey]=seq+1
                key=rawkey+(':'+str(seq) if seq else '')
                batch.append((master_id,key,kind,title[:250] or 'Channel',group or 'Live TV',tvg,logo,encrypt(line[:5000])))
                type_count[kind]+=1;total+=1
                if len(batch)>=350:
                    save_batch(c,batch);batch=[]
                title='';group='';logo='';tvg=''
            if total>250000: raise ValueError('Master playlist exceeds 250,000 streams')
        if batch:save_batch(c,batch)
        c.execute('DELETE FROM channels WHERE master_id=? AND stable_key NOT IN (SELECT key FROM seen_streams)',(master_id,))
        c.execute('UPDATE masters SET last_import=?,revision=revision+1 WHERE id=?',(now(),master_id))
        c.execute('COMMIT')
    except Exception:
        c.execute('ROLLBACK');raise
    return {'imported':total,'kinds':type_count}

@app.post('/api/admin/masters/{master_id}/import',dependencies=[Depends(need_admin)])
def import_master(master_id:int,file:UploadFile=File(...)):
    with db() as c:
        if not c.execute('SELECT id FROM masters WHERE id=?',(master_id,)).fetchone():raise HTTPException(404,'Master not found')
        # UploadFile is spooled to disk as needed; ensure total bytes are bounded during reading.
        try:
            size=0
            def lines():
                nonlocal size
                for b in file.file:
                    size+=len(b)
                    if size>100*1024*1024:raise ValueError('Playlist exceeds 100MB upload limit')
                    yield b
            result=parse_m3u(lines(),master_id,c)
        except ValueError as e: raise HTTPException(413,str(e))
    return result

@app.get('/api/admin/masters/{master_id}/channels',dependencies=[Depends(need_admin)])
def list_channels(master_id:int,offset:int=0,limit:int=100,search:str='',kind:str='',market_id:int|None=None):
    if limit<1 or limit>300:raise HTTPException(400,'Limit 1-300')
    clauses=['master_id=?'];params=[master_id]
    if search:clauses.append('(name LIKE ? OR group_name LIKE ? OR tvg_id LIKE ?)');params.extend(['%'+search[:80]+'%']*3)
    if kind:clauses.append('kind=?');params.append(kind)
    if market_id:clauses.append('market_id=?');params.append(market_id)
    clause=' AND '.join(clauses)
    with db() as c:
        rows=[dict(x) for x in c.execute('''SELECT id,kind,name,group_name AS `group`,tvg_id,logo,
            market_id,network,hidden,priority FROM channels WHERE '''+clause+' ORDER BY id LIMIT ? OFFSET ?',params+[limit,offset])]
        count=c.execute('SELECT COUNT(*) FROM channels WHERE '+clause,params).fetchone()[0]
    return {'items':rows,'total':count,'offset':offset,'next_offset':offset+len(rows) if offset+len(rows)<count else None}

@app.patch('/api/admin/channels/{channel_id}',dependencies=[Depends(need_admin)])
def patch_channel(channel_id:int,patch:ChannelPatch):
    values=patch.model_dump(exclude_unset=True)
    if 'group' in values:values['group_name']=values.pop('group')
    if 'hidden' in values:values['hidden']=int(values['hidden'])
    if 'network' in values:values['network']=values['network'] or None
    if not values:return {'updated':False}
    with db() as c:
        if values.get('market_id') is not None and not c.execute('SELECT 1 FROM markets WHERE id=?',(values['market_id'],)).fetchone():
            raise HTTPException(404,'Market not found')
        q='UPDATE channels SET '+', '.join(k+'=?' for k in values)+' WHERE id=?'
        cur=c.execute(q,[*values.values(),channel_id]);
        if not cur.rowcount:raise HTTPException(404,'Channel not found')
        c.execute('UPDATE masters SET revision=revision+1 WHERE id=(SELECT master_id FROM channels WHERE id=?)',(channel_id,))
    return {'updated':True}

@app.post('/api/admin/markets',dependencies=[Depends(need_admin)])
def add_market(payload:MarketCreate):
    if any(not re.fullmatch(r'\d{5}',z) for z in payload.zipcodes):raise HTTPException(400,'ZIP must have five digits')
    with db() as c:
        try: mid=c.execute('INSERT INTO markets(name) VALUES(?)',(payload.name.strip(),)).lastrowid
        except sqlite3.IntegrityError:raise HTTPException(409,'Market name already used')
        for z in set(payload.zipcodes):
            c.execute('INSERT INTO market_zips(zipcode,market_id) VALUES(?,?) ON CONFLICT(zipcode) DO UPDATE SET market_id=excluded.market_id',(z,mid))
    return {'id':mid,'zip_count':len(set(payload.zipcodes))}

@app.put('/api/admin/markets/{market_id}/zipcodes',dependencies=[Depends(need_admin)])
def set_market_zipcodes(market_id:int,payload:ZipAssign):
    if any(not re.fullmatch(r'\d{5}',z) for z in payload.zipcodes):raise HTTPException(400,'ZIP must have five digits')
    with db() as c:
        if not c.execute('SELECT 1 FROM markets WHERE id=?',(market_id,)).fetchone():raise HTTPException(404,'Market not found')
        c.execute('DELETE FROM market_zips WHERE market_id=?',(market_id,))
        for z in set(payload.zipcodes):c.execute('INSERT INTO market_zips(zipcode,market_id) VALUES(?,?) ON CONFLICT(zipcode) DO UPDATE SET market_id=excluded.market_id',(z,market_id))
    return {'id':market_id,'zip_count':len(set(payload.zipcodes))}

@app.post('/api/admin/customers',dependencies=[Depends(need_admin)])
def create_customer(data:CustomerCreate):
    with db() as c:
        if not c.execute('SELECT 1 FROM masters WHERE id=?',(data.master_id,)).fetchone():raise HTTPException(404,'Master not found')
        pin,ph=new_pin(c)
        customer_id=c.execute('''INSERT INTO customers(name,master_id,pin_digest,pin_hint,max_devices,expires_at,created_at)
            VALUES(?,?,?,?,?,?,?)''',(data.name,data.master_id,ph,pin[-2:],data.max_devices,now()+data.days*86400,now())).lastrowid
    return {'customer_id':customer_id,'pin':pin,'max_devices':data.max_devices,'expires_at':now()+data.days*86400,
            'notice':'Shown once; do not send the PIN in public GitHub issues'}

@app.patch('/api/admin/customers/{customer_id}',dependencies=[Depends(need_admin)])
def patch_customer(customer_id:int,changes:CustomerUpdate):
    fields=changes.model_dump(exclude_unset=True)
    if 'days_remaining' in fields:fields['expires_at']=now()+fields.pop('days_remaining')*86400
    if 'enabled' in fields:fields['enabled']=int(fields['enabled'])
    if not fields:return {'updated':False}
    with db() as c:
        if 'master_id' in fields and not c.execute('SELECT 1 FROM masters WHERE id=?',(fields['master_id'],)).fetchone():raise HTTPException(404,'Master not found')
        cur=c.execute('UPDATE customers SET '+','.join(k+'=?' for k in fields)+' WHERE id=?',[*fields.values(),customer_id])
        if not cur.rowcount:raise HTTPException(404,'Customer not found')
    return {'updated':True}

@app.post('/api/admin/customers/{customer_id}/regenerate-pin',dependencies=[Depends(need_admin)])
def regenerate_pin(customer_id:int):
    with db() as c:
        if not c.execute('SELECT 1 FROM customers WHERE id=?',(customer_id,)).fetchone():raise HTTPException(404,'Customer not found')
        pin,ph=new_pin(c)
        c.execute('BEGIN IMMEDIATE')
        c.execute('UPDATE customers SET pin_digest=?,pin_hint=? WHERE id=?',(ph,pin[-2:],customer_id))
        c.execute('DELETE FROM devices WHERE customer_id=?',(customer_id,))
        c.execute('COMMIT')
    return {'pin':pin,'devices_reset':True,'notice':'Old paired devices must enter this PIN again. Master playlist is unchanged.'}

@app.post('/api/admin/customers/{customer_id}/reset-devices',dependencies=[Depends(need_admin)])
def reset_devices(customer_id:int):
    with db() as c:
        if not c.execute('SELECT 1 FROM customers WHERE id=?',(customer_id,)).fetchone():raise HTTPException(404,'Customer not found')
        n=c.execute('DELETE FROM devices WHERE customer_id=?',(customer_id,)).rowcount
    return {'devices_removed':n,'message':'Playlist and customer PIN unchanged; devices must activate again'}

@app.post('/api/device/activate')
def activate(payload:Activate,request:Request):
    # PINs are not URLs and never belong in access logs. Rate limit requests by source IP.
    rate_check('activate:'+str(request.client.host if request.client else 'unknown'),limit=8,window=60)
    ph=digest(payload.pin,'PIN:');dev_hash=digest(payload.device_id,'DEVICE:')
    with db() as c:
        customer=c.execute('SELECT * FROM customers WHERE pin_digest=?',(ph,)).fetchone()
        if not customer or not customer['enabled'] or customer['expires_at']<=now():
            raise HTTPException(401,'PIN invalid or expired')
        prior=c.execute('SELECT id FROM devices WHERE customer_id=? AND device_digest=?',(customer['id'],dev_hash)).fetchone()
        if not prior:
            count=c.execute('SELECT COUNT(*) FROM devices WHERE customer_id=?',(customer['id'],)).fetchone()[0]
            if count>=customer['max_devices']:raise HTTPException(403,'Device limit reached. Contact your provider.')
        token=secrets.token_urlsafe(48)
        th=digest(token,'TOKEN:')
        if prior:
            c.execute('UPDATE devices SET token_digest=?,last_seen=? WHERE id=?',(th,now(),prior['id']))
        else:
            c.execute('INSERT INTO devices(customer_id,device_digest,token_digest,created_at,last_seen) VALUES(?,?,?,?,?)',(customer['id'],dev_hash,th,now(),now()))
        master=c.execute('SELECT name,revision FROM masters WHERE id=?',(customer['master_id'],)).fetchone()
    return {'device_token':token,'customer':customer['name'],'master':master['name'],
            'revision':master['revision'],'expires_at':customer['expires_at'],'device_limit':customer['max_devices']}

@app.get('/api/device/catalog')
def device_catalog(kind:str='live',offset:int=0,limit:int=150, device=Depends(need_device)):
    if kind not in ('live','movie','show') or not (0<=offset<=250000) or not(1<=limit<=250):
        raise HTTPException(400,'Invalid catalog paging')
    with db() as c:
        ch=c.execute('''SELECT id,name,group_name,tvg_id,logo,kind,stream_secret,network FROM channels
            WHERE master_id=? AND kind=? AND hidden=0 ORDER BY priority,id LIMIT ? OFFSET ?''',(device['master_id'],kind,limit+1,offset)).fetchall()
    more=len(ch)>limit;items=[{'id':r['id'],'name':r['name'],'group':r['group_name'],'tvgId':r['tvg_id'],
        'logo':r['logo'],'type':r['kind'],'network':r['network'],'url':decrypt(r['stream_secret'])} for r in ch[:limit]]
    return {'revision':device['revision'],'items':items,'next_offset':offset+limit if more else None}

@app.get('/api/device/locals')
def device_locals(zipcode:str,device=Depends(need_device)):
    if not re.fullmatch(r'\d{5}',zipcode):raise HTTPException(400,'ZIP must have 5 digits')
    with db() as c:
        market=c.execute('SELECT markets.id,markets.name FROM market_zips JOIN markets ON market_zips.market_id=markets.id WHERE market_zips.zipcode=?',(zipcode,)).fetchone()
        if not market:
            return {'zip':zipcode,'market':None,'items':[],'needs_market_mapping':True,
                    'message':'This ZIP is not mapped yet. Your administrator can assign its TV market.'}
        rows=c.execute('''SELECT id,name,group_name,tvg_id,logo,network,stream_secret FROM channels
            WHERE master_id=? AND kind='live' AND market_id=? AND hidden=0
            ORDER BY CASE network WHEN 'ABC' THEN 0 WHEN 'CBS' THEN 1 WHEN 'NBC' THEN 2 WHEN 'FOX' THEN 3 WHEN 'PBS' THEN 4 ELSE 5 END,priority,id LIMIT 13''',
            (device['master_id'],market['id'])).fetchall()
    items=[{'id':r['id'],'name':r['name'],'group':r['group_name'],'tvgId':r['tvg_id'],
             'logo':r['logo'],'network':r['network'],'url':decrypt(r['stream_secret'])} for r in rows[:12]]
    return {'zip':zipcode,'market':market['name'],'items':items,'needs_market_mapping':False,
            'message':'Showing stations actually assigned to your TV market, from your lineup only.'}

@app.get('/')
def home():return FileResponse(ROOT/'static'/'index.html')
app.mount('/static',StaticFiles(directory=str(ROOT/'static')),name='static')

@app.post('/api/admin/markets/import-csv',dependencies=[Depends(need_admin)])
def import_market_zip_csv(file:UploadFile=File(...)):
    """Import admin-supplied, appropriately licensed ZIP,market CSV; never bundle third-party DMA files."""
    import csv
    # File columns zipcode,market; new markets created automatically.
    upload=file.file.read(2_500_001)
    if len(upload)>2_500_000:raise HTTPException(413,'ZIP mapping CSV too large (2.5MB max)')
    try:
        rows=list(csv.DictReader(io.StringIO(upload.decode('utf-8-sig'))))
        if not rows or set(('zipcode','market'))-set(rows[0].keys()):
            raise ValueError('CSV requires zipcode,market column headers')
        validated=[]
        for row in rows:
            zipcode=(row.get('zipcode') or '').strip().zfill(5)
            market=(row.get('market') or '').strip()
            if not re.fullmatch(r'\d{5}',zipcode) or not(2<=len(market)<=80):
                raise ValueError('Invalid ZIP or market value')
            validated.append((zipcode,market))
        with db() as c:
            c.execute('BEGIN IMMEDIATE')
            try:
                for zipcode,market in validated:
                    c.execute('INSERT OR IGNORE INTO markets(name) VALUES(?)',(market,))
                    mid=c.execute('SELECT id FROM markets WHERE name=?',(market,)).fetchone()[0]
                    c.execute('INSERT INTO market_zips(zipcode,market_id) VALUES(?,?) ON CONFLICT(zipcode) DO UPDATE SET market_id=excluded.market_id',(zipcode,mid))
                c.execute('COMMIT')
            except Exception:
                c.execute('ROLLBACK');raise
        return {'zipcodes_imported':len(validated),'markets':len(set(m for _,m in validated))}
    except (ValueError,UnicodeError) as e:raise HTTPException(400,str(e))
