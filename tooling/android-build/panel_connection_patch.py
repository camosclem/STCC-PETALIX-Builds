"""Retain saved activations across the verified panel-origin migration."""
import pathlib,shutil
from playlist_patch import replace_once

def apply(work):
    root=work/'app/src/main/java/com/nuvio/tv/petalix'
    coordinator=root/'ManagedCoordinator.kt'
    replace_once(coordinator,'OkHttpClient.Builder().connectTimeout','OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).connectTimeout')
    text=coordinator.read_text();start=text.index('    private suspend fun post(path:String,json:JSONObject,bearer:String?):JSONObject=')
    end=text.index('    private fun encryptionKey()',start)
    text=text[:start]+'''    private suspend fun post(path:String,json:JSONObject,bearer:String?):JSONObject=withContext(Dispatchers.IO) {
        var url=PanelConnectionPolicy.currentOrigin+"/api/device/"+path
        for(attempt in 0 until 3) {
            val request=Request.Builder().url(url).post(json.toString().toRequestBody("application/json".toMediaType()))
            if(bearer!=null)request.header("Authorization","Bearer "+bearer)
            http.newCall(request.build()).execute().use { response ->
                if(response.code in listOf(301,302,303,307,308)) {
                    url=PanelConnectionPolicy.redirected(url,response.header("Location").orEmpty())?:error("The panel address could not be verified. Your saved activation is retained.")
                } else {
                    val raw=response.body?.string().orEmpty()
                    val doc=runCatching { JSONObject(raw) }.getOrElse { error("The panel is unavailable. Retry the connection; your saved activation is retained.") }
                    if(!response.isSuccessful) {
                        if(bearer!=null&&response.code in listOf(401,403)) {
                            check(prefs.edit().putBoolean("accessDenied",true).commit())
                            throw ActivationRequired()
                        }
                        error(doc.optString("error","Could not connect to the panel."))
                    }
                    if(bearer!=null)check(prefs.edit().putBoolean("accessDenied",false).commit())
                    return@withContext doc
                }
            }
        }
        error("The panel redirected too many times. Your saved activation is retained.")
    }

'''+text[end:]
    needle='ActivationSession.ready(hasSavedActivation,prefs.getString("expires",""),System.currentTimeMillis())'
    if text.count(needle)!=1:raise RuntimeError('Saved-session access binding needs review')
    text=text.replace(needle,needle+'&&!prefs.getBoolean("accessDenied",false)')
    coordinator.write_text(text)
    replace_once(coordinator,'.putLong("verified",System.currentTimeMillis()).commit()', '.putLong("verified",System.currentTimeMillis()).putBoolean("accessDenied",false).commit()')
    replace_once(coordinator,'"device-updates-v1"','"device-updates-v1","panel-session-origin-v1"')
    activity=root/'ActivationActivity.kt'
    replace_once(activity,'catch(e:ActivationRequired){showPin();message.text="Saved activation was not accepted. Contact your provider or enter your current PIN.";pin.requestFocus()}', 'catch(e:ActivationRequired){message.text="This TV’s saved activation could not be confirmed. Retry the connection or contact your provider. Your saved login is retained.";button.isEnabled=true;button.requestFocus()}')
    old='catch(e:Exception){message.text=e.message?:"Activation failed. Try again.";button.isEnabled=true}'
    replace_once(activity,old,'catch(e:Exception){if(e is ActivationRequired && manager.hasSavedActivation)restoreSaved() else {message.text=e.message?:"Activation failed. Try again.";button.isEnabled=true}}')
    replace_once(activity,'        fun openApp(){','''        val changePin=line("Use a different activation PIN",13f).apply { setTextColor(Color.LTGRAY);isFocusable=true;isClickable=true }
        content.addView(changePin)
        lateinit var restoreSaved:()->Unit
        fun openApp(){''')
    replace_once(activity,'        fun restoreSaved(){','        restoreSaved = {')
    replace_once(activity,'            pin.visibility=android.view.View.VISIBLE','            pin.visibility=android.view.View.VISIBLE\n            changePin.visibility=android.view.View.GONE')
    replace_once(activity,'            pin.visibility=android.view.View.GONE','            pin.visibility=android.view.View.GONE\n            changePin.visibility=android.view.View.VISIBLE')
    replace_once(activity,'        showPin()\n        if(manager.hasSavedActivation)restoreSaved()','        changePin.setOnClickListener{showPin();message.text="Enter the activation PIN from your provider.";pin.requestFocus()}\n        showPin()\n        if(manager.hasSavedActivation)restoreSaved()')
    gradle=work/'app/build.gradle.kts';text=gradle.read_text();old='petalix-control.pinkgiant5.chatgpt.site'
    if text.count(old)!=1:raise RuntimeError('Compiled panel origin needs review')
    gradle.write_text(text.replace(old,'petalix-control.caroliinaconsultant.chatgpt.site'))
    local=pathlib.Path(__file__).parent
    shutil.copyfile(local/'PanelConnectionPolicy.kt',root/'PanelConnectionPolicy.kt')
    tests=work/'app/src/test/java/com/nuvio/tv/petalix';tests.mkdir(parents=True,exist_ok=True);shutil.copyfile(local/'PanelConnectionPolicyTest.kt',tests/'PanelConnectionPolicyTest.kt')
