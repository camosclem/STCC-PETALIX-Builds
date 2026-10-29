"""Larger in-app branding in the combined test APKs, with Chris's new Petalix wordmark."""
import hashlib,pathlib,shutil
from playlist_patch import replace_once

RECIPE='seek-service-templates-branding-test-v2'
PETALIX_LOGO_SHA256='ef789202dbef793eadc6af20840f4beeed241802bbf0167a756ed1e92264a30f'
ROOT='app/src/main/java/com/nuvio/tv/'

def apply(work,brand):
    local=pathlib.Path(__file__).parent;root=work/ROOT
    for name in ['BrandLogoAssets.kt']:
        shutil.copyfile(local/name,root/'petalix'/name)
    shutil.copyfile(local/'BrandLogoBoundsTest.kt',work/'app/src/test/java/com/nuvio/tv/petalix/BrandLogoBoundsTest.kt')
    logo=local/'petalix_metallic_logo.png'
    if hashlib.sha256(logo.read_bytes()).hexdigest()!=PETALIX_LOGO_SHA256:raise RuntimeError('The requested Petalix logo checksum does not match')
    if brand=='petalix':
        targets=list((work/'app/src/main/res/drawable').glob('app_logo_wordmark*.png'))
        if len(targets)<6:raise RuntimeError('Theme wordmark resources need review')
        for target in targets:shutil.copyfile(logo,target)
    # Crop only the transparent layout padding at render time, keeping alpha and artwork intact.
    wordmark=root/'ui/components/BrandWordmark.kt'
    replace_once(wordmark,'import androidx.compose.runtime.Composable','import androidx.compose.runtime.Composable\nimport androidx.compose.runtime.remember\nimport androidx.compose.ui.platform.LocalContext\nimport androidx.compose.ui.graphics.asImageBitmap\nimport androidx.compose.ui.graphics.painter.BitmapPainter')
    replace_once(wordmark,'    Image(\n        painter = painterResource(id = drawableOverride ?: NuvioTheme.currentTheme.brandWordmarkResource),','''    val context=LocalContext.current
    val resource=drawableOverride ?: NuvioTheme.currentTheme.brandWordmarkResource
    val bitmap=remember(context,resource){com.nuvio.tv.petalix.BrandLogoAssets.bitmap(context,resource)}
    val image=bitmap?.let{remember(it){BitmapPainter(it.asImageBitmap())}}
    Image(
        painter = image ?: painterResource(id = resource),''')
    activity=root/'MainActivity.kt'
    replace_once(activity,'contentDescription = stringResource(R.string.app_name),\n                                    modifier = Modifier\n                                        .fillMaxWidth()\n                                        .height(42.dp)','contentDescription = stringResource(R.string.app_name),\n                                    modifier = Modifier\n                                        .fillMaxWidth()\n                                        .height(76.dp)')
    modern=root/'ModernSidebarBlurPanel.kt'
    replace_once(modern,'.fillMaxWidth(0.72f)\n                        .height(36.dp)','.fillMaxWidth(0.96f)\n                        .height(72.dp)')
    startup=root/'ui/components/StartupSplashScreen.kt'
    replace_once(startup,'modifier = Modifier.height(48.dp),','modifier = Modifier.widthIn(max=520.dp).height(112.dp),')
    # widthIn is deliberately explicit because the metallic wordmark is very wide.
    if 'import androidx.compose.foundation.layout.widthIn' not in startup.read_text():
        text=startup.read_text();startup.write_text(text.replace('package com.nuvio.tv.ui.components','package com.nuvio.tv.ui.components\n\nimport androidx.compose.foundation.layout.widthIn',1))
    profile=root/'ui/screens/profile/ProfileSelectionScreen.kt'
    replace_once(profile,'val LogoHeight = 44.dp','val LogoHeight = 72.dp')
    about=root/'ui/screens/settings/AboutScreen.kt'
    replace_once(about,'height = 40.dp,','height = 72.dp,')
    settings=root/'ui/screens/settings/SettingsDesignSystem.kt'
    replace_once(settings,'.fillMaxWidth(0.9f)\n                .height(72.dp)','.fillMaxWidth()\n                .height(96.dp)')
    activation=root/'petalix/ActivationActivity.kt'
    if brand=='petalix':
        drawable='app_logo_wordmark';old_size='dp(280),dp(85)';new_size='dp(440),dp(120)'
    else:
        drawable='stcc_logo';old_size='dp(210),dp(210)';new_size='dp(280),dp(280)'
    replace_once(activation,f'setImageResource(R.drawable.{drawable});scaleType=ImageView.ScaleType.FIT_CENTER}},LinearLayout.LayoutParams({old_size})',f'setImageBitmap(BrandLogoAssets.bitmap(this@ActivationActivity,R.drawable.{drawable}));scaleType=ImageView.ScaleType.FIT_CENTER}},LinearLayout.LayoutParams({new_size})')
    coordinator=root/'petalix/ManagedCoordinator.kt'
    replace_once(coordinator,'"seek-previews-test-v1"','"seek-previews-test-v1","large-branding-test-v2"')
