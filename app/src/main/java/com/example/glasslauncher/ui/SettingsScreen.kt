package com.example.glasslauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.glasslauncher.SystemActions
import com.example.glasslauncher.data.AnimSpeed
import com.example.glasslauncher.data.DrawerSort
import com.example.glasslauncher.data.GestureAction
import com.example.glasslauncher.data.IconColorMode
import com.example.glasslauncher.data.IconShape
import com.example.glasslauncher.data.LauncherSettings
import com.example.glasslauncher.data.OpenAnim
import com.example.glasslauncher.data.PageEffect
import com.example.glasslauncher.data.ScreenOrientation
import com.example.glasslauncher.data.SearchBarPos
import com.example.glasslauncher.data.UiApp
import com.kyant.backdrop.Backdrop
import kotlin.math.roundToInt

const val REPO_URL = "https://github.com/brinywharf/GlassLauncher"

private enum class Page(val en: String, val tr: String) {
    LOOK("Icons", "İkonlar"),
    GLASS("Liquid glass", "Sıvı cam"),
    HOME("Home screen", "Ana ekran"),
    DOCK("Dock", "Dock"),
    DRAWER("App drawer", "Uygulama çekmecesi"),
    GESTURES("Gestures", "Hareketler"),
    MOTION("Animations", "Animasyonlar"),
    SYSTEM("System", "Sistem"),
    HIDDEN("Hidden apps", "Gizli uygulamalar"),
    BACKUP("Backup & restore", "Yedekle ve geri yükle"),
    ABOUT("About", "Hakkında"),
    PICK_UP("Choose app", "Uygulama seç"),
    PICK_DOWN("Choose app", "Uygulama seç"),
    PICK_DOUBLE("Choose app", "Uygulama seç");

    val title get() = t(en, tr)
    val parent: Page? get() = if (this == PICK_UP || this == PICK_DOWN || this == PICK_DOUBLE) GESTURES else null
}

@Composable
fun SettingsScreen(
    settings: LauncherSettings,
    backdrop: Backdrop,
    apps: List<UiApp>,
    hiddenApps: List<UiApp>,
    hasWallpaper: Boolean,
    onUpdate: ((LauncherSettings) -> LauncherSettings) -> Unit,
    onUnhide: (String) -> Unit,
    onResetColors: () -> Unit,
    onResetSettings: () -> Unit,
    onPickWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var page by remember { mutableStateOf<Page?>(null) }
    BackHandler(enabled = page != null) { page = page?.parent }

    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 8.dp)
            .sheetPanel()
            .navigationBarsPadding(),
    ) {
        // Header
        Row(
            Modifier.padding(start = 12.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val current = page
            if (current != null) {
                RoundButton(Icons.Default.KeyboardArrowLeft) { page = current.parent }
                Spacer(Modifier.width(8.dp))
            } else {
                Spacer(Modifier.width(12.dp))
            }
            Text(
                current?.title ?: t("Settings", "Ayarlar"),
                style = PlainStyle.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(10.dp))
            if (current == null) AlphaBadge()
            Spacer(Modifier.weight(1f))
            RoundButton(Icons.Default.Close, onClose)
        }

        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val forward = targetState != null && (initialState == null || targetState?.parent == initialState)
                if (forward) {
                    (slideInHorizontally { it / 2 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 } + fadeOut())
                } else {
                    (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { it / 2 } + fadeOut())
                }
            },
            label = "settings-page",
            modifier = Modifier.weight(1f),
        ) { p ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
            ) {
                when (p) {
                    null -> MainPage(hiddenApps.size) { page = it }
                    Page.LOOK -> LookPage(settings, onUpdate, onResetColors)
                    Page.GLASS -> GlassPage(settings, onUpdate)
                    Page.HOME -> HomePage(settings, hasWallpaper, onUpdate, onPickWallpaper, onClearWallpaper)
                    Page.DOCK -> DockPage(settings, onUpdate)
                    Page.DRAWER -> DrawerPage(settings, onUpdate)
                    Page.GESTURES -> GesturesPage(settings, apps, onUpdate) { page = it }
                    Page.MOTION -> MotionPage(settings, onUpdate)
                    Page.SYSTEM -> SystemPage(settings, onUpdate)
                    Page.HIDDEN -> HiddenPage(hiddenApps, backdrop, onUnhide)
                    Page.BACKUP -> BackupPage(backdrop, onExport, onImport, onResetSettings)
                    Page.ABOUT -> AboutPage(backdrop)
                    Page.PICK_UP -> AppPickPage(apps) { k -> onUpdate { it.copy(swipeUpApp = k) }; page = Page.GESTURES }
                    Page.PICK_DOWN -> AppPickPage(apps) { k -> onUpdate { it.copy(swipeDownApp = k) }; page = Page.GESTURES }
                    Page.PICK_DOUBLE -> AppPickPage(apps) { k -> onUpdate { it.copy(doubleTapApp = k) }; page = Page.GESTURES }
                }
            }
        }
    }
}

// ================================================================ Pages

@Composable
private fun MainPage(hiddenCount: Int, open: (Page) -> Unit) {
    Note(
        t("Glass Launcher is in ALPHA: it's still being developed, so expect bugs and missing features.",
            "Glass Launcher ALPHA sürümünde: hâlâ geliştiriliyor, hatalar ve eksik özellikler olabilir.")
    )
    Spacer(Modifier.height(8.dp))
    CategoryRow(Icons.Default.Face, Page.LOOK, t("Shape, color, size, labels, dots", "Şekil, renk, boyut, isimler, noktalar"), open)
    CategoryRow(Icons.Default.Star, Page.GLASS, t("Refraction, blur, frost, light rim", "Kırılma, bulanıklık, buzlanma, kenar ışığı"), open)
    CategoryRow(Icons.Default.Home, Page.HOME, t("Grid, page effect, search bar, wallpaper", "Izgara, sayfa efekti, arama çubuğu, duvar kâğıdı"), open)
    CategoryRow(Icons.Default.ThumbUp, Page.DOCK, t("Size and background", "Boyut ve arka plan"), open)
    CategoryRow(Icons.Default.List, Page.DRAWER, t("Columns, sorting, suggestions, scroller", "Sütunlar, sıralama, öneriler, kaydırıcı"), open)
    CategoryRow(Icons.Default.PlayArrow, Page.GESTURES, t("Swipe up/down, double-tap", "Yukarı/aşağı kaydırma, çift dokunma"), open)
    CategoryRow(Icons.Default.Refresh, Page.MOTION, t("Speed, app open animation, haptics", "Hız, uygulama açılış animasyonu, titreşim"), open)
    CategoryRow(Icons.Default.Settings, Page.SYSTEM, t("Status bar, rotation, refresh rate", "Durum çubuğu, döndürme, yenileme hızı"), open)
    CategoryRow(Icons.Default.Lock, Page.HIDDEN, t("$hiddenCount hidden", "$hiddenCount gizli"), open)
    CategoryRow(Icons.Default.Build, Page.BACKUP, t("Save or restore your setup", "Düzenini kaydet veya geri yükle"), open)
    CategoryRow(Icons.Default.Info, Page.ABOUT, t("Version, default home app", "Sürüm, varsayılan ana ekran"), open)
}

@Composable
private fun LookPage(s: LauncherSettings, onUpdate: ((LauncherSettings) -> LauncherSettings) -> Unit, onResetColors: () -> Unit) {
    val context = LocalContext.current
    SettingSwitch(t("Liquid glass icons", "Sıvı cam ikonlar"), t("Auto-generated for every app", "Her uygulama için otomatik"), s.glassIcons) { v ->
        onUpdate { it.copy(glassIcons = v) }
    }
    if (s.glassIcons) {
        Label(t("Icon shape", "İkon şekli"))
        ChoiceChips(
            listOf(
                IconShape.SQUIRCLE to t("Squircle", "Yumuşak kare"),
                IconShape.ROUNDED to t("Rounded", "Yuvarlak köşe"),
                IconShape.CIRCLE to t("Circle", "Daire"),
                IconShape.SQUARE to t("Square", "Kare"),
                IconShape.TEARDROP to t("Teardrop", "Damla"),
            ),
            s.iconShape,
        ) { v -> onUpdate { it.copy(iconShape = v) } }
        Label(t("Icon color", "İkon rengi"))
        ChoiceChips(
            listOf(
                IconColorMode.AUTO to t("From app", "Uygulamadan"),
                IconColorMode.SINGLE to t("One color", "Tek renk"),
                IconColorMode.CLEAR to t("Clear glass", "Şeffaf cam"),
            ),
            s.iconColorMode,
        ) { v -> onUpdate { it.copy(iconColorMode = v) } }
        if (s.iconColorMode == IconColorMode.SINGLE) {
            Spacer(Modifier.height(12.dp))
            ColorPicker(s.iconColor, null) { c -> if (c != null) onUpdate { it.copy(iconColor = c) } }
        }
        Hint(t("Tip: long-press any app → Edit to give it its own color.", "İpucu: bir uygulamaya uzun bas → Düzenle ile ona özel renk ver."))
        Spacer(Modifier.height(8.dp))
        SheetButton(t("Reset all custom colors", "Tüm özel renkleri sıfırla"), onResetColors)
    }
    SettingSlider(t("Icon size", "İkon boyutu"), s.iconSize.toFloat(), 40f..84f, "${s.iconSize} dp", steps = 21) { v ->
        onUpdate { it.copy(iconSize = v.roundToInt()) }
    }
    IntSlider(t("Label size", "İsim boyutu"), s.labelSize, 9..16) { v -> onUpdate { it.copy(labelSize = v) } }
    SettingSwitch(t("Notification dots", "Bildirim noktaları"), t("Needs notification access", "Bildirim erişimi gerekir"), s.notificationDots) { v ->
        onUpdate { it.copy(notificationDots = v) }
        if (v && !SystemActions.hasNotificationAccess(context)) SystemActions.openNotificationAccessSettings(context)
    }
}

@Composable
private fun GlassPage(s: LauncherSettings, onUpdate: ((LauncherSettings) -> LauncherSettings) -> Unit) {
    SettingSlider(t("Refraction (edge bending)", "Kırılma (kenar bükülmesi)"), s.refraction, 0.3f..3f, "%.1f×".format(s.refraction)) { v ->
        onUpdate { it.copy(refraction = v) }
    }
    SettingSlider(t("Blur", "Bulanıklık"), s.blur, 0f..3f, "%.1f×".format(s.blur)) { v -> onUpdate { it.copy(blur = v) } }
    SettingSlider(t("Frost (milky surface)", "Buzlanma (süt beyazı yüzey)"), s.frost, 0f..0.35f, "${(s.frost * 100).roundToInt()}%") { v ->
        onUpdate { it.copy(frost = v) }
    }
    SettingSwitch(t("Light rim & gloss", "Kenar ışığı ve parlaklık"), null, s.rimLight) { v -> onUpdate { it.copy(rimLight = v) } }
    Hint(t("Live glass is used on the clock card, dock, search bar and menus.", "Canlı cam saat kartında, dock'ta, arama çubuğunda ve menülerde kullanılır."))
}

@Composable
private fun HomePage(
    s: LauncherSettings,
    hasWallpaper: Boolean,
    onUpdate: ((LauncherSettings) -> LauncherSettings) -> Unit,
    onPickWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
) {
    IntSlider(t("Columns", "Sütunlar"), s.homeColumns, 3..8) { v -> onUpdate { it.copy(homeColumns = v) } }
    IntSlider(t("Rows", "Satırlar"), s.homeRows, 3..8) { v -> onUpdate { it.copy(homeRows = v) } }
    SettingSwitch(t("Show app names", "Uygulama isimlerini göster"), null, s.showLabels) { v -> onUpdate { it.copy(showLabels = v) } }
    Label(t("Page transition", "Sayfa geçişi"))
    ChoiceChips(
        listOf(
            PageEffect.SLIDE to t("Slide", "Kaydır"),
            PageEffect.CUBE to t("Cube", "Küp"),
            PageEffect.STACK to t("Card stack", "Kart yığını"),
            PageEffect.ZOOM to t("Zoom", "Yakınlaştır"),
            PageEffect.ROTATE to t("Rotate", "Döndür"),
            PageEffect.FADE to t("Fade", "Solma"),
        ),
        s.pageEffect,
    ) { v -> onUpdate { it.copy(pageEffect = v) } }
    Label(t("Search bar", "Arama çubuğu"))
    ChoiceChips(
        listOf(
            SearchBarPos.BOTTOM to t("Bottom", "Altta"),
            SearchBarPos.TOP to t("Top", "Üstte"),
            SearchBarPos.HIDDEN to t("Hidden", "Gizli"),
        ),
        s.searchBarPos,
    ) { v -> onUpdate { it.copy(searchBarPos = v) } }
    SettingSwitch(t("“At a glance” card", "“Bir bakışta” kartı"), null, s.showGlance) { v -> onUpdate { it.copy(showGlance = v) } }
    SettingSwitch(t("24-hour clock", "24 saat biçimi"), null, s.clock24h) { v -> onUpdate { it.copy(clock24h = v) } }
    SettingSwitch(t("Page indicator", "Sayfa göstergesi"), null, s.showPageDots) { v -> onUpdate { it.copy(showPageDots = v) } }
    SettingSwitch(
        t("Add new apps to home screen", "Yeni uygulamaları ana ekrana ekle"), null, s.addNewAppsToHome,
    ) { v -> onUpdate { it.copy(addNewAppsToHome = v) } }
    Label(t("Wallpaper", "Duvar kâğıdı"))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SheetButton(t("Change", "Değiştir"), onPickWallpaper)
        if (hasWallpaper) SheetButton(t("Default background", "Varsayılan arka plan"), onClearWallpaper)
    }
}

@Composable
private fun DockPage(s: LauncherSettings, onUpdate: ((LauncherSettings) -> LauncherSettings) -> Unit) {
    SettingSwitch(t("Show dock", "Dock'u göster"), null, s.showDock) { v -> onUpdate { it.copy(showDock = v) } }
    SettingSwitch(t("Glass background", "Cam arka plan"), null, s.dockBackground) { v -> onUpdate { it.copy(dockBackground = v) } }
    IntSlider(t("Dock icons", "Dock ikonları"), s.dockCount, 3..7) { v -> onUpdate { it.copy(dockCount = v) } }
}

@Composable
private fun DrawerPage(s: LauncherSettings, onUpdate: ((LauncherSettings) -> LauncherSettings) -> Unit) {
    IntSlider(t("Columns", "Sütunlar"), s.drawerColumns, 3..9) { v -> onUpdate { it.copy(drawerColumns = v) } }
    Label(t("Sort apps", "Uygulamaları sırala"))
    ChoiceChips(
        listOf(
            DrawerSort.ALPHABETICAL to t("A–Z", "A–Z"),
            DrawerSort.MOST_USED to t("Most used", "En çok kullanılan"),
            DrawerSort.NEWEST to t("Newest", "En yeni"),
        ),
        s.drawerSort,
    ) { v -> onUpdate { it.copy(drawerSort = v) } }
    SettingSwitch(t("Show app names", "Uygulama isimlerini göster"), null, s.drawerLabels) { v -> onUpdate { it.copy(drawerLabels = v) } }
    SettingSwitch(t("Suggested apps row", "Önerilen uygulamalar satırı"), null, s.showSuggestions) { v -> onUpdate { it.copy(showSuggestions = v) } }
    SettingSwitch(t("A–Z fast scroller", "A–Z hızlı kaydırıcı"), null, s.drawerFastScroll) { v -> onUpdate { it.copy(drawerFastScroll = v) } }
    SettingSlider(t("Background darkness", "Arka plan koyuluğu"), s.drawerDim, 0f..0.8f, "${(s.drawerDim * 100).roundToInt()}%") { v ->
        onUpdate { it.copy(drawerDim = v) }
    }
}

@Composable
private fun GesturesPage(
    s: LauncherSettings,
    apps: List<UiApp>,
    onUpdate: ((LauncherSettings) -> LauncherSettings) -> Unit,
    open: (Page) -> Unit,
) {
    val context = LocalContext.current
    fun check(a: GestureAction) {
        if (a == GestureAction.LOCK_SCREEN && !SystemActions.isLockServiceEnabled(context)) SystemActions.openAccessibilitySettings(context)
    }
    GestureSetting(t("Swipe up", "Yukarı kaydır"), s.swipeUp, s.swipeUpApp, apps, { open(Page.PICK_UP) }) { a ->
        check(a); onUpdate { it.copy(swipeUp = a) }
    }
    GestureSetting(t("Swipe down", "Aşağı kaydır"), s.swipeDown, s.swipeDownApp, apps, { open(Page.PICK_DOWN) }) { a ->
        check(a); onUpdate { it.copy(swipeDown = a) }
    }
    GestureSetting(t("Double-tap", "Çift dokun"), s.doubleTap, s.doubleTapApp, apps, { open(Page.PICK_DOUBLE) }) { a ->
        check(a); onUpdate { it.copy(doubleTap = a) }
    }
    Note(
        t("“Lock screen” needs Glass Launcher turned on in Accessibility. If Android says the setting is “restricted”: App info → ⋮ → Allow restricted settings.",
            "“Ekranı kilitle” için Erişilebilirlik'te Glass Launcher açık olmalı. Android “kısıtlı” derse: Uygulama bilgisi → ⋮ → Kısıtlanmış ayarlara izin ver.")
    )
}

@Composable
private fun GestureSetting(
    title: String,
    value: GestureAction,
    appKey: String,
    apps: List<UiApp>,
    onPickApp: () -> Unit,
    onChange: (GestureAction) -> Unit,
) {
    Label(title)
    ChoiceChips(
        listOf(
            GestureAction.NONE to t("Nothing", "Hiçbiri"),
            GestureAction.APP_DRAWER to t("App drawer", "Çekmece"),
            GestureAction.NOTIFICATIONS to t("Notifications", "Bildirimler"),
            GestureAction.QUICK_SETTINGS to t("Quick settings", "Hızlı ayarlar"),
            GestureAction.LOCK_SCREEN to t("Lock screen", "Ekranı kilitle"),
            GestureAction.SEARCH to t("Search", "Arama"),
            GestureAction.LAUNCHER_SETTINGS to t("Launcher settings", "Başlatıcı ayarları"),
            GestureAction.OPEN_APP to t("Open app…", "Uygulama aç…"),
        ),
        value,
        onChange,
    )
    if (value == GestureAction.OPEN_APP) {
        val app = apps.firstOrNull { it.key == appKey }
        Spacer(Modifier.height(8.dp))
        SheetButton(app?.label?.let { "→ $it" } ?: t("Choose app", "Uygulama seç"), onPickApp)
    }
}

@Composable
private fun AppPickPage(apps: List<UiApp>, onPick: (String) -> Unit) {
    apps.forEach { app ->
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable { onPick(app.key) }
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(app.icon, null, Modifier.size(38.dp))
            Spacer(Modifier.width(14.dp))
            Text(app.label, style = PlainStyle.copy(fontSize = 16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MotionPage(s: LauncherSettings, onUpdate: ((LauncherSettings) -> LauncherSettings) -> Unit) {
    Label(t("Animation speed", "Animasyon hızı"))
    ChoiceChips(
        listOf(
            AnimSpeed.RELAXED to t("Relaxed", "Rahat"),
            AnimSpeed.NORMAL to t("Normal", "Normal"),
            AnimSpeed.FAST to t("Fast", "Hızlı"),
            AnimSpeed.FASTER to t("Faster", "Çok hızlı"),
        ),
        s.animSpeed,
    ) { v -> onUpdate { it.copy(animSpeed = v) } }
    Label(t("App open animation", "Uygulama açılış animasyonu"))
    ChoiceChips(
        listOf(
            OpenAnim.ZOOM to t("Zoom from icon", "İkondan büyüt"),
            OpenAnim.REVEAL to t("Circle reveal", "Açılarak göster"),
            OpenAnim.SYSTEM to t("System default", "Sistem varsayılanı"),
        ),
        s.openAnim,
    ) { v -> onUpdate { it.copy(openAnim = v) } }
    SettingSwitch(t("Haptic feedback", "Dokunsal titreşim"), null, s.haptics) { v -> onUpdate { it.copy(haptics = v) } }
}

@Composable
private fun SystemPage(s: LauncherSettings, onUpdate: ((LauncherSettings) -> LauncherSettings) -> Unit) {
    SettingSwitch(
        t("Maximum refresh rate", "En yüksek yenileme hızı"),
        t("Ask for the screen's top rate (120/144 Hz) on the home screen", "Ana ekranda ekranın en yüksek hızını (120/144 Hz) iste"),
        s.maxRefreshRate,
    ) { v -> onUpdate { it.copy(maxRefreshRate = v) } }
    SettingSwitch(t("Hide status bar", "Durum çubuğunu gizle"), null, s.hideStatusBar) { v -> onUpdate { it.copy(hideStatusBar = v) } }
    Label(t("Screen rotation", "Ekran döndürme"))
    ChoiceChips(
        listOf(
            ScreenOrientation.AUTO to t("Auto", "Otomatik"),
            ScreenOrientation.PORTRAIT to t("Portrait", "Dikey"),
            ScreenOrientation.LANDSCAPE to t("Landscape", "Yatay"),
        ),
        s.orientation,
    ) { v -> onUpdate { it.copy(orientation = v) } }
}

@Composable
private fun HiddenPage(hidden: List<UiApp>, backdrop: Backdrop, onUnhide: (String) -> Unit) {
    if (hidden.isEmpty()) {
        Hint(t("No hidden apps. Long-press an app → Hide app.", "Gizli uygulama yok. Bir uygulamaya uzun bas → Uygulamayı gizle."))
    }
    hidden.forEach { app ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(app.icon, null, Modifier.size(38.dp))
            Spacer(Modifier.width(12.dp))
            Text(app.label, style = PlainStyle.copy(fontSize = 15.sp), modifier = Modifier.weight(1f))
            SheetButton(t("Show", "Göster")) { onUnhide(app.key) }
        }
    }
}

@Composable
private fun BackupPage(backdrop: Backdrop, onExport: () -> Unit, onImport: () -> Unit, onReset: () -> Unit) {
    Hint(
        t("Saves all settings, your home screen, dock, hidden apps, names and colors to a file.",
            "Tüm ayarları, ana ekranını, dock'u, gizli uygulamaları, isimleri ve renkleri bir dosyaya kaydeder.")
    )
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SheetButton(t("Back up", "Yedekle"), onExport)
        SheetButton(t("Restore", "Geri yükle"), onImport)
    }
    Spacer(Modifier.height(20.dp))
    SheetButton(t("Reset all settings", "Tüm ayarları sıfırla"), onReset)
}

@Composable
private fun AboutPage(backdrop: Backdrop) {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Glass Launcher $version", style = PlainStyle.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.width(10.dp))
        AlphaBadge()
    }
    Hint(
        t("Alpha build: features may change, break or disappear between updates.",
            "Alpha sürüm: özellikler güncellemeler arasında değişebilir veya bozulabilir.")
    )
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SheetButton(t("Default home app", "Varsayılan ana ekran")) { SystemActions.openHomeSettings(context) }
        SheetButton("GitHub") { SystemActions.openUrl(context, REPO_URL) }
    }
}

// ================================================================ Building blocks

@Composable
fun AlphaBadge() {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFFFFB300))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text("ALPHA", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RoundButton(icon: ImageVector, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .frostedControl(CircleShape)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color.White) }
}

@Composable
private fun SheetButton(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .height(40.dp)
            .frostedControl(shape)
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = PlainStyle.copy(fontSize = 14.sp), maxLines = 1)
    }
}

@Composable
private fun CategoryRow(icon: ImageVector, page: Page, subtitle: String, open: (Page) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { open(page) }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .frostedControl(CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(page.title, style = PlainStyle.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium))
            Text(subtitle, style = PlainStyle.copy(fontSize = 13.sp, color = Color.White.copy(alpha = 0.65f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Default.KeyboardArrowRight, null, tint = Color.White.copy(alpha = 0.6f))
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = PlainStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = PlainStyle.copy(fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f)),
        modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
private fun Note(text: String) {
    Box(
        Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0x33FFB300))
            .padding(14.dp)
    ) {
        Text(text, style = PlainStyle.copy(fontSize = 13.sp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceChips(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            val isSel = value == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isSel) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.12f))
                    .clickable { onSelect(value) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                Text(
                    label,
                    color = if (isSel) Color.Black else Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

private val switchColors
    @Composable get() = SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = Color(0xFF5C8DFF),
        uncheckedThumbColor = Color.White.copy(alpha = 0.8f),
        uncheckedTrackColor = Color.White.copy(alpha = 0.18f),
        uncheckedBorderColor = Color.White.copy(alpha = 0.35f),
    )

private val sliderColors
    @Composable get() = SliderDefaults.colors(
        thumbColor = Color.White,
        activeTrackColor = Color(0xFF5C8DFF),
        inactiveTrackColor = Color.White.copy(alpha = 0.2f),
    )

@Composable
private fun SettingSwitch(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = PlainStyle.copy(fontSize = 16.sp))
            if (subtitle != null) {
                Text(subtitle, style = PlainStyle.copy(fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f)))
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, colors = switchColors)
    }
}

@Composable
private fun SettingSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    steps: Int = 0,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Row {
            Text(title, style = PlainStyle.copy(fontSize = 16.sp), modifier = Modifier.weight(1f))
            Text(valueText, style = PlainStyle.copy(fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f)))
        }
        Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range, steps = steps, colors = sliderColors)
    }
}

@Composable
private fun IntSlider(title: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    SettingSlider(
        title, value.toFloat(), range.first.toFloat()..range.last.toFloat(), value.toString(),
        steps = (range.last - range.first - 1).coerceAtLeast(0),
    ) { onChange(it.roundToInt()) }
}
