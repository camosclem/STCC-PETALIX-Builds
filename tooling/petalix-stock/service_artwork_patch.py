"""Bundle photographic service cards and bind the displayed native shortcut row."""
from pathlib import Path
import hashlib,json,shutil,urllib.request
from home_visual_owner_patch import replace
HERE=Path(__file__).resolve().parent

def apply(root):
    source=root/'app/src/main/java/com/nuvio/tv'
    art=root/'app/src/main/assets/petalix/service-art-v1'
    art.mkdir(parents=True,exist_ok=True)
    entries=json.loads((HERE/'service-artwork.json').read_text())
    for entry in entries.values():
        data=urllib.request.urlopen(urllib.request.Request(entry['url'],headers={'User-Agent':'Mozilla/5.0'}),timeout=45).read()
        if hashlib.sha256(data).hexdigest()!=entry['sha256']:raise ValueError('Service artwork changed; review required')
        (art/entry['filename']).write_bytes(data)
    categories=source/'petalix/PetalixHomeCategories.kt'
    replace(categories, 'artworkByService[folderId]?.let(::homeArtworkUri)',
        'if (folderId in setOf('+','.join('"'+key+'"' for key in entries)+')) "file:///android_asset/petalix/service-art-v1/$folderId.jpg" else artworkByService[folderId]?.let(::homeArtworkUri)')
    cards=source/'ui/screens/home/ModernHomeRows.kt'
    replace(cards, 'val focusGifUrl = when {', 'val focusGifUrl = when {\n        petalixArtwork != null -> null')
    replace(cards, 'petalixArtwork != null -> ContentScale.FillBounds', 'petalixArtwork != null -> ContentScale.Crop')
    replace(cards, '''                    if (payload != null) {
                        com.nuvio.tv.petalix.PetalixServiceMediaOverlay(''', '''                    if (payload != null) {
                        com.nuvio.tv.petalix.PetalixServiceMediaOverlay(''')
    replace(cards, '                    if (payload != null) {', '\
                    if (petalixArtwork != null && petalixLogoUri != null && payload?.folderId != "folder-petalix-britbox") {\n                        AsyncImage(model = petalixLogoUri, contentDescription = null, contentScale = ContentScale.Fit,\n                            modifier = Modifier.matchParentSize().padding(horizontal = 38.dp, vertical = 32.dp))\n                    }\n                    if (payload != null) {')
    replace(cards, '''                    isFocused = it.isFocused
                    onFocusStateChanged(it.isFocused)''', '''                    isFocused = it.isFocused
                    val categoryTitle = payload?.takeIf {
                        it.collectionId == com.nuvio.tv.petalix.PETALIX_LIVE_SHORTCUT_COLLECTION
                    }?.folderId?.let { com.nuvio.tv.petalix.petalixHomeShortcut(it)?.title }
                    if (it.isFocused) com.nuvio.tv.petalix.PetalixLiveCategoryFocus.category.value = categoryTitle
                    else if (categoryTitle != null && com.nuvio.tv.petalix.PetalixLiveCategoryFocus.category.value == categoryTitle)
                        com.nuvio.tv.petalix.PetalixLiveCategoryFocus.category.value = null
                    onFocusStateChanged(it.isFocused)''')
    shutil.copyfile(HERE/'PetalixLocalCandidate.kt',source/'petalix/PetalixLocalCandidate.kt')
    shutil.copyfile(HERE/'PetalixLocalCandidateTest.kt',root/'app/src/test/java/com/nuvio/tv/petalix/PetalixLocalCandidateTest.kt')

    from tv_presentation_patch import apply as apply_tv_presentation
    apply_tv_presentation(root)
