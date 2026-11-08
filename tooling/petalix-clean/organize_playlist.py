#!/usr/bin/env python3
"""Local-only Petalix USA M3U curator. Never stores, logs, or uploads provider URLs.

The PERSONAL TEST app uses matching rules in android/PetalixLineup.kt at
each playlist import/refresh; this script can also generate a local M3U file.
"""
import argparse
import json
import re
from pathlib import Path

RULES = json.loads(Path(__file__).with_name("lineup-rules.json").read_text(encoding="utf-8"))
CATEGORIES = RULES["live_tv"]["category_order"]
MAJOR = RULES["live_tv"]["prioritize"]["Sports"]["major_networks"]
FOREIGN = re.compile(
    r"(?:^|[\s|:/-])(?:INTERNATIONAL|WORLD|CANADA|CANADIAN|UK|UNITED KINGDOM|BRITAIN|"
    r"INDIA|PAKISTAN|TURKEY|TURKISH|FRANCE|FRENCH|GERMANY|GERMAN|ARABIC|ARABIA|"
    r"PORTUGAL|BRAZIL|SPAIN|ITALY|MEXICO|LATIN AMERICA|SOUTH AMERICA|EUROPE|"
    r"ASIA|AFRICA|AFGHANISTAN|RUSSIA|CHINA|JAPAN|KOREA|POLAND|GREECE|ISRAEL)"
    r"(?:$|[\s|:/-])", re.I
)
FOREIGN_PREFIX = re.compile(
    r"^(?:INTL|INTERNATIONAL|CANADA|CANADIAN|UK|UNITED KINGDOM|INDIA|PAKISTAN|"
    r"FRANCE|FRENCH|GERMANY|GERMAN|ARABIC|BRAZIL|SPAIN|ITALY|MEXICO|LATINO|RUSSIA|CHINA|JAPAN|KOREA)\s*[-:|]\s*", re.I
)
VOD_GROUP = re.compile(r"(?:^|[\s|:/-])(?:VOD|ON[\s-]*DEMAND|MOVIE[\s-]*VOD|SERIES|TV[\s-]*SHOWS|EPISODES)(?:$|[\s|:/-])", re.I)
VOD_URL = re.compile(r"/(?:movie|series|vod|episodes?)/|\.(?:mp4|mkv|avi|wmv)(?:[?#]|$)", re.I)
TSN = re.compile(r"\bTSN(?:\s*\+)?\b", re.I)
FANDUEL = re.compile(r"\b(?:FAN\s*DUEL|FANDUEL|BALLY SPORTS)\b", re.I)
NUMBERED = re.compile(r"(?<!\d)([1-5])(?!\d)")
QUALITY_SUFFIX = re.compile(r"(?:\s+|\||\[|\()(?:UHD|FHD|HD|SD|4K|2160P|1080P|720P|480P|HEVC|H265|60FPS|50FPS)(?:\]|\))?$", re.I)
QUALITY_TOKEN = re.compile(r"\b(UHD|4K|2160P|FHD|1080P|HD|720P|SD|480P)\b", re.I)
PREFIX = re.compile(r"^(?:US|USA)\s*[-:|]\s*", re.I)
MAJOR_RE = re.compile(
    r"^(?:ESPN(?:\+|2|U|NEWS)?|FS[12]|FOX SPORTS [12]|NFL NETWORK|NFL REDZONE|NBA TV|"
    r"MLB NETWORK|NHL NETWORK|CBS SPORTS(?: NETWORK)?|NBC SPORTS(?: NETWORK)?|"
    r"GOLF CHANNEL|TENNIS CHANNEL|BIG TEN NETWORK|B1G NETWORK|SEC NETWORK|ACC NETWORK)(?:\b|\s|$)", re.I
)
SPORTS_RE = re.compile(
    r"\bSPORTS?\b|\b(?:ESPN|NFL|NBA|MLB|NHL|NCAA|UFC|PGA|NASCAR|FORMULA 1|F1|"
    r"MLS|SOCCER|FOOTBALL|TENNIS|BOXING|MMA|GOLF|CRICKET|RACING|FS1|FS2|B1G|TSN|FANDUEL)\b", re.I
)
LOCAL_GENERIC = re.compile(r"^(?:ABC|CBS|NBC|FOX|PBS|CW)$", re.I)


def numbered(name):
    m = NUMBERED.search(name)
    return int(m.group(1)) if m else None


def clean_name(name):
    name = PREFIX.sub("", name.strip())
    while QUALITY_SUFFIX.search(name):
        name = QUALITY_SUFFIX.sub("", name).strip()
    return " ".join(name.split())


def norm(name):
    label = clean_name(name).upper()
    if FANDUEL.search(label) and numbered(label):
        return f"FANDUEL {numbered(label)}"
    if TSN.search(label) and numbered(label):
        return f"TSN {numbered(label)}"
    for rx, replacement in [
        (r"\bESPN\s+2\b", "ESPN2"), (r"\bESPN\s+NEWS\b", "ESPNEWS"),
        (r"\bESPN\s+U\b", "ESPNU"), (r"\bFOX SPORTS\s*[- ]?\s*1\b", "FS1"),
        (r"\bFOX SPORTS\s*[- ]?\s*2\b", "FS2"),
        (r"\bFS\s*[- ]\s*1\b", "FS1"), (r"\bFS\s*[- ]\s*2\b", "FS2"),
    ]:
        label = re.sub(rx, replacement, label)
    return " ".join(label.split())


def foreign(group, name):
    if not (FOREIGN.search(group) or FOREIGN_PREFIX.search(name)):
        return False
    return not (TSN.search(name) and numbered(name) is not None)


def on_demand(group, url):
    return bool(
        (VOD_GROUP.search(group) and "24/7" not in group and "24 7" not in group)
        or VOD_URL.search(url)
    )


def classify(group, name):
    text = group + " " + name
    title = clean_name(name)
    if FANDUEL.search(text) or TSN.search(text) or MAJOR_RE.search(title) or SPORTS_RE.search(text):
        return "Sports"
    if "24/7" in text or "24 7" in text:
        for pat, category in [
            (r"MOVIE|FILM|CINEMA", "24/7 | Movies"),
            (r"KID|CARTOON|FAMILY|ANIME", "24/7 | Kids"),
            (r"MUSIC|CONCERT|KARAOKE", "24/7 | Music"),
            (r"DRAMA|CRIME|REALITY|COMEDY|SHOW|SERIES|CLASSIC|TV", "24/7 | TV & Series"),
        ]:
            if re.search(pat, text, re.I):
                return category
        return "24/7 | Other"
    for pat, category in [
        (r"\bLOCALS?\b|AFFILIATE|CALL SIGN", "Locals"),
        (r"\bNEWS\b|WEATHER|BUSINESS NEWS", "News"),
        (r"\bWESTERN\b|COWBOY", "Western"),
        (r"\bMOVIE\b|CINEMA|PREMIUM", "Movies"),
        (r"\bKIDS?\b|CHILDREN|CARTOON|FAMILY", "Kids & Family"),
        (r"DOCUMENTARY|DOCUMENTARIES|SCIENCE|HISTORY|DISCOVERY|NAT GEO", "Documentaries"),
        (r"LIFESTYLE|COOKING|FOOD|TRAVEL|HOME|GARDEN|HGTV", "Lifestyle"),
        (r"\bMUSIC\b|MTV|VH1", "Music"),
        (r"ENTERTAINMENT|GENERAL|USA\s*\|", "Entertainment"),
    ]:
        if re.search(pat, text, re.I):
            return category
    return "Other USA"


def quality(extinf, name, group):
    found = QUALITY_TOKEN.findall(extinf + " " + name + " " + group)
    values = {"UHD": 5, "4K": 5, "2160P": 5, "FHD": 4, "1080P": 4,
              "HD": 3, "720P": 3, "SD": 1, "480P": 1}
    return max((values[t.upper()] for t in found), default=2)


def attrs_and_name(extinf):
    head, _, name = extinf.partition(",")
    match = re.search(r'\bgroup-title="([^"]*)"', head, re.I)
    group = match.group(1) if match else ""
    epg = re.search(r'\btvg-id="([^"]*)"', head, re.I)
    return group, name.strip(), epg.group(1) if epg else ""


def parse(source):
    header = "#EXTM3U"
    current = []
    for raw in source.splitlines():
        line = raw.strip()
        if not line:
            continue
        if line.startswith("#EXTM3U") and not current:
            header = line
        elif line.startswith("#EXTINF:"):
            current = [line]
        elif current:
            current.append(line)
            if not line.startswith("#"):
                yield header, current
                current = []


def rewrite_group(extinf, group, label):
    head, comma, _ = extinf.partition(",")
    if re.search(r'\bgroup-title="[^"]*"', head, re.I):
        head = re.sub(r'\bgroup-title="[^"]*"', 'group-title="' + group + '"', head, count=1, flags=re.I)
    else:
        head += ' group-title="' + group + '"'
    return head + comma + label


def sports_priority(label):
    normalized = norm(label)
    for i, network in enumerate(MAJOR):
        if normalized == network or normalized.startswith(network + " "):
            return i
    if FANDUEL.search(label):
        return 100 + (numbered(label) or 99)
    if TSN.search(label):
        return 200 + (numbered(label) or 99)
    return 300


def organize(source, include_backups=False):
    """Keep only one high-resolution stream per channel; backups are disabled."""
    chosen = {}
    counts = {"international_hidden": 0, "on_demand_hidden": 0, "invalid_number_hidden": 0, "duplicates_removed": 0}
    header = "#EXTM3U"
    for observed_header, block in parse(source):
        header = observed_header
        group, name, epg_id = attrs_and_name(block[0])
        url = block[-1]
        if on_demand(group, url):
            counts["on_demand_hidden"] += 1
            continue
        if foreign(group, name):
            counts["international_hidden"] += 1
            continue
        if ((TSN.search(name) or FANDUEL.search(name)) and numbered(name) is None):
            counts["invalid_number_hidden"] += 1
            continue
        label = clean_name(name)
        if not label:
            continue
        category = classify(group, label)
        id_name = norm(label)
        if category == "Locals" and LOCAL_GENERIC.fullmatch(id_name) and epg_id:
            id_name += "|" + epg_id.lower()
        score = quality(block[0], name, group)
        record = (category, label, score, [rewrite_group(block[0], category, label), *block[1:]])
        previous = chosen.get(id_name)
        if previous is None:
            chosen[id_name] = record
        else:
            counts["duplicates_removed"] += 1
            if score > previous[2]:
                chosen[id_name] = record

    def rank(item):
        group, label, q, _ = item
        return (CATEGORIES.index(group) if group in CATEGORIES else 999,
                sports_priority(label) if group == "Sports" else 0,
                -q, label.casefold())

    ordered = sorted(chosen.values(), key=rank)
    output = [header]
    for group, _, _, block in ordered:
        output.extend(block)
        counts[group] = counts.get(group, 0) + 1
    counts["primary_channels"] = len(ordered)
    counts["backup_channels"] = 0
    return "\n".join(output) + "\n", counts


def main():
    parser = argparse.ArgumentParser(description="Curate a private legal M3U locally for Petalix")
    parser.add_argument("input", type=Path, help="Local M3U input, never share a credential URL")
    parser.add_argument("output", type=Path, help="Local curated M3U output, keep it private")
    parser.add_argument("--include-backups", action="store_true",
                        help="Deprecated: backups are disabled per owner's no-duplicates requirement")
    args = parser.parse_args()
    source = args.input.read_text(encoding="utf-8-sig")
    if not source.lstrip().startswith("#EXTM3U"):
        parser.error("Input must be a local #EXTM3U playlist.")
    result, stats = organize(source)
    args.output.write_text(result, encoding="utf-8")
    print(json.dumps(stats, sort_keys=True))  # Counts ONLY. No credentials or URLs.


if __name__ == "__main__":
    main()
