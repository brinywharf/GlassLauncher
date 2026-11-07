package com.example.glasslauncher.ui

import android.appwidget.AppWidgetProviderInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.glasslauncher.WidgetController
import com.example.glasslauncher.WidgetProviderItem
import com.example.glasslauncher.data.AppInfo
import com.example.glasslauncher.data.IconColorMode
import com.example.glasslauncher.data.LauncherSettings
import com.example.glasslauncher.data.ShortcutItem
import com.example.glasslauncher.data.UiApp
import com.example.glasslauncher.data.UserData
import com.kyant.backdrop.Backdrop

data class MenuAction(val icon: ImageVector?, val label: String, val action: () -> Unit)

/** Dim scrim + a springy glass card in the middle. Tap outside to dismiss. */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun GlassOverlay(visible: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn(tween(140)), exit = fadeOut(tween(120))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } }
                .imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .animateEnterExit(
                        enter = scaleIn(fluidSpring(LocalUiPrefs.current, 0.72f, 1100f), initialScale = 0.86f),
                        exit = scaleOut(tween(120), targetScale = 0.94f),
                    )
                    .pointerInput(Unit) { detectTapGestures { } }
            ) {
                content()
            }
        }
    }
}

@Composable
fun GlassCard(backdrop: Backdrop, width: Int = 300, content: @Composable () -> Unit) {
    Column(
        Modifier
            .width(width.dp)
            .liquidGlass(
                backdrop, RoundedCornerShape(30.dp),
                tint = Color.Black.copy(alpha = 0.18f),
                blurRadius = 14.dp, refractionHeight = 22.dp, refractionAmount = 48.dp,
            )
            .padding(vertical = 14.dp),
    ) { content() }
}

@Composable
fun MenuRow(icon: ImageVector?, label: String, onClick: () -> Unit, iconBitmap: ImageBitmap? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            iconBitmap != null -> Image(iconBitmap, null, Modifier.size(22.dp))
            icon != null -> Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        if (icon != null || iconBitmap != null) Spacer(Modifier.width(16.dp))
        Text(label, style = LabelStyle.copy(fontSize = 16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MenuDivider() {
    Box(
        Modifier
            .padding(horizontal = 18.dp, vertical = 6.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.18f))
    )
}

/** Simple list menu (used for the home screen long-press). */
@Composable
fun GlassMenu(visible: Boolean, backdrop: Backdrop, title: String, items: List<MenuAction>, onDismiss: () -> Unit) {
    var shown by remember { mutableStateOf(title to items) }
    if (visible) shown = title to items
    GlassOverlay(visible, onDismiss) {
        GlassCard(backdrop) {
            Text(
                shown.first,
                style = LabelStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
            )
            shown.second.forEach { item ->
                MenuRow(item.icon, item.label, onClick = { onDismiss(); item.action() })
            }
        }
    }
}

/** Long-press menu for an app: shortcuts + actions. */
@Composable
fun AppMenu(
    app: UiApp?,
    data: UserData,
    backdrop: Backdrop,
    loadShortcuts: suspend (AppInfo) -> List<ShortcutItem>,
    onShortcut: (ShortcutItem) -> Unit,
    onToggleHome: (AppInfo) -> Unit,
    onMoveHome: (AppInfo, Int) -> Unit,
    onToggleDock: (AppInfo) -> Unit,
    onEdit: (UiApp) -> Unit,
    onHide: (AppInfo) -> Unit,
    onInfo: (AppInfo) -> Unit,
    onUninstall: (AppInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    var last by remember { mutableStateOf<UiApp?>(null) }
    if (app != null) last = app
    val shortcuts by produceState(emptyList<ShortcutItem>(), app?.key) {
        value = emptyList()
        if (app != null) value = loadShortcuts(app.app)
    }

    GlassOverlay(app != null, onDismiss) {
        val ui = last ?: return@GlassOverlay
        val a = ui.app
        val onHome = a.key in data.home
        val inDock = a.key in data.dock
        fun act(block: () -> Unit): () -> Unit = { onDismiss(); block() }

        GlassCard(backdrop) {
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(ui.icon, null, Modifier.size(46.dp))
                Spacer(Modifier.width(14.dp))
                Text(
                    ui.label,
                    style = LabelStyle.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (shortcuts.isNotEmpty()) {
                MenuDivider()
                shortcuts.forEach { sc ->
                    MenuRow(null, sc.label, onClick = act { onShortcut(sc) }, iconBitmap = sc.icon)
                }
            }
            MenuDivider()
            MenuRow(
                Icons.Default.Home,
                if (onHome) t("Remove from home screen", "Ana ekrandan kaldır") else t("Add to home screen", "Ana ekrana ekle"),
                act { onToggleHome(a) },
            )
            if (onHome) {
                Row(Modifier.padding(horizontal = 12.dp)) {
                    Box(Modifier.weight(1f)) {
                        MenuRow(Icons.Default.KeyboardArrowLeft, t("Move back", "Geri taşı"), { onMoveHome(a, -1) })
                    }
                    Box(Modifier.weight(1f)) {
                        MenuRow(Icons.Default.KeyboardArrowRight, t("Move forward", "İleri taşı"), { onMoveHome(a, 1) })
                    }
                }
            }
            MenuRow(
                Icons.Default.Star,
                if (inDock) t("Remove from dock", "Dock'tan kaldır") else t("Add to dock", "Dock'a ekle"),
                act { onToggleDock(a) },
            )
            MenuRow(Icons.Default.Edit, t("Edit name & icon color", "İsmi ve ikon rengini düzenle"), act { onEdit(ui) })
            MenuRow(Icons.Default.Close, t("Hide app", "Uygulamayı gizle"), act { onHide(a) })
            MenuRow(Icons.Default.Info, t("App info", "Uygulama bilgisi"), act { onInfo(a) })
            MenuRow(Icons.Default.Delete, t("Uninstall", "Kaldır"), act { onUninstall(a) })
        }
    }
}

// ---------------------------------------------------------------- Edit dialog & color picker

val Swatches: List<Int> = listOf(
    0xFFFF5252, 0xFFFF9100, 0xFFFFD600, 0xFF76FF03, 0xFF00E676, 0xFF1DE9B6,
    0xFF00B0FF, 0xFF2979FF, 0xFF651FFF, 0xFFD500F9, 0xFFFF4081, 0xFFFFFFFF,
    0xFF9E9E9E, 0xFF263238,
).map { it.toInt() }

private fun hueOf(color: Int): Float {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color, hsv)
    return hsv[0]
}

/** Swatches + a hue slider. selected == null means "Auto". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(selected: Int?, autoLabel: String?, onSelect: (Int?) -> Unit) {
    Column {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (autoLabel != null) {
                Box(
                    Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = if (selected == null) 0.9f else 0.15f))
                        .clickable { onSelect(null) }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        autoLabel,
                        color = if (selected == null) Color.Black else Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Swatches.forEach { c ->
                Box(
                    Modifier
                        .size(34.dp)
                        .background(Color(c), CircleShape)
                        .border(
                            if (selected == c) 3.dp else 1.dp,
                            Color.White.copy(alpha = if (selected == c) 1f else 0.4f),
                            CircleShape,
                        )
                        .clickable { onSelect(c) }
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        var hue by remember { mutableFloatStateOf(selected?.let { hueOf(it) } ?: 220f) }
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f, 0.75f, 1f) }))
            )
            Slider(
                value = hue,
                onValueChange = {
                    hue = it
                    onSelect(Color.hsv(it.coerceIn(0f, 360f), 0.75f, 1f).toArgb())
                },
                valueRange = 0f..360f,
                colors = SliderDefaults.colors(
                    thumbColor = Color.hsv(hue.coerceIn(0f, 360f), 0.75f, 1f),
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                ),
            )
        }
    }
}

@Composable
fun EditAppDialog(
    app: UiApp?,
    data: UserData,
    settings: LauncherSettings,
    backdrop: Backdrop,
    preview: suspend (AppInfo, Int?) -> ImageBitmap,
    onSave: (AppInfo, String?, Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var last by remember { mutableStateOf<UiApp?>(null) }
    if (app != null) last = app

    GlassOverlay(app != null, onDismiss) {
        val ui = last ?: return@GlassOverlay
        val a = ui.app
        var name by remember(a.key) { mutableStateOf(data.renames[a.key] ?: a.label) }
        var color by remember(a.key) { mutableStateOf(data.colors[a.key]) }
        val tint: Int? = color ?: when (settings.iconColorMode) {
            IconColorMode.AUTO -> a.autoColor
            IconColorMode.SINGLE -> settings.iconColor
            IconColorMode.CLEAR -> null
        }
        val previewIcon by produceState<ImageBitmap?>(ui.icon, a.key, tint) { value = preview(a, tint) }

        GlassCard(backdrop, width = 330) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(t("Edit app", "Uygulamayı düzenle"), style = LabelStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold))
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    previewIcon?.let { Image(it, null, Modifier.size(84.dp)) }
                }
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.14f))
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        textStyle = LabelStyle.copy(fontSize = 16.sp),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(t("Icon color", "İkon rengi"), style = LabelStyle.copy(fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f)))
                Spacer(Modifier.height(10.dp))
                ColorPicker(color, t("Default", "Varsayılan")) { color = it }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip(t("Reset", "Sıfırla"), null, backdrop) {
                        name = a.label
                        color = null
                    }
                    Spacer(Modifier.weight(1f))
                    GlassChip(t("Save", "Kaydet"), null, backdrop) {
                        onSave(a, name.trim(), color)
                        onDismiss()
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Widget picker

@Composable
fun WidgetPicker(
    visible: Boolean,
    controller: WidgetController,
    backdrop: Backdrop,
    onPick: (AppWidgetProviderInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val providers by produceState<List<WidgetProviderItem>?>(null, visible) {
        if (visible && value == null) value = controller.loadProviders()
    }
    GlassOverlay(visible, onDismiss) {
        GlassCard(backdrop, width = 340) {
            Text(
                t("Add a widget", "Widget ekle"),
                style = LabelStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
            )
            val list = providers
            if (list == null) {
                Text(
                    t("Loading…", "Yükleniyor…"),
                    style = LabelStyle.copy(fontSize = 15.sp),
                    modifier = Modifier.padding(22.dp),
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 520.dp)) {
                    items(list) { p ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDismiss()
                                    onPick(p.info)
                                }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val icon = p.icon
                            if (icon != null) Image(icon, null, Modifier.size(40.dp))
                            else Box(Modifier.size(40.dp))
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(p.label, style = LabelStyle.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${p.appLabel} · ${p.cols}×${p.rows}",
                                    style = LabelStyle.copy(fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f)),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
