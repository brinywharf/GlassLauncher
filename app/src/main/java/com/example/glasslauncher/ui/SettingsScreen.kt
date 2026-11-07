package com.example.glasslauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.glasslauncher.SystemActions
import com.example.glasslauncher.data.AppInfo
import com.example.glasslauncher.data.IconColorMode
import com.example.glasslauncher.data.LauncherSettings
import com.kyant.backdrop.Backdrop
import kotlin.math.roundToInt

const val REPO_URL = "https://github.com/brinywharf/GlassLauncher"

@Composable
fun SettingsScreen(
    settings: LauncherSettings,
    backdrop: Backdrop,
    hiddenApps: List<AppInfo>,
    hasWallpaper: Boolean,
    onUpdate: ((LauncherSettings) -> LauncherSettings) -> Unit,
    onUnhide: (String) -> Unit,
    onResetColors: () -> Unit,
    onResetSettings: () -> Unit,
    onPickWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 8.dp)
            .liquidGlass(
                backdrop, RoundedCornerShape(topStart = 42.dp, topEnd = 42.dp),
                tint = Color.Black.copy(alpha = 0.40f),
                blurRadius = 22.dp, refractionHeight = 30.dp, refractionAmount = 70.dp,
            )
            .navigationBarsPadding(),
    ) {
        // Header
        Row(
            Modifier.padding(start = 24.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(t("Settings", "Ayarlar"), style = LabelStyle.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold))
            Spacer(Modifier.width(10.dp))
            AlphaBadge()
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(44.dp)
                    .liquidGlass(backdrop, CircleShape, refractionHeight = 10.dp, refractionAmount = 22.dp)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Close, t("Close", "Kapat"), tint = Color.White) }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Note(
                t("Glass Launcher is in ALPHA: it's still being developed, so expect bugs and missing features.",
                    "Glass Launcher ALPHA sürümünde: hâlâ geliştiriliyor, hatalar ve eksik özellikler olabilir.")
            )

            // ------------------------------------------------ Glass
            Section(t("Liquid glass", "Sıvı cam"))
            SettingSlider(
                t("Refraction (edge bending)", "Kırılma (kenar bükülmesi)"),
                settings.refraction, 0.3f..3f, "%.1f×".format(settings.refraction),
            ) { v -> onUpdate { it.copy(refraction = v) } }
            SettingSlider(
                t("Blur", "Bulanıklık"),
                settings.blur, 0f..3f, "%.1f×".format(settings.blur),
            ) { v -> onUpdate { it.copy(blur = v) } }
            SettingSlider(
                t("Frost (milky surface)", "Buzlanma (süt beyazı yüzey)"),
                settings.frost, 0f..0.35f, "${(settings.frost * 100).roundToInt()}%",
            ) { v -> onUpdate { it.copy(frost = v) } }
            SettingSwitch(t("Light rim & gloss", "Kenar ışığı ve parlaklık"), null, settings.rimLight) { v ->
                onUpdate { it.copy(rimLight = v) }
            }

            // ------------------------------------------------ Icons
            Section(t("Icons", "İkonlar"))
            SettingSwitch(
                t("Liquid glass icons", "Sıvı cam ikonlar"),
                t("Auto-generated glass icons for every app", "Her uygulama için otomatik cam ikon"),
                settings.glassIcons,
            ) { v -> onUpdate { it.copy(glassIcons = v) } }
            if (settings.glassIcons) {
                Text(
                    t("Icon color", "İkon rengi"),
                    style = LabelStyle.copy(fontSize = 15.sp),
                    modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
                )
                Segmented(
                    options = listOf(
                        IconColorMode.AUTO to t("From app", "Uygulamadan"),
                        IconColorMode.SINGLE to t("One color", "Tek renk"),
                        IconColorMode.CLEAR to t("Clear glass", "Şeffaf cam"),
                    ),
                    selected = settings.iconColorMode,
                ) { mode -> onUpdate { it.copy(iconColorMode = mode) } }
                if (settings.iconColorMode == IconColorMode.SINGLE) {
                    Spacer(Modifier.height(12.dp))
                    ColorPicker(settings.iconColor, null) { c ->
                        if (c != null) onUpdate { it.copy(iconColor = c) }
                    }
                }
                Text(
                    t("Tip: long-press any app → Edit to give it its own color.",
                        "İpucu: bir uygulamaya uzun bas → Düzenle ile ona özel renk ver."),
                    style = LabelStyle.copy(fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f)),
                    modifier = Modifier.padding(top = 10.dp),
                )
                Spacer(Modifier.height(8.dp))
                GlassChip(t("Reset all custom colors", "Tüm özel renkleri sıfırla"), null, backdrop, onResetColors)
            }
            SettingSlider(
                t("Icon size", "İkon boyutu"),
                settings.iconSize.toFloat(), 40f..84f, "${settings.iconSize} dp", steps = 21,
            ) { v -> onUpdate { it.copy(iconSize = v.roundToInt()) } }
            SettingSwitch(t("Show app names", "Uygulama isimlerini göster"), null, settings.showLabels) { v ->
                onUpdate { it.copy(showLabels = v) }
            }
            SettingSwitch(
                t("Notification dots", "Bildirim noktaları"),
                t("Needs notification access", "Bildirim erişimi gerekir"),
                settings.notificationDots,
            ) { v ->
                onUpdate { it.copy(notificationDots = v) }
                if (v && !SystemActions.hasNotificationAccess(context)) SystemActions.openNotificationAccessSettings(context)
            }

            // ------------------------------------------------ Home
            Section(t("Home screen", "Ana ekran"))
            SettingSwitch(t("“At a glance” card", "“Bir bakışta” kartı"), null, settings.showGlance) { v ->
                onUpdate { it.copy(showGlance = v) }
            }
            SettingSwitch(t("24-hour clock", "24 saat biçimi"), null, settings.clock24h) { v ->
                onUpdate { it.copy(clock24h = v) }
            }
            SettingSwitch(t("Search bar", "Arama çubuğu"), null, settings.showSearchBar) { v ->
                onUpdate { it.copy(showSearchBar = v) }
            }
            IntSlider(t("Home columns", "Ana ekran sütunları"), settings.homeColumns, 3..8) { v ->
                onUpdate { it.copy(homeColumns = v) }
            }
            IntSlider(t("Home rows", "Ana ekran satırları"), settings.homeRows, 3..8) { v ->
                onUpdate { it.copy(homeRows = v) }
            }
            IntSlider(t("Dock size", "Dock boyutu"), settings.dockCount, 3..7) { v ->
                onUpdate { it.copy(dockCount = v) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 10.dp)) {
                GlassChip(t("Change wallpaper", "Duvar kâğıdını değiştir"), null, backdrop, onPickWallpaper)
                if (hasWallpaper) GlassChip(t("Animated", "Hareketli"), null, backdrop, onClearWallpaper)
            }

            // ------------------------------------------------ Drawer
            Section(t("App drawer", "Uygulama çekmecesi"))
            IntSlider(t("Drawer columns", "Çekmece sütunları"), settings.drawerColumns, 3..9) { v ->
                onUpdate { it.copy(drawerColumns = v) }
            }
            SettingSwitch(t("Suggested apps", "Önerilen uygulamalar"), null, settings.showSuggestions) { v ->
                onUpdate { it.copy(showSuggestions = v) }
            }

            // ------------------------------------------------ Gestures
            Section(t("Gestures", "Hareketler"))
            SettingSwitch(
                t("Swipe down for notifications", "Bildirimler için aşağı kaydır"), null,
                settings.swipeDownNotifications,
            ) { v -> onUpdate { it.copy(swipeDownNotifications = v) } }
            SettingSwitch(
                t("Double-tap to lock", "Kilitlemek için çift dokun"),
                t("Needs Glass Launcher turned on in Accessibility", "Erişilebilirlik'te Glass Launcher açık olmalı"),
                settings.doubleTapLock,
            ) { v ->
                onUpdate { it.copy(doubleTapLock = v) }
                if (v && !SystemActions.isLockServiceEnabled(context)) SystemActions.openAccessibilitySettings(context)
            }
            Note(
                t("If Android says a setting is “restricted”: open App info for Glass Launcher → ⋮ menu → Allow restricted settings.",
                    "Android bir ayar için “kısıtlı” derse: Glass Launcher'ın Uygulama bilgisi → ⋮ menü → Kısıtlanmış ayarlara izin ver.")
            )

            // ------------------------------------------------ Hidden apps
            if (hiddenApps.isNotEmpty()) {
                Section(t("Hidden apps", "Gizli uygulamalar"))
                hiddenApps.forEach { app ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.foundation.Image(app.icon, null, Modifier.size(36.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(app.label, style = LabelStyle.copy(fontSize = 15.sp), modifier = Modifier.weight(1f))
                        GlassChip(t("Show", "Göster"), null, backdrop) { onUnhide(app.key) }
                    }
                }
            }

            // ------------------------------------------------ About
            Section(t("About", "Hakkında"))
            Text(
                "Glass Launcher $version",
                style = LabelStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
            )
            Text(
                t("Alpha build: features may change, break or disappear between updates.",
                    "Alpha sürüm: özellikler güncellemeler arasında değişebilir veya bozulabilir."),
                style = LabelStyle.copy(fontSize = 13.sp, color = Color.White.copy(alpha = 0.75f)),
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassChip(t("Default home app", "Varsayılan ana ekran"), null, backdrop) {
                    SystemActions.openHomeSettings(context)
                }
                GlassChip("GitHub", null, backdrop) { SystemActions.openUrl(context, REPO_URL) }
            }
            Spacer(Modifier.height(10.dp))
            GlassChip(t("Reset all settings", "Tüm ayarları sıfırla"), null, backdrop, onResetSettings)
        }
    }
}

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
private fun Section(title: String) {
    Text(
        title.uppercase(),
        style = LabelStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFBFD4FF)),
        modifier = Modifier.padding(top = 26.dp, bottom = 6.dp),
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
        Text(text, style = LabelStyle.copy(fontSize = 13.sp))
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
            Text(title, style = LabelStyle.copy(fontSize = 16.sp))
            if (subtitle != null) {
                Text(subtitle, style = LabelStyle.copy(fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f)))
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
            Text(title, style = LabelStyle.copy(fontSize = 16.sp), modifier = Modifier.weight(1f))
            Text(valueText, style = LabelStyle.copy(fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f)))
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

@Composable
private fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(4.dp)
    ) {
        options.forEach { (value, label) ->
            val isSel = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (isSel) Color.White.copy(alpha = 0.9f) else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
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
