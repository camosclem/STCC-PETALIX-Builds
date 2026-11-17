"""Bundle photographic service cards and bind the displayed native shortcut row."""
from pathlib import Path
import io,json,shutil,urllib.request
from PIL import Image
from home_visual_owner_patch import replace
HERE=Path(__file__).resolve().parent

def apply(root):
    source=root/'app/src/main/java/com/nuvio/tv'
    art=root/'app/src/main/assets/petalix/service-art-v1'
    art.mkdir(parents=True,exist_ok=True)
    entries=json.loads((HERE/'service-artwork.json').read_text())
    for entry in entries.values():
        with urllib.request.urlopen(urllib.request.Request(entry['url'],headers={'User-Agent':'Mozilla/5.0'}),timeout=45) as response:
            data=response.read(10_000_001)
        if len(data)>10_000_000:raise ValueError('Service artwork is too large')
        # Approved artwork feeds may update in place. Validate the image, not an old fingerprint.
        Image.open(io.BytesIO(data)).verify()
        (art/entry['filename']).write_bytes(data)
    categories=source/'petalix/PetalixHomeCategories.kt'
    replace(categories, 'artworkByService[folderId]?.let(::homeArtworkUri)',
        'artworkByService[folderId]?.let(::homeArtworkUri) ?: if (folderId in setOf('+','.join('"'+key+'"' for key in entries)+')) "file:///android_asset/petalix/service-art-v1/$folderId.jpg" else null')
    cards=source/'ui/screens/home/ModernHomeRows.kt'
    replace(cards, 'val focusGifUrl = when {', 'val focusGifUrl = when {\n        petalixArtwork != null -> null')
    replace(cards, 'petalixArtwork != null -> ContentScale.FillBounds', 'petalixArtwork != null -> ContentScale.Crop')
    replace(cards, '''                    if (payload != null) {
                        com.nuvio.tv.petalix.PetalixServiceMediaOverlay(''', '''                    if (payload != null) {
                        com.nuvio.tv.petalix.PetalixServiceMediaOverlay(''')
    replace(cards, '                    if (payload != null) {', '\
                    if (petalixArtwork != null && petalixLogoUri != null && payload?.folderId in setOf("folder-OA4T8NYO", "folder-IQTF27K8", "folder-9YQK1ZF1", "folder-ILJM8PD0")) {\n                        AsyncImage(model = petalixLogoUri, contentDescription = null, contentScale = ContentScale.Fit,\n                            modifier = Modifier.matchParentSize().padding(horizontal = 38.dp, vertical = 32.dp))\n                    }\n                    if (payload != null) {')
    replace(cards, '''                    isFocused = it.isFocused
                    onFocusStateChanged(it.isFocused)''', '''                    isFocused = it.isFocused
                    val categoryTitle = payload?.takeIf {
                        it.collectionId == com.nuvio.tv.petalix.PETALIX_LIVE_SHORTCUT_COLLECTION
                    }?.folderId?.let { com.nuvio.tv.petalix.petalixHomeShortcut(it)?.title }
                    if (it.isFocused) com.nuvio.tv.petalix.PetalixLiveCategoryFocus.category.value = categoryTitle
                    else if (categoryTitle != null && com.nuvio.tv.petalix.PetalixLiveCategoryFocus.category.value == categoryTitle)
                        com.nuvio.tv.petalix.PetalixLiveCategoryFocus.category.value = null
                    onFocusStateChanged(it.isFocused)''')
    # The original branded cards already contain their logo and caption.
    replace(cards, 'if (serviceTagline != null) {',
        'if (serviceTagline != null && payload?.folderId !in setOf("folder-WFGY1XPD", "folder-AT8KYHZO", "folder-8ZBZOMW9", "folder-LN9SPMQN", "folder-petalix-britbox")) {')
    shutil.copyfile(HERE/'PetalixLocalCandidate.kt',source/'petalix/PetalixLocalCandidate.kt')
    shutil.copyfile(HERE/'PetalixLocalCandidateTest.kt',root/'app/src/test/java/com/nuvio/tv/petalix/PetalixLocalCandidateTest.kt')

    from tv_presentation_patch import apply as apply_tv_presentation
    apply_tv_presentation(root)
