package io.cttech.lighthouse.ui.home

import androidx.compose.foundation.layout.widthIn
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.cttech.lighthouse.library.TitleCleaner
import io.cttech.lighthouse.theme.Lh
import io.cttech.lighthouse.ui.components.ControllerTextField
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.cttech.lighthouse.ui.components.FocusOnAppear
import io.cttech.lighthouse.ui.components.PillButton
import io.cttech.lighthouse.ui.components.popupWidth
import io.cttech.lighthouse.ui.components.rememberAppIcon
import io.cttech.lighthouse.ui.components.trapFocus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.cttech.lighthouse.ui.components.popupHints
import io.cttech.lighthouse.ui.components.ProvideHints
import io.cttech.lighthouse.ui.components.HintBarHeight

/** One launchable activity (an app can have more than one, e.g. RetroArch). */
data class DrawerApp(val label: String, val packageName: String, val activity: String)

/**
 * Every launchable app on the device, including ones Lighthouse keeps off its shelves (system apps,
 * hidden items). Opening from here doesn't touch the layout or "Continue playing". B closes.
 * Select (or long-press) on an app opens its menu: App info, Uninstall, Hide / Unhide.
 *
 * [hiddenState] says whether Lighthouse knows the app: null = not in its library (system apps, so nothing
 * to hide), otherwise whether it is currently hidden.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppDrawer(hiddenState: (String) -> Boolean?, onSetHidden: (String, Boolean) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    // Reloaded when we come back (e.g. from the uninstall dialog), so a removed app leaves the grid.
    var reload by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { reload++; onPauseOrDispose {} }
    val apps by produceState(emptyList<DrawerApp>(), reload) { value = withContext(Dispatchers.IO) { loadApps(context) } }
    var menuFor by remember { mutableStateOf<DrawerApp?>(null) }
    var lastKey by remember { mutableStateOf<String?>(null) }
    val returnFocus = remember { FocusRequester() }
    LaunchedEffect(menuFor) {
        if (menuFor == null && lastKey != null) repeat(30) {
            androidx.compose.runtime.withFrameNanos { }
            if (runCatching { returnFocus.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
        }
    }
    val shown = remember(apps, query) {
        val q = TitleCleaner.sortKey(query)
        if (q.isBlank()) apps else apps.filter { TitleCleaner.sortKey(it.label).contains(q) }
    }
    val first = remember { FocusRequester() }
    ProvideHints(listOf("B" to "Close", "⧉" to "Options", "A" to "Open")) { if (it == "B") onClose() }

    Box(
        Modifier
            .fillMaxSize()
            .trapFocus()
            .background(Lh.colors.background.copy(alpha = 0.96f))
            .onPreviewKeyEvent { e ->
                if (menuFor == null && e.type == KeyEventType.KeyDown && (e.key == Key.ButtonB || e.key == Key.Escape)) { onClose(); true } else false
            },
    ) {
        Column(Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, top = 18.dp, bottom = HintBarHeight), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("All apps", color = Lh.colors.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                ControllerTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search apps",
                    modifier = Modifier.widthIn(max = 420.dp).weight(1f, fill = false),
                )
                Text("${shown.size} apps", color = Lh.colors.textMuted, fontSize = 14.sp)
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(8),
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                itemsIndexed(shown, key = { _, a -> "${a.packageName}/${a.activity}" }) { i, app ->
                    val tileKey = "${app.packageName}/${app.activity}"
                    val mod = when {
                        tileKey == lastKey -> Modifier.focusRequester(returnFocus)
                        i == 0 -> Modifier.focusRequester(first)
                        else -> Modifier
                    }
                    AppTile(app, mod, onMenu = { lastKey = tileKey; menuFor = app }) { openApp(context, app); onClose() }
                }
            }
        }
        // Inside the drawer's focus group: the drawer cancels focus moves that leave it, which would refuse the menu.
        menuFor?.let { app ->
            AppMenu(
                app = app,
                hidden = hiddenState(app.packageName),
                onOpen = { openApp(context, app); onClose() },
                onSetHidden = { onSetHidden(app.packageName, it); menuFor = null },
                onClose = { menuFor = null },
            )
        }
    }
    if (apps.isNotEmpty() && lastKey == null) FocusOnAppear(first, apps.size)
}

@Composable
private fun AppMenu(app: DrawerApp, hidden: Boolean?, onOpen: () -> Unit, onSetHidden: (Boolean) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val first = remember { FocusRequester() }
    var opened by remember { mutableStateOf(false) }
    val canUninstall = remember(app.packageName) { isUninstallable(context, app.packageName) }
    ProvideHints(popupHints()) { if (it == "B") onClose() }
    Box(
        Modifier
            .fillMaxSize()
            .trapFocus()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(indication = null, interactionSource = null, onClick = onClose)
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key in setOf(Key.ButtonB, Key.Escape, Key.ButtonSelect)) { onClose(); true } else false
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .popupWidth(420.dp)
                .padding(vertical = 12.dp)
                .background(Lh.colors.surface, RoundedCornerShape(18.dp))
                .clickable(indication = null, interactionSource = null) {}
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(app.label, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            PillButton("Open", Modifier.focusRequester(first).onFocusChanged { if (it.isFocused) opened = true }, onClick = onOpen)
            PillButton("App info") {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
                onClose()
            }
            if (canUninstall) PillButton("Uninstall") {
                val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
                onClose()
            }
            if (hidden != null) PillButton(if (hidden) "Unhide in Lighthouse" else "Hide from Lighthouse") { onSetHidden(!hidden) }
        }
    }
    // The grid tile that opened the menu still holds focus on the first frames, and requestFocus() then returns
    // false without throwing: keep asking until the menu really has it, or D-pad presses land on the grid behind.
    LaunchedEffect(app.packageName) {
        repeat(60) {
            if (opened) return@LaunchedEffect
            androidx.compose.runtime.withFrameNanos { }
            runCatching { first.requestFocus() }
        }
    }
}

private fun openApp(context: Context, app: DrawerApp) {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        .setComponent(ComponentName(app.packageName, app.activity))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    runCatching { context.startActivity(intent) }
}

/** Android refuses to uninstall system apps (an updated one can be rolled back, which counts). */
private fun isUninstallable(context: Context, pkg: String): Boolean {
    val info = runCatching { context.packageManager.getApplicationInfo(pkg, 0) }.getOrNull() ?: return false
    val system = info.flags and ApplicationInfo.FLAG_SYSTEM != 0
    val updated = info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
    return !system || updated
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(app: DrawerApp, modifier: Modifier, onMenu: () -> Unit, onOpen: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val icon = rememberAppIcon(app.packageName)
    Column(
        modifier
            .graphicsLayer { val s = if (focused) 1.06f else 1f; scaleX = s; scaleY = s }
            .background(if (focused) Color.White.copy(alpha = 0.14f) else Color.Transparent, RoundedCornerShape(14.dp))
            .border(if (focused) 2.dp else 0.dp, if (focused) Lh.colors.focus else Color.Transparent, RoundedCornerShape(14.dp))
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.ButtonA -> { onOpen(); true }
                    Key.ButtonSelect -> { onMenu(); true }
                    else -> false
                }
            }
            .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onLongClick = onMenu, onClick = onOpen)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(56.dp)) }
        }
        Text(app.label, color = Lh.colors.text, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

/** All launcher activities except Lighthouse's own, sorted by label. */
private fun loadApps(context: Context): List<DrawerApp> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val infos = if (Build.VERSION.SDK_INT >= 33) {
        pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION") pm.queryIntentActivities(intent, 0)
    }
    return infos
        .filter { !it.activityInfo.packageName.startsWith("io.cttech.lighthouse") }
        .map { DrawerApp(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.activityInfo.name) }
        .sortedBy { it.label.lowercase() }
}
