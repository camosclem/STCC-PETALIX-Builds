package com.nuvio.tv.petalix

private val sportsGroup = Regex("(?i)\\b(?:sports?|nfl|nba|nhl|mlb|ncaa|ncaaf|ncaab|teams?|sunday ticket|league pass|events?|ppv|espn|dazn|fubo|btn)\\b")
private val sportsFeed = Regex("(?i)\\b(?:nfl teams?|nba teams?|nhl teams?|mlb teams?|sunday ticket|league pass|ppv|espn)\\b")
/** A call sign in a team/event feed does not make it a local broadcast station. */
internal fun petalixLocalStationCandidate(name: String, group: String): Boolean =
    !sportsGroup.containsMatchIn(group) && !sportsFeed.containsMatchIn(name)
