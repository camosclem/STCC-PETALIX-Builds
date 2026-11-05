from pathlib import Path
import shutil

def one(text,old,new,label):
    if text.count(old)!=1:raise RuntimeError("Remote-control anchor changed: "+label)
    return text.replace(old,new)

def apply(work):
    root=Path(work)/"app/src/main/java/com/nuvio/tv"
    shutil.copyfile(Path(__file__).with_name("PetalixRemoteControl.kt"),root/"petalix/PetalixRemoteControl.kt")
    p=root/"petalix/ManagedCoordinator.kt";s=p.read_text()
    s=one(s,'"management-v4",','"management-v4","remote-control-v1",',"capability")
    s=one(s,'    suspend fun appUpdate():JSONObject {','''    suspend fun remoteControl(sessionId:String,action:String,ack:Long):JSONObject {
        if(!ready)throw ActivationRequired()
        return post("remote-control",report().put("sessionId",sessionId).put("action",action).put("ack",ack),token())
    }
    suspend fun appUpdate():JSONObject {''',"authenticated remote endpoint")
    p.write_text(s)
    p=root/"MainActivity.kt";s=p.read_text()
    s=one(s,'    override fun onResume() {\n        super.onResume()',
        '    override fun onResume() {\n        super.onResume()\n        com.nuvio.tv.petalix.PetalixRemoteControl.attach(this,petalix)',"foreground start")
    s=one(s,'    override fun onPause() {','    override fun onPause() {\n        com.nuvio.tv.petalix.PetalixRemoteControl.detach(this,petalix)',"foreground stop")
    p.write_text(s)
