package com.example.glasslauncher.ui

import android.graphics.Rect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.glasslauncher.data.DragSource
import com.example.glasslauncher.data.LauncherSettings
import com.example.glasslauncher.data.UiApp
import com.kyant.backdrop.Backdrop

/**
 * An open folder: glass card with an editable name and the apps inside.
 * Drag an app out of it to put it back on the home screen.
 */
@Composable
fun FolderOverlay(
    folder: FolderEntry?,
    hiddenForDrag: Boolean,
    settings: LauncherSettings,
    dots: Set<String>,
    backdrop: Backdrop,
    onLaunch: (UiApp, Rect?) -> Unit,
    onAppLongPress: (UiApp) -> Unit,
    onRename: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var last by remember { mutableStateOf<FolderEntry?>(null) }
    if (folder != null) last = folder
    val rename by rememberUpdatedState(onRename)

    GlassOverlay(folder != null, onDismiss, hidden = hiddenForDrag) {
        val f = last ?: return@GlassOverlay
        var name by remember(f.id) { mutableStateOf(f.name) }
        val focus = LocalFocusManager.current
        // Save the name when the folder closes
        DisposableEffect(f.id) {
            onDispose { if (name.isNotBlank() && name != f.name) rename(f.id, name) }
        }

        val cols = 4
        val rows = (f.apps.size + cols - 1) / cols
        val cell = settings.iconSize.dp + 36.dp

        GlassCard(backdrop, width = 340) {
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                textStyle = LabelStyle.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    focus.clearFocus()
                    if (name.isNotBlank()) rename(f.id, name)
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = cell * 4)
                    .verticalScroll(rememberScrollState())
            ) {
                SlotGrid(
                    items = f.apps.map { AppEntry(it) },
                    cols = cols,
                    rows = rows.coerceAtLeast(1),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(cell * rows.coerceAtLeast(1)),
                ) { entry ->
                    AppIcon(
                        entry.app, settings.iconSize.dp, true,
                        dot = settings.notificationDots && entry.app.app.packageName in dots,
                        onClick = { app, rect ->
                            onDismiss()
                            onLaunch(app, rect)
                        },
                        onLongClick = onAppLongPress,
                        labelSize = settings.labelSize,
                        dragSource = DragSource.Folder(f.id),
                    )
                }
            }
        }
    }
}
