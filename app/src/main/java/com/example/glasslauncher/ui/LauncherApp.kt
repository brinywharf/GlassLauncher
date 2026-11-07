package com.example.glasslauncher.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.glasslauncher.AppInfo
import com.example.glasslauncher.LauncherViewModel
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val LabelStyle = TextStyle(
    color = Color.White,
    shadow = Shadow(Color.Black.copy(alpha = 0.5f), blurRadius = 6f),
)

@Composable
fun LauncherApp(homePressed: Flow<Unit>, vm: LauncherViewModel = viewModel()) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val pinnedKeys by vm.pinned.collectAsStateWithLifecycle()
    val wallpaper by vm.wallpaper.collectAsStateWithLifecycle()

    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var focusSearch by remember { mutableStateOf(false) }
    var homeMenuOpen by remember { mutableStateOf(false) }
    var appMenuFor by remember { mutableStateOf<AppInfo?>(null) }

    // Home button while we're already showing: go back to the home page.
    LaunchedEffect(homePressed) {
        homePressed.collect {
            drawerOpen = false; homeMenuOpen = false; appMenuFor = null
        }
    }
    BackHandler(drawerOpen || homeMenuOpen || appMenuFor != null) {
        when {
            appMenuFor != null -> appMenuFor = null
            homeMenuOpen -> homeMenuOpen = false
            else -> drawerOpen = false
        }
    }

    val pickWallpaper = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.setWallpaper(uri)
    }

    // Everything drawn into this backdrop is what the glass refracts.
    val backdrop = rememberLayerBackdrop()
    val dockApps = remember(apps, pinnedKeys) {
        pinnedKeys.mapNotNull { key -> apps.firstOrNull { it.key == key } }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        LauncherBackground(wallpaper, Modifier.fillMaxSize().layerBackdrop(backdrop))

        HomeScreen(
            backdrop = backdrop,
            dockApps = dockApps,
            onLaunch = vm::launch,
            onOpenDrawer = { search -> focusSearch = search; drawerOpen = true },
            onLongPressHome = { homeMenuOpen = true },
            onLongPressApp = { appMenuFor = it },
        )

        AnimatedVisibility(
            visible = drawerOpen,
            enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = 400f)) { it / 3 } + fadeIn(),
            exit = slideOutVertically { it / 3 } + fadeOut(),
        ) {
            AppDrawer(
                backdrop = backdrop,
                apps = apps,
                focusSearch = focusSearch,
                onLaunch = { drawerOpen = false; vm.launch(it) },
                onLongPressApp = { appMenuFor = it },
                onClose = { drawerOpen = false },
            )
        }

        GlassMenu(
            visible = homeMenuOpen,
            backdrop = backdrop,
            title = "Home screen",
            onDismiss = { homeMenuOpen = false },
            items = buildList<Pair<String, () -> Unit>> {
                add("Change wallpaper" to {
                    pickWallpaper.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                })
                if (wallpaper != null) add("Use animated background" to { vm.clearWallpaper() })
            },
        )

        val target = appMenuFor
        GlassMenu(
            visible = target != null,
            backdrop = backdrop,
            title = target?.label.orEmpty(),
            onDismiss = { appMenuFor = null },
            items = if (target == null) emptyList() else listOf(
                (if (target.key in pinnedKeys) "Remove from dock" else "Add to dock") to { vm.togglePin(target) },
                "App info" to { vm.openAppInfo(target) },
                "Uninstall" to { vm.uninstall(target) },
            ),
        )
    }
}

// ---------------------------------------------------------------- Home

@Composable
private fun HomeScreen(
    backdrop: Backdrop,
    dockApps: List<AppInfo>,
    onLaunch: (AppInfo) -> Unit,
    onOpenDrawer: (focusSearch: Boolean) -> Unit,
    onLongPressHome: () -> Unit,
    onLongPressApp: (AppInfo) -> Unit,
) {
    var dragTotal by remember { mutableStateOf(0f) }
    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPressHome() }) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragTotal = 0f },
                    onVerticalDrag = { _, dy -> dragTotal += dy },
                    onDragEnd = { if (dragTotal < -120f) onOpenDrawer(false) },
                )
            }
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        ClockCard(backdrop)
        Spacer(Modifier.weight(1f))

        // Search pill
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .liquidGlass(backdrop, RoundedCornerShape(50), refractionHeight = 12.dp, refractionAmount = 24.dp)
                .clickable { onOpenDrawer(true) },
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                "  Search apps",
                style = LabelStyle.copy(fontSize = 16.sp, color = Color.White.copy(alpha = 0.8f)),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        Spacer(Modifier.height(16.dp))

        // Dock
        Row(
            Modifier
                .fillMaxWidth()
                .height(92.dp)
                .liquidGlass(backdrop, RoundedCornerShape(36.dp), refractionHeight = 20.dp, refractionAmount = 40.dp)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dockApps.isEmpty()) {
                Text(
                    "Long-press an app in the drawer to pin it here",
                    style = LabelStyle.copy(fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f)),
                )
            }
            dockApps.forEach { app ->
                AppIcon(app, showLabel = false, onClick = { onLaunch(app) }, onLongClick = { onLongPressApp(app) })
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("⌃ swipe up for all apps", style = LabelStyle.copy(fontSize = 11.sp, color = Color.White.copy(0.55f)))
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ClockCard(backdrop: Backdrop) {
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(1_000)
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .liquidGlass(backdrop, RoundedCornerShape(32.dp), refractionHeight = 24.dp, refractionAmount = 48.dp)
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            now.format(DateTimeFormatter.ofPattern("HH:mm")),
            style = LabelStyle.copy(fontSize = 72.sp, fontWeight = FontWeight.Light),
        )
        Text(
            now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
            style = LabelStyle.copy(fontSize = 16.sp, color = Color.White.copy(alpha = 0.85f)),
        )
    }
}

// ---------------------------------------------------------------- Drawer

@Composable
private fun AppDrawer(
    backdrop: Backdrop,
    apps: List<AppInfo>,
    focusSearch: Boolean,
    onLaunch: (AppInfo) -> Unit,
    onLongPressApp: (AppInfo) -> Unit,
    onClose: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(focusSearch) { if (focusSearch) focus.requestFocus() }

    val filtered = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }
    var dragTotal by remember { mutableStateOf(0f) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 12.dp)
            .liquidGlass(
                backdrop,
                RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
                tint = Color.Black.copy(alpha = 0.20f),
                blurRadius = 16.dp,
                refractionHeight = 24.dp,
                refractionAmount = 48.dp,
            )
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragTotal = 0f },
                    onVerticalDrag = { _, dy -> dragTotal += dy },
                    onDragEnd = { if (dragTotal > 160f) { keyboard?.hide(); onClose() } },
                )
            }
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // Grab handle
        Box(
            Modifier
                .padding(top = 10.dp)
                .align(Alignment.CenterHorizontally)
                .size(width = 40.dp, height = 5.dp)
                .background(Color.White.copy(alpha = 0.5f), CircleShape)
        )

        // Search field
        Box(
            Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .height(48.dp)
                .liquidGlass(backdrop, RoundedCornerShape(50), refractionHeight = 10.dp, refractionAmount = 20.dp)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (query.isEmpty()) {
                Text("Search", style = LabelStyle.copy(fontSize = 16.sp, color = Color.White.copy(0.6f)))
            }
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = LabelStyle.copy(fontSize = 16.sp),
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(filtered, key = { it.key }) { app ->
                Box(contentAlignment = Alignment.Center) {
                    AppIcon(
                        app,
                        showLabel = true,
                        onClick = { keyboard?.hide(); onLaunch(app) },
                        onLongClick = { onLongPressApp(app) },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Pieces

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppIcon(app: AppInfo, showLabel: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        Modifier
            .width(76.dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(app.icon, contentDescription = app.label, modifier = Modifier.size(56.dp))
        if (showLabel) {
            Spacer(Modifier.height(6.dp))
            Text(
                app.label,
                style = LabelStyle.copy(fontSize = 12.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** A small glass popup with a title and a list of actions. */
@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun GlassMenu(
    visible: Boolean,
    backdrop: Backdrop,
    title: String,
    items: List<Pair<String, () -> Unit>>,
    onDismiss: () -> Unit,
) {
    // Keep the last contents while the menu fades out.
    var shown by remember { mutableStateOf(title to items) }
    if (visible) shown = title to items
    val (shownTitle, shownItems) = shown
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.25f))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
            contentAlignment = Alignment.Center,
        ) {
                Column(
                    Modifier
                        .animateEnterExit(
                            enter = scaleIn(spring(dampingRatio = 0.6f, stiffness = 500f), initialScale = 0.85f),
                            exit = scaleOut(targetScale = 0.9f),
                        )
                        .width(280.dp)
                        .liquidGlass(
                            backdrop,
                            RoundedCornerShape(28.dp),
                            tint = Color.White.copy(alpha = 0.14f),
                            blurRadius = 12.dp,
                            refractionHeight = 20.dp,
                            refractionAmount = 40.dp,
                        )
                        .padding(vertical = 12.dp),
                ) {
                    Text(
                        shownTitle,
                        style = LabelStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    shownItems.forEach { (label, action) ->
                        Text(
                            label,
                            style = LabelStyle.copy(fontSize = 16.sp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onDismiss(); action() }
                                .padding(horizontal = 22.dp, vertical = 14.dp),
                        )
                    }
                }
        }
    }
}
