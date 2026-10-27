"""Fix saved-session renewal in our activation integration, for both brands."""
import pathlib,shutil


def once(text,old,new,label):
    if text.count(old)!=1:
        raise RuntimeError('Activation integration needs review: '+label)
    return text.replace(old,new)


def apply(work,brand):
    if brand not in {'petalix','stcc'}:raise RuntimeError('Unknown activation brand')
    root=work/'app/src/main/java/com/nuvio/tv/petalix'
    coordinator=root/'ManagedCoordinator.kt'
    text=coordinator.read_text()
    if 'ActivationSession.ready(' in text:
        if '86_400_000L' in text or 'if(!ready)throw ActivationRequired()' in text:
            raise RuntimeError('Partly applied activation repair')
        return
    old='    val ready:Boolean get()=runCatching { token().isNotEmpty()&&Instant.parse(prefs.getString("expires","")).toEpochMilli()>System.currentTimeMillis()&&System.currentTimeMillis()-prefs.getLong("verified",0)<86_400_000L }.getOrDefault(false)'
    text=once(text,old,'''    val hasSavedActivation:Boolean get()=token().isNotEmpty()
    val ready:Boolean get()=ActivationSession.ready(hasSavedActivation,prefs.getString("expires",""),System.currentTimeMillis())''','saved access')
    # Status and updater requests must be able to confirm an online renewal even
    # when the previously cached subscription expiry has passed.
    guard='        if(!ready)throw ActivationRequired()'
    if text.count(guard)!=2:raise RuntimeError('Activation refresh/update guards need review')
    text=text.replace(guard,'        if(!hasSavedActivation)throw ActivationRequired()')
    receipt='        acknowledge()'
    if text.count(receipt)!=2:raise RuntimeError('Activation receipt bindings need review')
    text=text.replace(receipt,'        ActivationSession.acknowledge { acknowledge() }')
    coordinator.write_text(text)

    activity=root/'ActivationActivity.kt'
    text=activity.read_text()
    start=text.index('        button.setOnClickListener{')
    end=text.index('        setContentView(',start)
    original=text[start:end]
    if original.count('manager.activate(code)')!=1 or 'manager.refresh()' in original:
        raise RuntimeError('Activation screen needs review')
    # Retain the existing branded PIN form and validation. Only saved sessions
    # get an automatic status check and a connection retry without PIN entry.
    form=original.replace('        button.setOnClickListener{','            button.setOnClickListener{',1)
    form=form.replace('startActivity(Intent(this@ActivationActivity,MainActivity::class.java));finish()','openApp()')
    replacement='''        fun openApp(){startActivity(Intent(this@ActivationActivity,MainActivity::class.java));finish()}
        fun showPin(){
            pin.visibility=android.view.View.VISIBLE
            button.text="Activate this TV";button.isEnabled=true
FORM        }
        fun restoreSaved(){
            pin.visibility=android.view.View.GONE
            button.text="Retry connection";button.isEnabled=false
            message.text="Restoring your saved activation…"
            button.setOnClickListener{restoreSaved()}
            lifecycleScope.launch {
                try { manager.refresh();openApp() }
                catch(e:ActivationRequired){showPin();message.text="Saved activation was not accepted. Contact your provider or enter your current PIN.";pin.requestFocus()}
                catch(e:kotlinx.coroutines.CancellationException){throw e}
                catch(_:Exception){message.text="Cannot check access right now. Check your connection, then select Retry connection. Your saved activation is retained.";button.isEnabled=true;button.requestFocus()}
            }
        }
        showPin()
        if(manager.hasSavedActivation)restoreSaved()
'''.replace('FORM',form)
    text=text[:start]+replacement+text[end:]
    text=once(text,'        pin.requestFocus()','        if(!manager.hasSavedActivation)pin.requestFocus()','initial focus')
    activity.write_text(text)
    local=pathlib.Path(__file__).resolve().parent
    shutil.copyfile(local/'ActivationSession.kt',root/'ActivationSession.kt')
    tests=work/'app/src/test/java/com/nuvio/tv/petalix';tests.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(local/'ActivationSessionTest.kt',tests/'ActivationSessionTest.kt')
