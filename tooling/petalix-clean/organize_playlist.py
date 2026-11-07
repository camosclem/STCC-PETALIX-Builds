#!/usr/bin/env python3
"""Local-only M3U curator. Never prints or publishes private provider URLs."""
import argparse
import json
import re
from collections import Counter
from pathlib import Path

RULES = json.loads(Path(__file__).with_name("lineup-rules.json").read_text(encoding="utf-8"))
ORDER = RULES["live_tv"]["category_order"]
MAIN = RULES["live_tv"]["prioritize"]["Sports | Major Networks"]

FOREIGN = re.compile(
    r"(?:^|[\s|:/-])(?:CANADA|CANADIAN|UK|UNITED KINGDOM|BRITAIN|INDIA|"
    r"PAKISTAN|TURKEY|TURKISH|FRANCE|FRENCH|GERMANY|GERMAN|"
    r"ARABIC|PORTUGAL|BRAZIL|SPAIN|ITALY|MEXICO|LATIN AMERICA)"
    r"(?:$|[\s|:/-])", re.I
)
MAIN_NETWORK = re.compile(
    r"^(?:ESPN(?:\+|2|U|NEWS)?|FS[12]|FOX SPORTS [12]|NFL NETWORK|NFL REDZONE|"
    r"NBA TV|MLB NETWORK|NHL NETWORK|CBS SPORTS(?: NETWORK)?|NBC SPORTS(?: NETWORK)?|"
    r"GOLF CHANNEL|TENNIS CHANNEL|BIG TEN NETWORK|B1G NETWORK|"
    r"SEC NETWORK|ACC NETWORK)(?:\b|\s|$)", re.I
)
SPORTS = re.compile(
    r"\bSPORTS?\b|\b(?:ESPN|NFL|NBA|MLB|NHL|NCAA|UFC|PGA|NASCAR|FORMULA 1|F1|"
    r"MLS|SOCCER|FOOTBALL|TENNIS|BOXING|MMA|GOLF|CRICKET|RACING|"
    r"FANDUEL|TSN\+|TSN PLUS|TSN PLUS)\b", re.I
)
QUALITY = re.compile(r"(?:\s+|[|[])(?:UHD|FHD|HD|SD|4K|HEVC|H265|60FPS)\]?$", re.I)
NAME_PREFIX = re.compile(r"^(?:US|USA)\s*[-:|]\s*", re.I)


def clean_name(name):
    result = NAME_PREFIX.sub("", name.strip())
    while QUALITY.search(result):
        result = QUALITY.sub("", result).strip()
    return re.sub(r"\s+", " ", result)


def attrs_and_name(extinf):
    head, _, name = extinf.partition(",")
    group = re.search(r'\bgroup-title="([^"]*)"', head, re.I)
    return (group.group(1) if group else ""), name.strip()


def classify(group, name):
    text = (group + " " + name).upper()
    if re.search(r"\bTSN\s*(?:\+|PLUS)\b", text):
        return "Sports | TSN+"
    if re.search(r"FAN\s*DUEL|FANDUEL|BALLY SPORTS", text):
        return "Sports | FanDuel"
    if MAIN_NETWORK.search(name.upper().strip()):
        return "Sports | Major Networks"
    if SPORTS.search(text):
        leagues = [
            ("NFL", r"\bNFL\b|REDZONE|SUNDAY TICKET"),
            ("NBA", r"\bNBA\b|LEAGUE PASS"),
            ("MLB", r"\bMLB\b|BASEBALL"),
            ("NHL", r"\bNHL\b|HOCKEY"),
            ("College", r"\bNCAA\b|COLLEGE|BIG TEN|B1G|SEC NETWORK|ACC NETWORK"),
            ("Soccer", r"\bMLS\b|SOCCER|PREMIER LEAGUE|LA LIGA|CHAMPIONS LEAGUE"),
            ("Golf & Tennis", r"\bGOLF\b|\bPGA\b|TENNIS|ATP|WTA"),
            ("Racing", r"\bNASCAR\b|FORMULA 1|\bF1\b|RACING|MOTOGP"),
            ("Combat", r"\bUFC\b|BOXING|\bMMA\b|WRESTLING|\bWWE\b"),
        ]
        for suffix, pattern in leagues:
            if re.search(pattern, text):
                return "Sports | " + suffix
        return "Sports | Other"
    if "24/7" in text or "24 7" in text:
        if re.search(r"MOVIE|FILM|CINEMA", text):
            return "24/7 | Movies"
        if re.search(r"KID|CARTOON|FAMILY|ANIME", text):
            return "24/7 | Kids"
        if re.search(r"MUSIC|CONCERT|KARAOKE", text):
            return "24/7 | Music"
        if re.search(r"DRAMA|CRIME|REALITY|COMEDY|SHOW|SERIES|CLASSIC|TV", text):
            return "24/7 | TV & Series"
        return "24/7 | Other"
    if re.search(r"\bLOCALS?\b|AFFILIATE|CALL SIGN", text):
        return "Locals"
    if re.search(r"\bNEWS\b|WEATHER|BUSINESS NEWS", text):
        return "News"
    if re.search(r"\bWESTERN\b|COWBOY", text):
        return "Western"
    if re.search(r"\bMOVIE\b|CINEMA|PREMIUM", text):
        return "Movies"
    if re.search(r"\bKIDS?\b|CHILDREN|CARTOON|FAMILY", text):
        return "Kids & Family"
    if re.search(r"DOCUMENTARY|DOCUMENTARIES|SCIENCE|HISTORY|DISCOVERY|NAT GEO", text):
        return "Documentaries"
    if re.search(r"LIFESTYLE|COOKING|FOOD|TRAVEL|HOME|GARDEN|HGTV", text):
        return "Lifestyle"
    if re.search(r"\bMUSIC\b|MTV|VH1", text):
        return "Music"
    if re.search(r"ENTERTAINMENT|GENERAL|USA\s*\|", text):
        return "Entertainment"
    return "Other USA"


def foreign(group, name):
    # A Canadian-only TSN+ feed was expressly requested as a sports exception.
    if re.search(r"\bTSN\s*(?:\+|PLUS)\b", name + " " + group, re.I):
        return False
    return bool(FOREIGN.search(group))


def quality(extinf, name):
    text = (extinf + " " + name).upper()
    if re.search(r"\b4K\b|\bUHD\b", text):
        return 5
    if re.search(r"\bFHD\b|\b1080P\b", text):
        return 4
    if re.search(r"\bHD\b|\b720P\b", text):
        return 3
    if re.search(r"\bSD\b|\b480P\b", text):
        return 1
    return 2


def parse(source):
    """One #EXTINF, its metadata directives and its URL constitute a channel."""
    header = "#EXTM3U"
    current = []
    for raw in source.splitlines():
        line = raw.strip()
        if not line:
            continue
        if line.startswith("#EXTM3U") and not current:
            header = line
        elif line.startswith("#EXTINF:"):
            if current:
                current = []
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


def rank(entry):
    category, label, q, _, block = entry
    network_priority = 999
    if category == "Sports | Major Networks":
        n = label.upper()
        for index, network in enumerate(MAIN):
            if n == network or n.startswith(network + " "):
                network_priority = index
                break
    return (ORDER.index(category) if category in ORDER else 999, network_priority,
            label.casefold(), -q)


def organize(text, include_backups=False):
    winners = {}
    backups = []
    counts = Counter()
    header = "#EXTM3U"
    for observed_header, block in parse(text):
        header = observed_header
        group, name = attrs_and_name(block[0])
        if foreign(group, name):
            counts["foreign_hidden"] += 1
            continue
        category = classify(group, name)
        label = clean_name(name)
        if not label:
            counts["unlabeled_hidden"] += 1
            continue
        q = quality(block[0], name)
        transformed = [rewrite_group(block[0], category, label), *block[1:]]
        current = (category, label, q, name, transformed)
        # Keep distinct local stations; only identical city/call-sign labels collide.
        key = (category, label.casefold())
        previous = winners.get(key)
        if previous is None:
            winners[key] = current
        elif q > previous[2]:
            winners[key] = current
            backups.append(previous)
            counts["duplicates_condensed"] += 1
        else:
            backups.append(current)
            counts["duplicates_condensed"] += 1

    ordered = sorted(winners.values(), key=rank)
    output = [header]
    for category, label, _, _, block in ordered:
        output.extend(block)
        counts[category] += 1
    if include_backups:
        for category, label, _, _, block in sorted(backups, key=rank):
            output.extend([rewrite_group(block[0], "Backup Streams", label), *block[1:]])
    counts["primary_channels"] = len(ordered)
    counts["backup_channels"] = len(backups)
    return "\n".join(output) + "\n", dict(counts)


def main():
    parser = argparse.ArgumentParser(description="Curate an authorized playlist locally for Petalix")
    parser.add_argument("input", type=Path, help="Local M3U input; never paste an account URL")
    parser.add_argument("output", type=Path, help="Local output M3U; keep it private")
    parser.add_argument("--include-backups", action="store_true")
    args = parser.parse_args()
    source = args.input.read_text(encoding="utf-8-sig")
    if not source.lstrip().startswith("#EXTM3U"):
        parser.error("Input must be a local #EXTM3U playlist.")
    result, stats = organize(source, args.include_backups)
    args.output.write_text(result, encoding="utf-8")
    # Counts only; never log stream addresses, login credentials, or channel URLs.
    print(json.dumps(stats, sort_keys=True))


if __name__ == "__main__":
    main()
