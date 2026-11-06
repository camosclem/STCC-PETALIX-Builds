(() => {
  'use strict';
  const L = window.OpenGuide;
  const native = !!window.AndroidBridge;
  const $ = id => document.getElementById(id);
  const NAV = ['Home','Guide','Sports','Replays','Teams','Favorites','Movies','TV Shows'];
  const DRAWER = ['Home','Live TV','Guide','Sports','Replays','Teams','Favorites','My Locals','Movies','TV Shows','Continue Watching','Settings'];
  const providers = [
    ['Netflix','#891626','#f91c35'],['Prime Video','#173657','#75c7f8'],['Hulu','#113b2b','#45e48b'],
    ['Disney+','#141e4b','#d1e8ff'],['Max','#342057','#c3a0ff'],['Paramount+','#16498e','#e4f3ff'],
    ['Apple TV+','#29292f','#ffffff'],['Peacock','#3c2649','#f2dafe'],['Tubi','#3b2a14','#fbe87e'],['Pluto TV','#263143','#d8e3fe']
  ];
  const colorByProvider = Object.fromEntries(providers.map(([name,bg,fg])=>[name,{bg,fg}]));
  const storageKey = 'petalix-binged-style-v2';
  const defaultState = () => ({tab:'Home', settingsTab:'Playlists',category:'All',search:'',service:'',drawer:false,
    sources:[],channels:[],movies:[],shows:[],epg:{},epgUrl:'',favorites:[],locals:[],followedTeams:[],zip:'',
    lastChannel:'',continueWatching:[],autoplay:true,sounds:true,accent:'#2685ff',profile:'Petalix',sourceStatus:'',libraryFilter:'All'});
  let state = defaultState();
  const pendingXtream = new Map();
  const incomingPlaylist = new Map();
  const esc = value => String(value == null ? '' : value).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const img = value => /^(https?:\/\/|file:\/\/\/android_asset\/)/i.test(value||'') ? esc(value) : '';
  const fmt = val => new Date(val).toLocaleTimeString([], {hour:'numeric',minute:'2-digit'});
  const initials = name => String(name||'TV').split(/\s+/).map(s=>s[0]||'').slice(0,3).join('').toUpperCase();
  const uid = s => L.hash(s);
  const safeUrl = url => /^https?:\/\//i.test(url||'');
  const activeSource = id => state.sources.find(s=>s.id===id);
  const list = () => state.channels;
  const mediaId = item => item.id;
  const hasContent = () => state.channels.length+state.movies.length+state.shows.length>0;
  let toastTimer;
  function toast(message){const el=$('toast');el.textContent=message;el.classList.add('show');clearTimeout(toastTimer);toastTimer=setTimeout(()=>el.classList.remove('show'),4500);}
  function loadState(){try{const saved=JSON.parse(localStorage.getItem(storageKey)||'{}');Object.assign(state,saved);state.tab='Home';state.drawer=false;state.search='';}catch(e){state=defaultState();}}
  function save(){try{const fields=['sources','channels','movies','shows','epgUrl','favorites','locals','followedTeams','zip','lastChannel','continueWatching','autoplay','sounds','accent','profile','sourceStatus'];const snapshot=Object.fromEntries(fields.map(k=>[k,state[k]]));localStorage.setItem(storageKey,JSON.stringify(snapshot));}catch(e){toast('Device storage is full. Refresh your source or remove unused catalogs.');}}
  function button(label,action,arg='',extra=''){return `<button class="btn ${extra}" data-action="${esc(action)}" data-arg="${esc(arg)}">${esc(label)}</button>`;}
  function navButton(label){return `<button class="topnav-item ${state.tab===label?'active':''}" data-action="nav" data-arg="${esc(label)}">${esc(label)}</button>`;}
  function head(title,note=''){return `<div class="pagehead"><h2>${esc(title)}</h2><span class="muted">${esc(note)}</span></div>`;}
  function empty(title,detail,action='',label=''){return `<div class="empty"><div class="empty-icon">◇</div><h2>${esc(title)}</h2><p>${esc(detail)}</p>${action?button(label,action,action==='nav'?'Settings':'','blue'):''}</div>`;}
  function serviceFor(item){let s=(item.group||'')+' '+(item.provider||'');s=s.toLowerCase();
    for(const [label] of providers){const needle=label.toLowerCase();const aliases= label==='Apple TV+'?['apple tv','appletv']:label==='Prime Video'?['prime video','amazon prime','amazon originals']:label==='Paramount+'?['paramount']:label==='Disney+'?['disney','disney plus']:label==='Max'?['hbo','max originals','hbo max']:label==='Pluto TV'?['pluto']:label==='Peacock'?['peacock']:label==='Tubi'?['tubi']:label==='Netflix'?['netflix']:['hulu'];if(s.includes(needle)||aliases.some(x=>s.includes(x)))return label;}
    return 'Other';
  }
  function artCard(item,type='live'){
    const name=esc(item.name||item.title||'Untitled');const poster=img(item.logo||item.poster||'');
    const sourceName=esc(item.group||item.provider||item.source||'');const flagged=state.favorites.includes(mediaId(item));
    const label=type==='live'?'LIVE':type==='show'?'SERIES':type==='movie'?'MOVIE':type==='resume'?'CONTINUE':'WATCH';
    return `<button class="card focusable" data-action="${type==='live'?'play':'details'}" data-id="${esc(item.id)}" data-type="${esc(type)}" title="${name}">
      <span class="card-art" style="background:linear-gradient(110deg,${type==='movie'?'#30202f':type==='show'?'#15233b':'#171c2c'},#101119 62%,#2b2238)">${poster?`<img loading="lazy" src="${poster}" alt="" onerror="this.style.display='none'">`:`<span class="initials">${esc(initials(name))}</span>`}<span class="tag ${type==='live'?'live':''}">${label}</span></span>
      <span class="card-title">${name}${flagged?' ★':''}</span><span class="card-sub">${sourceName||'Petalix'}${item.season?' · '+esc(item.season):''}</span></button>`;
  }
  function rail(items,type){return `<div class="tiles">${items.slice(0,26).map(i=>artCard(i,type)).join('')}</div>`;}
  function providerTile(label,mode){const d=colorByProvider[label]||{bg:'#303040',fg:'#ffffff'};const items=(mode==='Movies'?state.movies:state.shows).filter(i=>serviceFor(i)===label);
    return `<button class="service focusable" style="--service-bg:${d.bg};color:${d.fg}" data-action="service" data-arg="${esc(label)}" data-mode="${esc(mode)}"><span class="service-aura" aria-hidden="true"></span><span class="service-orbit" aria-hidden="true"></span><strong class="service-title">${esc(label)}</strong><span class="service-count">${items.length?items.length+' titles':'Browse collection'}</span></button>`;
  }
  function providerGrid(mode){return `<div class="service-grid">${providers.map(p=>providerTile(p[0],mode)).join('')}
    <button class="service focusable" style="--service-bg:#2b303b" data-action="service" data-arg="Other" data-mode="${esc(mode)}"><span class="service-aura" aria-hidden="true"></span><span class="service-orbit" aria-hidden="true"></span><strong class="service-title">Other Collections</strong><span class="service-count">Unsorted titles from your provider</span></button></div>`;}
  function home(){
    const featured=state.channels.find(c=>c.id===state.lastChannel)||state.channels[0];
    const heroName=featured?featured.name:'Petalix TV';const featureDesc=featured?`${featured.group||'Live TV'} · Watch live from your connected playlist`:'Live TV, your movies, and your shows. All together in one place.';
    const logo=featured?img(featured.logo):'';
    const hero=`<section class="hero"><div class="hero-media">${logo?`<img src="${logo}" alt="" onerror="this.style.display='none'">`:featured?`<span class="hero-logo-fallback">${esc(heroName)}</span>`:`<img src="petalix-logo.png" alt="Petalix">`}</div><div class="hero-text"><div class="hero-eyebrow">${featured?'Featured · Live TV':'WELCOME TO PETALIX'}</div><h1>${esc(heroName)}</h1><p>${esc(featureDesc)}</p><div class="hero-actions">${featured?button('▶  Watch','play','', 'blue'):button('Connect Your TV','nav','Settings','blue')}${featured?button(state.favorites.includes(featured.id)?'★ Favorited':'☆ Add to Favorites','favorite',featured.id):''}${button('Guide','nav','Guide')}</div></div></section>`;
    const resume=state.continueWatching.map(id=>[...state.movies,...state.shows].find(x=>x.id===id)).filter(Boolean);
    return `${hero}${state.sources.length?'':`<div class="info-strip">Connect an M3U playlist or Xtream Codes account in Settings to see your channels and on-demand catalog.</div>`}
      ${state.channels.length?`${head('Live TV',state.channels.length+' channels')}${rail(state.channels,'live')}`:''}
      ${head('Streaming Services','Your provider collections')}${providerGrid('Movies')}
      ${resume.length?`${head('Continue Watching',resume.length+' saved')}${rail(resume,'resume')}`:''}
      ${state.movies.length?`${head('Movies',state.movies.length+' titles')}${rail(state.movies,'movie')}`:''}
      ${state.shows.length?`${head('TV Shows',state.shows.length+' shows')}${rail(state.shows,'show')}`:''}
      ${state.channels.length?`${head('Sports','From your channels')}${rail(state.channels.filter(L.isSports),'live')}`:''}`;
  }
  function live(){const all=state.channels.filter(matches);const groupNames=Array.from(new Set(all.map(x=>x.group||'Other'))).slice(0,60);const visible=state.category==='All'?all:all.filter(x=>x.group===state.category);
    return `${head('Live TV',all.length+' channels')}${chipRow(['All',...groupNames],state.category,'filter')}${visible.length?rail(visible,'live'):empty('No channels',state.sources.length?'Try another category or search.':'Add your M3U or Xtream Codes provider under Settings.','nav','Open Settings')}`;}
  function matches(item){const q=state.search.trim().toLowerCase();return !q||[item.name,item.title,item.group,item.provider,item.source].join(' ').toLowerCase().includes(q);}
  function chipRow(items,selected,action){return `<div class="chips">${items.map(x=>`<button class="chip ${x===selected?'active':''}" data-action="${esc(action)}" data-arg="${esc(x)}">${esc(x)}</button>`).join('')}</div>`;}
  function sports(){const sports=state.channels.filter(L.isSports).filter(matches);const names=['All sports','Football','Basketball','Hockey','Soccer','Fighting','Tennis','Other'];const filtered=state.libraryFilter==='All sports'?sports:sports.filter(c=>state.libraryFilter==='Other'?true:(c.name+' '+c.group).toLowerCase().includes(state.libraryFilter.toLowerCase()));
    return `${chipRow(names,state.libraryFilter==='All'? 'All sports':state.libraryFilter,'sport-filter')}${head('Sports channels',sports.length+' available')}${sports.length?rail(filtered,'live'):empty('No sports channels','Your connected source does not include channels marked as sports.')}${head('Teams','Follow teams in your guide')}${chipRow(['NFL','NBA','NHL','MLB','NCAA','MLS'],state.libraryFilter,'league')}`;}
  function replays(){return `${head('Replays','Provider dependent')}${empty('No replays available','A TV guide or playlist alone cannot create recordings. Replay and catch-up will appear only when your connected service supplies them.')}`;}
  function teams(){return `${head('Teams','Favorites and leagues')}${chipRow(['NFL','NCAA','NBA','NHL','MLB','MLS','Premier League','Champions League'],state.libraryFilter==='All'?'NFL':state.libraryFilter,'league')}${empty('Follow your teams','This test build displays sports channels from your provider. Team schedules and game cards require a separate verified sports-data feed.')}`;}
  function favorites(){const collection=[...state.channels,...state.movies,...state.shows].filter(i=>state.favorites.includes(i.id)).filter(matches);return collection.length?`${head('Favorites',collection.length+' saved')}${rail(collection.filter(i=>state.channels.some(c=>c.id===i.id)),'live')}${rail(collection.filter(i=>state.movies.some(c=>c.id===i.id)),'movie')}${rail(collection.filter(i=>state.shows.some(c=>c.id===i.id)),'show')}`:empty('No favorites yet','Hold OK on a channel, or open a movie or show and select Add to Favorites.');}
  function streaming(mode){const service=state.service;const items=(mode==='Movies'?state.movies:state.shows).filter(matches);if(!service){return `${head(mode,`${items.length} available from your sources`)}<p class="muted smalltext">Choose a streaming-service collection. Tiles organize the movies and shows in your own catalog; they do not sign into those separate services.</p>${providerGrid(mode)}${!items.length?empty(`No ${mode.toLowerCase()} loaded`,'Add an Xtream Codes login with VOD access or import a playlist containing movie/show entries.') :''}`;}
    const selected=items.filter(i=>serviceFor(i)===service);const styles=colorByProvider[service]||{bg:'#1b1d27',fg:'#f9f9fc'};const genres=[...new Set(selected.map(x=>x.genre||x.group||'All').filter(Boolean))].slice(0,12);
    return `<div style="padding-top:25px">${button('‹  All services','back-service','','ghost')}<section class="hero" style="min-height:260px;margin-top:20px;background:radial-gradient(ellipse at 20% 35%,${styles.bg},#050509 75%)"><div class="hero-media"><span class="hero-logo-fallback">${esc(service)}</span></div><div class="hero-text"><div class="hero-eyebrow">PETALIX COLLECTION</div><h1>${esc(service)}</h1><p>${selected.length} titles from your connected ${mode==='Movies'?'movie':'TV show'} sources</p></div></section>
    ${selected.length?`${head('Featured',selected.length+' titles')}${rail(selected,mode==='Movies'?'movie':'show')}${genres.slice(0,7).map(g=>`${head(g)}${rail(selected.filter(x=>(x.genre||x.group||'All')===g),mode==='Movies'?'movie':'show')}`).join('')}`:empty('No matching titles','Your source has no items labeled '+service+'. Check the provider categories or use Other Collections.')}</div>`;
  }
  function continueWatching(){const items=state.continueWatching.map(id=>[...state.movies,...state.shows].find(x=>x.id===id)).filter(Boolean);return `${head('Continue Watching',items.length+' saved')}${items.length?rail(items,'resume'):empty('Nothing started yet','Select a movie or episode to begin your watch history.')}`;}
  function locals(){const eligible=state.channels.filter(L.isLocal);const chosen=state.channels.filter(c=>state.locals.includes(c.id));return `${head('My Locals',chosen.length+' selected')}<div class="settings-box"><div class="setting-row"><div><strong>ZIP code</strong><small>Saved on this device. Automatic station matching is not included yet.</small></div><input id="zipcode" placeholder="28601" value="${esc(state.zip)}" maxlength="5" inputmode="numeric"></div><div class="setting-actions">${button('Save ZIP','save-zip','','blue')}</div></div>${chosen.length?`${head('My channels')}${rail(chosen,'live')}`:''}${head('Find local channels',eligible.length+' possible matches')}<div class="service-grid">${eligible.slice(0,80).map(c=>`<button class="service" style="height:105px" data-action="toggle-local" data-id="${esc(c.id)}"><strong>${esc(c.name)}</strong><span class="service-count">${state.locals.includes(c.id)?'✓ Included':'+ Add to locals'}</span></button>`).join('')}</div>${eligible.length?'':empty('No local stations detected','Your M3U group labels may differ. You can still favorite channels in Live TV.')}`;}
  function guide(){const chans=state.channels.filter(matches).slice(0,70);if(!chans.length)return empty('Guide is empty','Add an M3U or Xtream Codes account in Settings. XMLTV is optional but needed for program names.');
    const stamp=Date.now(),start=new Date(stamp);start.setMinutes(Math.floor(start.getMinutes()/30)*30,0,0);const slots=Array.from({length:6},(_,i)=>start.getTime()+i*30*60000);const pct=(stamp-start.getTime())/(180*60000);const line=155 + pct*(6*260);
    const programs=c=>state.epg[(c.tvgId||'').toLowerCase()]||state.epg[(c.name||'').toLowerCase()]||[];
    let lastCategory='';let rows='';for(const c of chans){if(c.group!==lastCategory){lastCategory=c.group;rows+=`<div class="guide-category">${esc(String(lastCategory||'LIVE TV').toUpperCase())}</div>`;}
      const safeLogo=img(c.logo);const items=programs(c);rows+=`<div class="guide-row"><button class="guide-name" data-action="play" data-id="${esc(c.id)}">${safeLogo?`<img src="${safeLogo}" alt="${esc(c.name)}" onerror="this.style.display='none'">`:`<span>${esc(initials(c.name))}</span>`}</button>${slots.map(t=>{const p=items.find(p=>p.start<=t && p.stop>t);return `<button class="guide-program ${p&&p.start<=stamp&&p.stop>stamp?'current':''}" data-action="play" data-id="${esc(c.id)}"><strong>${esc(p?p.title:'No guide information')}</strong><small>${esc(p?fmt(p.start)+' · '+c.name:c.name)}</small></button>`;}).join('')}<div class="guide-nowline" style="left:${line}px"></div></div>`;}
    return `<div class="guide-wrap"><div class="guide-top"><div class="guide-header-label">● Now</div>${slots.map(t=>`<div>${fmt(t)}</div>`).join('')}</div>${rows}</div>`;
  }
  function settings(){const labels=['Playlists','Profile','Playback','Captions','Video Player','Appearance','About'];const menu=`<div class="settings-menu">${labels.map((s,i)=>`<button class="${state.settingsTab===s?'selected':''}" data-action="settings-tab" data-arg="${esc(s)}"><span>${['▣','◉','▶','CC','▰','◐','ⓘ'][i]}</span>${esc(s)}</button>`).join('')}</div>`;
    return `<div class="settings-layout">${menu}<div class="settings-pane">${settingsPage()}</div></div>`;}
  function settingsPage(){const page=state.settingsTab;
    if(page==='Playlists')return `<h1>Playlists & Sources</h1><p>Import your own M3U URL/file or sign in to a supported Xtream Codes service. Streams aren't included with Petalix.</p>
      <div class="settings-group"><label>M3U PLAYLIST</label><div class="settings-box"><div class="setting-field"><label for="m3u-name">Source name</label><input id="m3u-name" placeholder="My IPTV"></div><div class="setting-field"><label for="m3u-url">M3U playlist URL</label><input id="m3u-url" placeholder="https://provider.example/playlist.m3u"></div><div class="setting-actions">${button('Add M3U','add-m3u','','blue')}${button('Import M3U file','pick-file')}${button('Refresh all sources','refresh')}</div></div></div>
      <div class="settings-group"><label>XTREAM CODES LOGIN</label><div class="settings-box"><div class="setting-field"><label for="xc-name">Connection name</label><input id="xc-name" placeholder="Living Room IPTV"></div><div class="setting-field"><label for="xc-server">Server URL (including port if required)</label><input id="xc-server" placeholder="http://provider.example:8080"></div><div class="setting-field"><label for="xc-user">Username</label><input id="xc-user" placeholder="Username" autocapitalize="off" autocomplete="off"></div><div class="setting-field"><label for="xc-pass">Password</label><input id="xc-pass" type="password" placeholder="Password" autocomplete="off"></div><div class="setting-actions">${button('Connect Xtream Codes','connect-xc','','blue')}</div><div class="status-line">Live TV, movies, series, and episode lists are imported from your authorized provider. Credentials stay on this device; they are sent to your provider for login.</div></div></div>
      <div class="settings-group"><label>PROGRAM GUIDE</label><div class="settings-box"><div class="setting-field"><label for="epg-url">XMLTV EPG URL</label><input id="epg-url" placeholder="https://provider.example/xmltv.xml.gz" value="${esc(state.epgUrl)}"></div><div class="setting-actions">${button('Load EPG','load-epg','','blue')}</div></div></div>
      <div class="settings-group"><label>CONNECTED SOURCES</label><div class="settings-box">${state.sources.length?state.sources.map(s=>`<div class="sourcebox"><div><strong>${esc(s.name)} · ${s.type==='xtream'?'Xtream':'M3U'}</strong><small>${esc((s.server||s.url||'Local playlist file').replace(/\?.*/,''))}</small></div><button data-action="remove-source" data-id="${esc(s.id)}">Remove</button></div>`).join(''):'<div class="setting-row"><small>No sources connected yet</small></div>'}</div></div><p class="smalltext">${esc(state.sourceStatus)}</p>`;
    if(page==='Profile')return `<h1>Profile</h1><p>Your selected channels and collections are saved on this device.</p><div class="settings-group"><label>WHO'S WATCHING</label><div class="settings-box"><div class="setting-row"><div><strong>Display name</strong><small>Stored locally on this device</small></div><input id="profile-name" value="${esc(state.profile)}"></div><div class="setting-actions">${button('Save profile','save-profile','','blue')}</div></div></div>`;
    if(page==='Playback')return `<h1>Playback</h1><p>Control how live streams start.</p><div class="settings-group"><label>STARTING A CHANNEL</label><div class="settings-box"><div class="setting-row"><div>Autoplay on select<small>Pressing OK plays a channel using the built-in Media3 player.</small></div><button class="switch ${state.autoplay?'on':''}" data-action="toggle-autoplay" aria-label="Toggle autoplay"></button></div><div class="setting-row"><div>Source switching<small>Automated backup feed switching isn't available in this build.</small></div><span class="muted">Planned</span></div></div></div>`;
    if(page==='Appearance')return `<h1>Appearance</h1><p>Petalix uses a midnight background inspired by the layout you provided.</p><div class="settings-group"><label>TV REMOTE & MOTION</label><div class="settings-box"><div class="setting-row"><div>Navigation sounds<small>Original soft selection tones when you move through Petalix; can be muted.</small></div><button class="switch ${state.sounds?'on':''}" data-action="toggle-sounds" aria-label="Toggle navigation sounds"></button></div><div class="setting-row">Animated service tiles<small>Animated visual highlights on streaming-service tiles; reduced motion follows your device setting.</small></div></div></div><div class="settings-group"><label>ACCENT COLOR</label><div class="settings-box"><div class="setting-row"><div>Selection color<small>Buttons and active navigation.</small></div><div class="chips">${['#2685ff','#f43362','#ba57ed','#f39c12','#45ca6f'].map(c=>`<button style="background:${c};width:33px;height:33px;border-radius:50%;border:${state.accent===c?'3px solid white':'1px solid #555'}" data-action="accent" data-arg="${c}" title="${c}"></button>`).join('')}</div></div></div></div>`;
    if(page==='Captions')return `<h1>Captions</h1><p>Captions are provided by the playback engine if the stream has caption tracks.</p><div class="settings-box"><div class="setting-row">Closed captions<small>Use the native player subtitle button when available.</small></div></div>`;
    if(page==='Video Player')return `<h1>Video Player</h1><p>Live TV and VOD playback use Media3 ExoPlayer.</p><div class="settings-box"><div class="setting-row">Picture size<small>Currently uses Fit.</small></div><div class="setting-row">Remote navigation<small>Use Back to return to Petalix.</small></div></div>`;
    return `<h1>About</h1><p>Petalix TV — Binged-style independent test build.</p><div class="settings-box"><div class="setting-row">Build <span class="muted">0.2 · Test</span></div><div class="setting-row">Channels <span class="muted">${state.channels.length.toLocaleString()}</span></div><div class="setting-row">Movies <span class="muted">${state.movies.length.toLocaleString()}</span></div><div class="setting-row">TV Shows <span class="muted">${state.shows.length.toLocaleString()}</span></div><div class="setting-row">No channels are bundled with the app.</div><div class="setting-actions">${button('Clear all local data','reset')}</div></div>`;
  }
  function drawNav(){ $('topnav').innerHTML=NAV.map(navButton).join('');$('drawer-nav').innerHTML=DRAWER.map((n,i)=>`<button class="drawer-item ${state.tab===n?'active':''}" data-action="nav" data-arg="${esc(n)}"><span class="symbol">${['⌂','▣','▤','◈','↶','☆','♡','⌖','▰','▦','◉','⚙'][i]}</span>${esc(n)}</button>`).join('');const groups=Array.from(new Set(state.channels.map(c=>c.group||'Other'))).slice(0,30);$('drawer-groups').innerHTML=`<div class="drawer-label">CATEGORIES</div>${groups.map((g,i)=>`<button class="drawer-item" data-action="group" data-arg="${esc(g)}"><span class="symbol" style="color:${['#9bbd6a','#4aaabe','#d96b87','#72b0ec','#d49b6d'][i%5]}">●</span>${esc(g)}<span class="qty">${state.channels.filter(c=>c.group===g).length}</span></button>`).join('')}`; }
  function render(preserveScroll=false){const y=$('viewport').scrollTop;document.documentElement.style.setProperty('--accent',state.accent);drawNav();$('profile-button').textContent=(state.profile||'P')[0].toUpperCase();
    const pages={'Home':home,'Live TV':live,'Guide':guide,'Sports':sports,'Replays':replays,'Teams':teams,'Favorites':favorites,'Movies':()=>streaming('Movies'),'TV Shows':()=>streaming('TV Shows'),'Continue Watching':continueWatching,'My Locals':locals,'Settings':settings};$('screen').innerHTML=(pages[state.tab]||home)();
    $('drawer').classList.toggle('open',state.drawer);$('scrim').hidden=!state.drawer;if(!preserveScroll)$('viewport').scrollTop=0;else $('viewport').scrollTop=y;
  }
  function nav(where){if(where==='Series')where='TV Shows';if(where==='Movies'||where==='TV Shows')state.service='';if(where==='Settings'&&!state.settingsTab)state.settingsTab='Playlists';state.tab=where;state.drawer=false;state.category='All';state.libraryFilter='All';render();}
  function firstChannel(){return state.channels.find(c=>c.id===state.lastChannel)||state.channels[0];}
  function play(url,name,id){if(!safeUrl(url)){toast('This item does not contain a playable stream URL.');return;}if(id){state.lastChannel=id;save();}if(native){window.AndroidBridge.playChannel(url,name||'Petalix TV');return;}showDetails(`<h2>${esc(name||'Player')}</h2><p>Browser preview does not provide native IPTV playback. Install the APK for Media3 playback.</p>${button('Close','close-details')}`);}
  function showDetails(html){$('details-body').innerHTML=html;$('details').classList.remove('hidden');$('details-close').focus();}
  function closeDetails(){$('details').classList.add('hidden');$('details-body').innerHTML='';}
  function detail(id,type){const item=[...state.movies,...state.shows].find(x=>x.id===id);if(!item){toast('Title not found');return;}
    const isShow=item.type==='show';const art=img(item.poster||item.logo);const fav=state.favorites.includes(item.id);const plot=item.plot||`From your connected ${esc(item.source||'provider')} catalog.`;
    const body=`<div class="detail-hero">${art?`<img class="detail-image" src="${art}" alt="">`:'<div class="detail-image"></div>'}<div class="detail-meta"><span class="hero-eyebrow">${isShow?'TV SHOW':'MOVIE'} · ${esc(serviceFor(item))}</span><h1>${esc(item.name)}</h1><p class="muted">${esc(plot)}</p><p class="smalltext">${esc(item.genre||item.group||'')} ${item.rating?' · ★ '+esc(item.rating):''}</p><div class="hero-actions">${!isShow?button('▶ Play','play-movie',item.id,'blue'):button('Browse episodes','load-series',item.id,'blue')}${button(fav?'★ Remove Favorite':'☆ Add Favorite','favorite-media',item.id)}</div></div></div><div id="episode-section"></div>`;
    showDetails(body);
  }
  function markContinue(id){state.continueWatching=[id,...state.continueWatching.filter(x=>x!==id)].slice(0,60);save();}
  function playMovie(item){if(!item)return;markContinue(item.id);play(item.url||mediaUrl(item),item.name);}
  function mediaUrl(item){const source=activeSource(item.sourceId);if(item.url)return item.url;if(!source||source.type!=='xtream')return '';const base=source.server.replace(/\/$/,'');const user=encodeURIComponent(source.user),pass=encodeURIComponent(source.pass);
    const id=encodeURIComponent(item.streamId);const ext=String(item.ext||'mp4').replace(/[^a-z0-9]/gi,'')||'mp4';return `${base}/${item.type==='episode'?'series':'movie'}/${user}/${pass}/${id}.${ext}`;}
  function toggleFav(id){state.favorites=state.favorites.includes(id)?state.favorites.filter(x=>x!==id):[...state.favorites,id];save();}
  function toggleLocal(id){state.locals=state.locals.includes(id)?state.locals.filter(x=>x!==id):[...state.locals,id];save();render(true);}
  function classifyPlaylist(text,name){const parsed=L.parseM3U(text,name);const live=[],movie=[],show=[];
    for(const c of parsed){const g=(c.group||'').toLowerCase();const u=(c.url||'').split('?')[0].toLowerCase();const ep=(c.name||'').match(/(.+?)[ ._-]*s(\d{1,2})[ ._-]*e(\d{1,3})/i);
      const seriesHint=/series|tv shows|episodes/.test(g)||/\/series\//.test(u);
      const movieHint=/movies?|films?|cinema|vod/.test(g)||/\/movie\//.test(u)||/\.(mp4|mkv|avi|mov|wmv)$/.test(u);
      if(seriesHint){show.push({...c,type:'show',season:ep?`S${ep[2]}E${ep[3]}`:'',episodeOf:ep?ep[1].trim():c.name});}
      else if(movieHint){movie.push({...c,type:'movie',poster:c.logo});}
      else live.push(c);
    }return {live,movie,show};
  }
  function applyPlaylist(content,name,url='',sourceId=''){try{const {live,movie,show}=classifyPlaylist(content,name);if(!sourceId){const old=state.sources.find(s=>s.name===name);sourceId=old?old.id:uid(name+'-'+Date.now());if(!old)state.sources.push({id:sourceId,name,type:'m3u',url});}
      state.channels=state.channels.filter(c=>c.sourceId!==sourceId).concat(live.map(c=>({...c,sourceId})));state.movies=state.movies.filter(c=>c.sourceId!==sourceId).concat(movie.map(c=>({...c,sourceId})));state.shows=state.shows.filter(c=>c.sourceId!==sourceId).concat(show.map(c=>({...c,sourceId})));
      state.sourceStatus=`${live.length} live · ${movie.length} movies · ${show.length} show entries from ${name}`;save();nav('Home');toast(state.sourceStatus);
    }catch(e){toast(e.message||'Invalid M3U playlist');}}
  window.onNativePlaylist=(content,name)=>{const source=state.sources.find(s=>s.name===name);applyPlaylist(content,name,source&&source.url||'');};
  // The Android layer streams large provider lists as compact JSON batches, never a giant M3U string.
  window.onNativePlaylistChunk=(batch,name,last,skipped)=>{
    let entries=incomingPlaylist.get(name);
    if(!entries){entries=[];incomingPlaylist.set(name,entries);}
    if(Array.isArray(batch))entries.push(...batch);
    if(!last)return;
    incomingPlaylist.delete(name);
    const source=state.sources.find(s=>s.name===name);
    const sourceId=source?.id||uid(name+'-'+Date.now());
    if(!source)state.sources.push({id:sourceId,name,type:'m3u',url:''});
    const mapped=entries.map(item=>{
      const type=item.type==='show'?'show':item.type==='movie'?'movie':'live';
      return {...item,id:uid(name+'|'+item.url),sourceId,source:name,
        ...(type==='movie'?{poster:item.logo}:{}),type};
    });
    const live=mapped.filter(i=>i.type==='live'),movie=mapped.filter(i=>i.type==='movie'),show=mapped.filter(i=>i.type==='show');
    state.channels=state.channels.filter(i=>i.sourceId!==sourceId).concat(live);
    state.movies=state.movies.filter(i=>i.sourceId!==sourceId).concat(movie);
    state.shows=state.shows.filter(i=>i.sourceId!==sourceId).concat(show);
    state.sourceStatus=`Imported ${live.length} live, ${movie.length} movies, ${show.length} shows`+
      (skipped?` · ${skipped.toLocaleString()} entries excluded to fit this device`:'');
    save();nav('Home');toast(state.sourceStatus);
  };

  window.onNativeEpg=(content)=>applyEpg(content);
  window.onNativeError=(message)=>{state.sourceStatus=String(message);toast('Connection error: '+message);if(state.tab==='Settings')render(true);};
  function applyEpg(content){try{const doc=new DOMParser().parseFromString(content,'application/xml');if(doc.querySelector('parsererror'))throw Error('Malformed XMLTV');const rows=doc.querySelectorAll('programme');const out={};for(let i=0;i<rows.length&&i<90000;i++){const e=rows[i],id=(e.getAttribute('channel')||'').toLowerCase();if(!id)continue;const from=L.parseXmlTvDate(e.getAttribute('start')),to=L.parseXmlTvDate(e.getAttribute('stop'));if(!from||!to)continue;const p={start:from.getTime(),stop:to.getTime(),title:e.querySelector('title')?.textContent||'Program'};(out[id]??=[]).push(p);}for(const list of Object.values(out))list.sort((a,b)=>a.start-b.start);state.epg=out;toast('Guide loaded: '+Object.keys(out).length+' channels');render(true);}catch(e){toast('Guide error: '+e.message);}}
  function fetchSource(url,callback){if(native){throw Error('Use Android import interface');}return fetch(url).then(r=>{if(!r.ok)throw Error('HTTP '+r.status);return r.text();}).then(callback);}
  function xcApi(source,action,ref=''){const key=`${source.id}|${action||'auth'}|${ref}`;const params=new URLSearchParams({username:source.user,password:source.pass});if(action)params.set('action',action);if(ref){if(action==='get_series_info')params.set('series_id',ref);if(action==='get_vod_info')params.set('vod_id',ref);}const url=source.server.replace(/\/$/,'')+'/player_api.php?'+params.toString();
    if(native){window.AndroidBridge.importXtream(source.server,source.user,source.pass,action,ref,key);}
    else fetchSource(url,response=>window.onNativeXtream(response,key)).catch(e=>window.onNativeError(e.message+' (browser CORS may restrict this preview)'));
  }
  function connectXtream(){const name=$('xc-name')?.value.trim()||'My Xtream',server=$('xc-server')?.value.trim().replace(/\/(player_api\.php|get\.php).*$/i,'').replace(/\/$/,'');const user=$('xc-user')?.value.trim(),pass=$('xc-pass')?.value||'';
    if(!/^https?:\/\/[^/\s]+/i.test(server)||!user||!pass)return toast('Enter your server URL, username and password.');const id=uid(name+'|'+server+'|'+user);const source={id,name,server,user,pass,type:'xtream'};pendingXtream.set(id,source);state.sourceStatus='Connecting to '+name+'…';toast(state.sourceStatus);xcApi(source,'','');}
  function storeXtreamSource(source){const old=activeSource(source.id);if(old)Object.assign(old,source);else state.sources.push(source);save();}
  function xcEntries(source,action,data){if(!Array.isArray(data)){toast('Unexpected Xtream response for '+action);return;}const n=source.name;
    if(action==='get_live_categories'){source.liveCategories=Object.fromEntries(data.map(x=>[String(x.category_id),x.category_name||'Live TV']));storeXtreamSource(source);return;}
    if(action==='get_vod_categories'){source.vodCategories=Object.fromEntries(data.map(x=>[String(x.category_id),x.category_name||'Movies']));storeXtreamSource(source);return;}
    if(action==='get_series_categories'){source.seriesCategories=Object.fromEntries(data.map(x=>[String(x.category_id),x.category_name||'Series']));storeXtreamSource(source);return;}
    if(action==='get_live_streams'){const added=data.map(x=>({id:uid(source.id+'|live|'+x.stream_id),sourceId:source.id,source:n,name:x.name||'Channel',group:(source.liveCategories||{})[String(x.category_id)]||'Live TV',tvgId:x.epg_channel_id||'',logo:x.stream_icon||'',number:x.num||'',url:x.direct_source&&safeUrl(x.direct_source)?x.direct_source:`${source.server}/live/${encodeURIComponent(source.user)}/${encodeURIComponent(source.pass)}/${encodeURIComponent(x.stream_id)}.ts`,type:'live'}));state.channels=state.channels.filter(x=>x.sourceId!==source.id).concat(added);}
    if(action==='get_vod_streams'){const added=data.map(x=>({id:uid(source.id+'|vod|'+x.stream_id),sourceId:source.id,source:n,streamId:x.stream_id,name:x.name||'Movie',group:(source.vodCategories||{})[String(x.category_id)]||'Movies',poster:x.stream_icon||'',type:'movie',ext:x.container_extension||'mp4',rating:x.rating||'',year:x.year||'',plot:''}));state.movies=state.movies.filter(x=>x.sourceId!==source.id).concat(added);}
    if(action==='get_series'){const added=data.map(x=>({id:uid(source.id+'|series|'+x.series_id),sourceId:source.id,source:n,seriesId:x.series_id,name:x.name||'TV Show',group:(source.seriesCategories||{})[String(x.category_id)]||'Series',poster:x.cover||'',type:'show',plot:x.plot||'',rating:x.rating||'',genre:x.genre||''}));state.shows=state.shows.filter(x=>x.sourceId!==source.id).concat(added);}
    state.sourceStatus=`${state.channels.length} live · ${state.movies.length} movies · ${state.shows.length} shows imported`;save();if(state.tab==='Settings')render(true);
  }
  window.onNativeXtream=(raw,key)=>{try{const [sourceId,action,ref]=String(key).split('|');const source=pendingXtream.get(sourceId)||activeSource(sourceId);if(!source)return;const data=JSON.parse(raw);
      if(action==='auth'){if(!data||!data.user_info||String(data.user_info.auth)==='0'||/expired|disabled|banned/i.test(data.user_info.status||'')){throw Error('Xtream login rejected by provider');}
        storeXtreamSource(source);pendingXtream.delete(sourceId);state.sourceStatus='Xtream connected: '+source.name;save();toast(state.sourceStatus);
        // Categories are fetched first for correct provider labels; stream lists arrive afterward.
        xcApi(source,'get_live_categories');xcApi(source,'get_vod_categories');xcApi(source,'get_series_categories');
        xcApi(source,'get_live_streams');xcApi(source,'get_vod_streams');xcApi(source,'get_series');return;
      }
      if(action==='get_series_info'){renderEpisodeDetail(source,ref,data);return;}
      xcEntries(source,action,data);
    }catch(e){state.sourceStatus='Xtream error: '+e.message;toast(state.sourceStatus);}};
  function renderEpisodeDetail(source,seriesId,data){const item=state.shows.find(x=>x.sourceId===source.id && String(x.seriesId)===String(seriesId));if(!item)return;const seasons=data.episodes||{};const html=Object.entries(seasons).sort(([a],[b])=>Number(a)-Number(b)).map(([season,eps])=>`<h3>Season ${esc(season)}</h3><div class="episodes">${(Array.isArray(eps)?eps:[]).map(ep=>{const eid=String(ep.id||ep.stream_id||'');const ext=String(ep.container_extension||'mp4');return `<button class="episode" data-action="play-episode" data-id="${esc(eid)}" data-source="${esc(source.id)}" data-ext="${esc(ext)}" data-parent="${esc(item.id)}"><strong>${esc(ep.title||'Episode '+(ep.episode_num||''))}</strong><small>Episode ${esc(ep.episode_num||'')}</small></button>`;}).join('')}</div>`).join('');
    const target=$('episode-section');if(target)target.innerHTML=`${html||'<p class="muted">No episodes returned by provider.</p>'}`;
  }
  function refresh(){for(const source of state.sources){if(source.type==='xtream'){xcApi(source,'get_live_categories');xcApi(source,'get_vod_categories');xcApi(source,'get_series_categories');xcApi(source,'get_live_streams');xcApi(source,'get_vod_streams');xcApi(source,'get_series');}
      else if(source.type==='m3u'&&source.url){if(native)window.AndroidBridge.importPlaylist(source.url,source.name);else fetchSource(source.url,s=>applyPlaylist(s,source.name,source.url,source.id)).catch(e=>toast(e.message));}}
    toast('Refreshing connected sources…');}
  function handle(action,buttonElement){const arg=buttonElement.dataset.arg||'',id=buttonElement.dataset.id||'';
    if(action==='nav'){nav(arg);return;}if(action==='menu'){state.drawer=!state.drawer;render(true);return;}if(action==='group'){state.category=arg;nav('Live TV');state.category=arg;render();return;}
    if(action==='filter'){state.category=arg;render(true);return;}if(action==='sport-filter'||action==='league'){state.libraryFilter=arg;render(true);return;}
    if(action==='settings-tab'){state.settingsTab=arg;render();return;}if(action==='service'){state.tab=buttonElement.dataset.mode||'Movies';state.service=arg;render();return;}
    if(action==='back-service'){state.service='';render();return;}
    if(action==='play'){const item=state.channels.find(x=>x.id===id)||firstChannel();if(item)play(item.url,item.name,item.id);else toast('Connect a playlist first');return;}
    if(action==='details'){detail(id,buttonElement.dataset.type);return;}
    if(action==='play-movie'){const item=state.movies.find(x=>x.id===arg);if(item)playMovie(item);return;}
    if(action==='load-series'){const item=state.shows.find(x=>x.id===arg);if(!item)return;markContinue(item.id);const s=activeSource(item.sourceId);if(s?.type==='xtream')xcApi(s,'get_series_info',String(item.seriesId));else {const x=$('episode-section');if(x){x.innerHTML=`<h3>Available playlist entries</h3><div class="episodes">${state.shows.filter(y=>y.episodeOf===item.episodeOf||y.id===item.id).map(e=>`<button class="episode" data-action="play-show-entry" data-id="${esc(e.id)}">${esc(e.name)}</button>`).join('')}</div>`;}}return;}
    if(action==='play-show-entry'){const item=state.shows.find(x=>x.id===id);if(item){markContinue(item.id);play(item.url,item.name);}return;}
    if(action==='play-episode'){const s=activeSource(buttonElement.dataset.source);const ep={streamId:id,ext:buttonElement.dataset.ext,sourceId:s?.id,type:'episode'};const name=state.shows.find(x=>x.id===buttonElement.dataset.parent)?.name||'Episode';markContinue(buttonElement.dataset.parent);play(mediaUrl(ep),name+' · Episode');return;}
    if(action==='favorite'||action==='favorite-media'){toggleFav(arg);if(action==='favorite-media'&&$('details-body').children.length)detail(arg,'movie');else render(true);return;}
    if(action==='toggle-local'){toggleLocal(id);return;}
    if(action==='save-zip'){const s=$('zipcode')?.value.trim()||'';if(!/^\d{5}$/.test(s))return toast('Enter a five-digit ZIP code');state.zip=s;save();toast('ZIP saved');return;}
    if(action==='save-profile'){state.profile=($('profile-name')?.value||'Petalix').trim();save();render(true);return;}
    if(action==='toggle-autoplay'){state.autoplay=!state.autoplay;save();render(true);return;}
    if(action==='toggle-sounds'){state.sounds=!state.sounds;save();if(native&&window.AndroidBridge.setUiSounds)window.AndroidBridge.setUiSounds(state.sounds);render(true);return;}
    if(action==='accent'){state.accent=arg;save();render(true);return;}
    if(action==='add-m3u'){const url=$('m3u-url')?.value.trim(),name=$('m3u-name')?.value.trim()||'My M3U';if(!safeUrl(url))return toast('Enter an HTTP or HTTPS playlist URL');const old=state.sources.find(s=>s.name===name);if(!old)state.sources.push({id:uid(name+'-'+Date.now()),name,url,type:'m3u'});else old.url=url;save();if(native)window.AndroidBridge.importPlaylist(url,name);else fetchSource(url,s=>applyPlaylist(s,name,url)).catch(e=>toast('Unable to load playlist: '+e.message));toast('Downloading playlist…');return;}
    if(action==='pick-file'){if(native)window.AndroidBridge.openPlaylistFile();else $('playlist-file').click();return;}
    if(action==='connect-xc'){connectXtream();return;}
    if(action==='load-epg'){const url=$('epg-url')?.value.trim();if(!safeUrl(url))return toast('Enter an XMLTV URL');state.epgUrl=url;save();if(native)window.AndroidBridge.importEpg(url);else fetchSource(url,applyEpg).catch(e=>toast('EPG error: '+e.message));toast('Loading guide…');return;}
    if(action==='refresh'){refresh();return;}
    if(action==='remove-source'){const s=activeSource(id);if(!s)return;state.sources=state.sources.filter(x=>x.id!==id);state.channels=state.channels.filter(x=>x.sourceId!==id);state.movies=state.movies.filter(x=>x.sourceId!==id);state.shows=state.shows.filter(x=>x.sourceId!==id);save();render(true);return;}
    if(action==='reset'){if(!confirm('Clear Petalix test app settings and all imported catalogs?'))return;localStorage.removeItem(storageKey);state=defaultState();render();return;}
    if(action==='close-details'){closeDetails();return;}
  }
  document.addEventListener('click',e=>{const target=e.target.closest('[data-action]');if(!target)return;handle(target.dataset.action,target);});
  $('menu-button').onclick=()=>{state.drawer=!state.drawer;render(true);};$('scrim').onclick=()=>{state.drawer=false;render(true);};$('settings-shortcut').onclick=()=>{state.settingsTab='Playlists';nav('Settings');};$('profile-button').onclick=()=>{state.settingsTab='Profile';nav('Settings');};$('details-close').onclick=closeDetails;
  $('search').addEventListener('input',e=>{const pos=e.target.selectionStart;state.search=e.target.value;render(true);e.target.focus();e.target.setSelectionRange(pos,pos);});
  $('playlist-file').addEventListener('change',e=>{const file=e.target.files[0];if(file)file.text().then(t=>applyPlaylist(t,file.name)).catch(err=>toast(err.message));e.target.value='';});
  function interactiveVisible(element){
    if(!element||element.disabled)return false;
    if(element.closest('#drawer')&&!state.drawer)return false;
    if(element.closest('#details')&&$('details').classList.contains('hidden'))return false;
    const rect=element.getBoundingClientRect();const style=getComputedStyle(element);
    return rect.width>1&&rect.height>1&&style.display!=='none'&&style.visibility!=='hidden';
  }
  const tvSelector='button:not([disabled]),input:not([disabled]),select:not([disabled]),[tabindex="0"]';
  function remoteCandidates(){
    const scope=(!$('details').classList.contains('hidden'))?$('details'):(state.drawer?$('drawer'):document);
    return [...scope.querySelectorAll(tvSelector)].filter(interactiveVisible);
  }
  function focusTv(el){
    if(!el)return;
    el.focus({preventScroll:true});
    el.scrollIntoView({block:'nearest',inline:'nearest',behavior:'smooth'});
  }
  window.petalixRemote=(direction)=>{
    if(direction==='menu'){$('menu-button').click();return true;}
    const elements=remoteCandidates();let focused=document.activeElement;
    if(!elements.includes(focused)){
      const initial=elements.find(e=>e.closest('#screen'))||elements[0];
      if(initial)focusTv(initial);
      return true;
    }
    if(direction==='select'){
      if(focused.matches('input,textarea')){focused.focus();return false;}
      focused.click();return true;
    }
    if(focused.matches('input,textarea') && (direction==='left'||direction==='right'))return false;
    const source=focused.getBoundingClientRect();const x=source.left+source.width/2,y=source.top+source.height/2;
    let nearest=null,lowest=Infinity;
    for(const other of elements){
      if(other===focused)continue;
      const r=other.getBoundingClientRect(),ox=r.left+r.width/2,oy=r.top+r.height/2;
      const dx=ox-x,dy=oy-y;
      const along=direction==='left'?-dx:direction==='right'?dx:direction==='up'?-dy:dy;
      const across=(direction==='left'||direction==='right')?Math.abs(dy):Math.abs(dx);
      if(along<3)continue;
      const cost=along+2.5*across+(across>Math.max(source.width,source.height)*1.8?250:0);
      if(cost<lowest){lowest=cost;nearest=other;}
    }
    if(nearest)focusTv(nearest);
    return true;
  };
  // Laptop and wireless keyboards use the same navigation as a Shield remote.
  document.addEventListener('keydown',e=>{
    const d={ArrowLeft:'left',ArrowRight:'right',ArrowUp:'up',ArrowDown:'down'}[e.key];
    if(d){if(!e.target.matches('input,textarea')||d==='up'||d==='down'){
      e.preventDefault();window.petalixRemote(d);
    }}
  });
  document.addEventListener('keydown',e=>{if(e.key==='Escape'||e.key==='Backspace'&&e.target.tagName!=='INPUT'){e.preventDefault();window.handleBack();}
    if(e.key==='Enter'&&e.target.dataset?.action==='play'&&e.repeat){const id=e.target.dataset.id;if(id){toggleFav(id);toast('Favorite updated');}}});
  window.handleBack=()=>{if(!$('details').classList.contains('hidden')){closeDetails();return;}if(state.drawer){state.drawer=false;render(true);return;}if(state.service){state.service='';render();return;}if(state.tab!=='Home'){nav('Home');return;}if(native)window.AndroidBridge.exitApp();};
  loadState();render();if(native&&window.AndroidBridge.setUiSounds)window.AndroidBridge.setUiSounds(state.sounds!==false);
  if(native)setTimeout(()=>focusTv(document.querySelector('#screen button, #topnav button')),180);
  if(native&&state.sources.length)setTimeout(refresh,650);
})();
