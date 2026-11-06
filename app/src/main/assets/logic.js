(function (root) {
  'use strict';
  function hash(s) {
    let h = 2166136261;
    for (let i = 0; i < s.length; i++) {
      h ^= s.charCodeAt(i);
      h = Math.imul(h, 16777619);
    }
    return (h >>> 0).toString(36);
  }
  function attrs(s) {
    const result = {};
    const exp = /([\w-]+)\s*=\s*(["'])(.*?)\2/g;
    let item;
    while ((item = exp.exec(s))) result[item[1].toLowerCase()] = item[3];
    return result;
  }
  function parseM3U(content, source) {
    const lines = String(content).replace(/^\uFEFF/, '').split(/\r?\n/);
    if (!lines.some(x => x.trim().startsWith('#EXTINF'))) {
      if (lines.some(x => x.includes('#EXT-X-'))) throw Error('This is an HLS stream manifest, not a channel playlist. Add an M3U channel list.');
      throw Error('No #EXTINF channel entries found in this playlist.');
    }
    const channels = [];
    let info = null, group = '';
    for (const raw of lines) {
      const line = raw.trim();
      if (!line) continue;
      if (line.startsWith('#EXTGRP:')) { group = line.slice(8).trim(); if (info && info.group === 'Other') info.group = group; continue; }
      if (line.startsWith('#EXTINF:')) {
        const comma = line.indexOf(',');
        const left = comma < 0 ? line : line.slice(0, comma);
        const meta = attrs(left);
        info = {
          name: (comma < 0 ? meta['tvg-name'] : line.slice(comma + 1).trim()) || meta['tvg-name'] || 'Untitled channel',
          tvgId: meta['tvg-id'] || '',
          logo: meta['tvg-logo'] || '',
          group: meta['group-title'] || group || 'Other',
          number: meta['tvg-chno'] || meta['channel-number'] || '',
        };
        continue;
      }
      if (line.startsWith('#')) continue;
      if (info && /^(https?:\/\/|rtsp:\/\/)/i.test(line)) {
        const key = `${source}|${info.tvgId || info.name}|${line}`;
        channels.push({ ...info, id: hash(key), url: line, source });
        info = null;
        group = '';
      }
    }
    if (!channels.length) throw Error('No playable HTTP, HTTPS or RTSP channel URLs found.');
    return channels;
  }
  function isSports(c) {
    const s = (c.group + ' ' + c.name).toLowerCase();
    return /sports?|espn|fox sports|fanduel|tsn|nba|nfl|nhl|mlb|soccer|football|ufc|premier league|sec network|acc network|sky sport|bein|tennis|racing/.test(s);
  }
  function isLocal(c) {
    const g = (c.group || '').toLowerCase();
    const n = (c.name || '').toLowerCase();
    return /locals?|broadcast|over.the.air|affiliates?|city stations?/.test(g) ||
      /\b(abc|cbs|nbc|fox|pbs|cw|mytv)\s*(?:\d{1,2}|\w{3,5}\b)|\b(?:wbtv|wcnc|wsoc|wccb|wlos|wspa|wltx)\b/.test(n);
  }
  function parseXmlTvDate(raw) {
    if (!raw) return null;
    const match = raw.trim().match(/^(\d{4})(\d\d)(\d\d)(\d\d)(\d\d)(\d\d)?\s*([+-])(\d\d)(\d\d)/);
    if (!match) return null;
    const [,y,m,d,h,min,sec,sign,tzh,tzm] = match;
    const base = Date.UTC(+y,+m - 1,+d,+h,+min,+(sec || 0));
    const offsetMs = (+tzh * 60 + +tzm) * 60 * 1000 * (sign === '+' ? 1 : -1);
    return new Date(base - offsetMs);
  }
  const exported = { parseM3U, isSports, isLocal, parseXmlTvDate, hash };
  if (typeof module !== 'undefined' && module.exports) module.exports = exported;
  root.OpenGuide = exported;
})(typeof window !== 'undefined' ? window : globalThis);
