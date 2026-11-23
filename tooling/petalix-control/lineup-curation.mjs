/**
 * Petalix metadata-only curation PREVIEW.
 * Does not fetch stream URLs, change a customer playlist, or publish to the site.
 */
const compare = new Intl.Collator('en-US', {numeric:true,sensitivity:'base'});
const value = x => String(x ?? '').trim();
const byName = (a,b) => compare.compare(value(a), value(b));

function western(channel) {
  // Sports feeds always stay available under the Sports group in this preview.
  if (/\bsports?\b/i.test(value(channel.group))) return false;
  const categories=[channel.genre,channel.category,...(Array.isArray(channel.tags)?channel.tags:[])];
  if (categories.some(x=>/\bwestern(s)?\b/i.test(value(x)))) return true;
  // "AMC WEST" and similar regional feeds are not Western movies.
  return /\b(western(s)?|cowboy\s+(movies|tv)|classic\s+westerns)\b/i.test(value(channel.name));
}
function tsn(name) {return /^(?:[A-Z]{2,5}\s*[|:-]\s*)?TSN\b/i.test(name);}
function fanduel(name) {return /\bfan\s*duel\b/i.test(name);}
function tsnIndex(name) {const m=value(name).match(/\bTSN\s*([1-5])\b/i);return m?Number(m[1]):99;}
function itemCompare(a,b) {return byName(a.name,b.name)||byName(a.id,b.id);}
function tsnSort(a,b) {return tsnIndex(a.name)-tsnIndex(b.name)||itemCompare(a,b);}
function fanSort(a,b) {
  const tv=name=>/\bfan\s*duel\s*tv\b/i.test(name);
  return Number(tv(b.name))-Number(tv(a.name))||itemCompare(a,b);
}
function categorySort(a,b) {
  if(a==='Favorites')return -1;
  if(b==='Favorites')return 1;
  if(a==='Sports')return -1;
  if(b==='Sports')return 1;
  return byName(a,b);
}
export function previewPetalixCuration({brand,revision,channels}) {
  if (brand!=='petalix') throw new Error('Only Petalix may be curated');
  if (!value(revision)) throw new Error('Expected source revision is required');
  if (!Array.isArray(channels)) throw new Error('Expected channel metadata array');
  const unique=new Set(), groups=new Map(), moves=[];
  for (const c of channels) {
    if (!c || typeof c!=='object') throw new Error('Invalid channel');
    const id=value(c.id), name=value(c.name), oldGroup=value(c.group);
    if(!id||!name||!oldGroup) throw new Error('Channel id, name and group required');
    if(unique.has(id)) throw new Error('Duplicate channel ID');
    unique.add(id);
    let group=oldGroup;
    if(fanduel(name))group='FanDuel';
    else if(tsn(name))group='TSN';
    else if(western(c))group='Western';
    if(!groups.has(group))groups.set(group,[]);
    groups.get(group).push({id,name,group});
    if(group!==oldGroup)moves.push({id,from:oldGroup,to:group});
  }
  for(const [name,entries] of groups) {
    entries.sort(name==='TSN'?tsnSort:name==='FanDuel'?fanSort:itemCompare);
  }
  const groupNames=[...groups.keys()].sort(categorySort);
  const assignments=groupNames.flatMap(group=>groups.get(group).map(c=>({id:c.id,group})));
  if(assignments.length!==channels.length) throw new Error('Count invariant failed');
  return {
    brand,sourceRevision:revision,channelCount:channels.length,
    groups:groupNames.map(name=>({name,count:groups.get(name).length,channelIds:groups.get(name).map(c=>c.id)})),
    moves,assignments,
    notes:[
      'PREVIEW ONLY. The hosted Petalix site is not connected here.',
      'Do not remove source feeds or hide them from Sports Hub matching.',
      'Server publication requires owner authorization, expectedRevision, audit trail and rollback.'
    ]
  };
}
