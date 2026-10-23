"""Keep the native updater while authenticating updates for individual activated TVs."""
import pathlib

def apply(root,brand):
    base=root/'app/src'
    coordinator=base/'main/java/com/nuvio/tv/petalix/ManagedCoordinator.kt'
    text=coordinator.read_text()
    marker='    private fun persistSession(doc:JSONObject) {'
    if 'suspend fun appUpdate()' not in text:
        assert text.count(marker)==1,'Managed session binding changed'
        text=text.replace(marker,'''    private val pendingUpdateRequest=kotlinx.coroutines.flow.MutableStateFlow("")
    val updateRequests:kotlinx.coroutines.flow.StateFlow<String> get()=pendingUpdateRequest
    suspend fun appUpdate():JSONObject {
        if(!ready)throw ActivationRequired()
        return post("update",report(),token())
    }

'''+marker)
        needle='        persistSession(doc)\n        acknowledge()\n    }\n\n    private val pendingUpdateRequest'
        assert text.count(needle)==1,'Managed heartbeat binding changed'
        text=text.replace(needle,'''        persistSession(doc)
        val updateRequest=doc.optJSONObject("appUpdate")?.optString("requestId").orEmpty()
        if(updateRequest.isNotBlank())pendingUpdateRequest.value=updateRequest
        acknowledge()
    }

    private val pendingUpdateRequest''')
        needle='"kaptain-manifest-v1"'
        assert text.count(needle)==1,'Managed capability binding changed'
        text=text.replace(needle,needle+',"device-updates-v1"')
        coordinator.write_text(text)
    repository=base/'full/java/com/nuvio/tv/updater/UpdateRepository.kt'
    text=repository.read_text()
    needle='private val gitHubReleaseApi:GitHubReleaseApi)'
    if 'private val coordinator:' not in text:
        assert text.count(needle)==1,'Updater dependency binding changed'
        text=text.replace(needle,'private val gitHubReleaseApi:GitHubReleaseApi,private val coordinator:com.nuvio.tv.petalix.ManagedCoordinator)')
        text=text.replace('    private val http=', '    val deviceUpdateRequests get()=coordinator.updateRequests\n    private val http=',1)
        needle='            val request=Request.Builder()'
        assert text.count(needle)==1,'Updater request binding changed'
        text=text.replace(needle,'            if(coordinator.ready)return@runCatching parse(coordinator.appUpdate(),channel)\n'+needle)
        start=text.index('                val doc=JSONObject(response.body?.string().orEmpty()).optJSONObject("update")')
        end=text.index('\n            }',start)
        parse=text[start:end].replace('val doc=JSONObject(response.body?.string().orEmpty()).optJSONObject("update")','val doc=payload.optJSONObject("update")').replace('                AppUpdate(','                return AppUpdate(')
        text=text[:start]+'                parse(JSONObject(response.body?.string().orEmpty()),channel)'+text[end:]
        insert=text.rfind('\n}')
        text=text[:insert]+'\n    private fun parse(payload:JSONObject,channel:UpdateChannel):AppUpdate {\n'+parse+'\n    }\n'+text[insert:]
        repository.write_text(text)
    model=base/'full/java/com/nuvio/tv/updater/UpdateViewModel.kt'
    text=model.read_text()
    needle='    init {\n'
    if 'updateRepository.deviceUpdateRequests.collect' not in text:
        assert text.count(needle)==1,'Updater initialization binding changed'
        text=text.replace(needle,'''    init {
        viewModelScope.launch {
            updateRepository.deviceUpdateRequests.collect { request ->
                if(request.isNotBlank())checkForUpdates(force=true,showNoUpdateFeedback=false)
            }
        }
''',1)
    needle='val remoteNewer = VersionUtils.isRemoteNewer(update.tag, BuildConfig.VERSION_NAME)'
    assert text.count(needle)==1 or 'val remoteNewer = update.expectedVersionCode?.let' in text,'Updater version comparison changed'
    text=text.replace(needle,'val remoteNewer = update.expectedVersionCode?.let { it > BuildConfig.VERSION_CODE } ?: VersionUtils.isRemoteNewer(update.tag, BuildConfig.VERSION_NAME)')
    model.write_text(text)
