package io.cttech.lighthouse.ui.home

import androidx.compose.foundation.layout.widthIn
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import io.cttech.lighthouse.ui.components.FocusOnAppear
import io.cttech.lighthouse.ui.components.rememberAppIcon
import io.cttech.lighthouse.ui.components.trapFocus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One launchable activity (an app can have more than one, e.g. RetroArch). */
data class DrawerApp(val label: String, val packageName: String, val activity: String)

/**
 * Every launchable app on the device, including ones Lighthouse keeps off its shelves (system apps,
 * hidden items). Opening from here doesn't touch the layout or "Continue playing". B closes.
 */
@Composable
fun AppDrawer(onClose: () -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val apps by produceState(emptyList<DrawerApp>()) { value = withContext(Dispatchers.IO) { loadApps(context) } }
    val shown = remember(apps, query) {
        val q = TitleCleaner.sortKey(query)
        if (q.isBlank()) apps else apps.filter { TitleCleaner.sortKey(it.label).contains(q) }
    }
    val first = remember { FocusRequester() }

    Box(
        Modifier
            .fillMaxSize()
            .trapFocus()
            .background(Lh.colors.background.copy(alpha = 0.96f))
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && (e.key == Key.ButtonB || e.key == Key.Escape)) { onClose(); true } else false
            },
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    AppTile(app, if (i == 0) Modifier.focusRequester(first) else Modifier) {
                        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                            .setComponent(ComponentName(app.packageName, app.activity))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                        runCatching { context.startActivity(intent) }
                        onClose()
                    }
                }
            }
        }
    }
    if (apps.isNotEmpty()) FocusOnAppear(first, apps.size)
}

@Composable
private fun AppTile(app: DrawerApp, modifier: Modifier, onOpen: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val icon = rememberAppIcon(app.packageName)
    Column(
        modifier
            .graphicsLayer { val s = if (focused) 1.06f else 1f; scaleX = s; scaleY = s }
            .background(if (focused) Color.White.copy(alpha = 0.14f) else Color.Transparent, RoundedCornerShape(14.dp))
            .border(if (focused) 2.dp else 0.dp, if (focused) Lh.colors.focus else Color.Transparent, RoundedCornerShape(14.dp))
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.key == Key.ButtonA) { onOpen(); true } else false }
            .clickable(onClick = onOpen)
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
