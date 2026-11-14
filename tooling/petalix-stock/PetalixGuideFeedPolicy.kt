package com.nuvio.tv.petalix

/** Guide presentation does not remove a channel from the repository or backup index. */
internal fun petalixShowGuideFeed(hiddenFromGuide: Boolean, explicitSavedChoice: Boolean): Boolean =
    !hiddenFromGuide || explicitSavedChoice
