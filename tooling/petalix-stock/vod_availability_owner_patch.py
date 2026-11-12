from home_visual_owner_patch import replace


def apply(root):
    source = root / 'app/src/main/java/com/nuvio/tv'
    vod = source / 'livetv/ondemand/OnDemandStreams.kt'
    replace(vod, '    @Volatile\n    var offeredInStreams: Boolean = false', '''    private val _offeredInStreams = kotlinx.coroutines.flow.MutableStateFlow(false)
    internal val offeredInStreams: kotlinx.coroutines.flow.StateFlow<Boolean> = _offeredInStreams

    internal fun setOfferedInStreams(value: Boolean) {
        _offeredInStreams.value = value
    }''')
    replace(vod, '        if (!repo.hasContent.value) return emptyList()', '''        if (!repo.hasContent.value && repo.status.value.loading) {
            kotlinx.coroutines.withTimeoutOrNull(8_000L) {
                combine(repo.hasContent, repo.status) { hasContent, status ->
                    hasContent || !status.loading
                }.first { it }
            }
        }
        if (!repo.hasContent.value) return emptyList()''')
    replace(source / 'livetv/ondemand/OnDemandRepository.kt',
            '.collect { OnDemandStreams.offeredInStreams = it }',
            '.collect { OnDemandStreams.setOfferedInStreams(it) }')
    availability = source / 'core/streams/PlaybackAvailability.kt'
    replace(availability, 'private val serverStreams: (String, String) -> Boolean = { _, _ -> false }',
            'private val serverStreams: (String, String) -> Boolean = { _, _ -> false },\n    private val providerOffersOnDemand: Boolean = false')
    replace(availability, '((type == "movie" || type == "series") && com.nuvio.tv.livetv.ondemand.OnDemandStreams.offeredInStreams)',
            '(com.nuvio.tv.petalix.isPetalixVodType(type) && providerOffersOnDemand)')
    provider = source / 'ui/components/PlaybackAvailabilityProvider.kt'
    replace(provider, '''        serverRepository.uiState
    ) { addons, scrapers, _ ->''', '''        serverRepository.uiState,
        com.nuvio.tv.livetv.ondemand.OnDemandStreams.offeredInStreams
    ) { addons, scrapers, _, offeredInStreams ->''')
    replace(provider, '            serverStreams = serverStreams::canServe',
            '            serverStreams = serverStreams::canServe,\n            providerOffersOnDemand = offeredInStreams')
