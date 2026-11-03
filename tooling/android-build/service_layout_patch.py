"""Install separate service landing compositions while retaining catalog and playback state."""
from pathlib import Path
import shutil

def apply(work):
    root=Path(work)/'app/src/main/java/com/nuvio/tv/petalix/ui2'
    path=root/'Petalix2Services.kt'
    text=path.read_text()
    start=text.index('@Composable\nfun Petalix2ServiceScreen(')
    end=text.index('/** Import the saved, service-specific queries.',start)
    text=text[:start]+'''@Composable
fun Petalix2ServiceScreen(onHome:()->Unit,onDetail:(String,String,String)->Unit,viewModel:PetalixServiceViewModel=hiltViewModel()) {
    DistinctPetalixServiceScreen(onHome,onDetail,viewModel)
}

'''+text[end:]
    path.write_text(text)
    shutil.copyfile(Path(__file__).with_name('PetalixServiceLayouts.kt'),root/'PetalixServiceLayouts.kt')
