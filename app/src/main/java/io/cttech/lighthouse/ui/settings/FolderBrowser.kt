package io.cttech.lighthouse.ui.settings

import io.cttech.lighthouse.ui.components.popupWidth
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.cttech.lighthouse.theme.Lh
import io.cttech.lighthouse.ui.components.FocusOnAppear
import io.cttech.lighthouse.ui.components.PillButton
import io.cttech.lighthouse.ui.components.trapFocus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Pick a ROM folder with the controller: starts at the storage volumes (Internal, SD…), A opens a folder,
 * "Use this folder" picks the one you're in. B goes up a level, and closes at the top.
 * Reads the file system directly, so it needs "All files access" (sideload flavour).
 */
@Composable
internal fun FolderBrowser(roots: List<File>, onPick: (File) -> Unit, onClose: () -> Unit) {
    var dir by remember { mutableStateOf<File?>(null) } // null = list of volumes
    val first = remember { FocusRequester() }
    val useButton = remember { FocusRequester() }
    val entries by produceState(emptyList<File>(), dir) {
        value = withContext(Dispatchers.IO) {
            dir?.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.sortedBy { it.name.lowercase() } ?: roots
        }
    }
    val atRoot = dir?.let { d -> roots.any { it.absolutePath == d.absolutePath } } == true
    fun up() {
        val d = dir ?: return onClose()
        dir = if (roots.any { it.absolutePath == d.absolutePath }) null else d.parentFile
    }
    BackHandler { up() }

    Box(
        Modifier
            .fillMaxSize()
            .trapFocus()
            .background(Lh.colors.background.copy(alpha = 0.97f))
            .onPreviewKeyEvent { e -> if (e.type == KeyEventType.KeyDown && (e.key == Key.ButtonB || e.key == Key.Escape)) { up(); true } else false },
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.popupWidth(760.dp).padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Choose a ROM folder", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            Text(dir?.absolutePath ?: "Storage", color = Lh.colors.textMuted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.StartEllipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                dir?.takeUnless { atRoot }?.let { d -> PillButton("Use this folder", Modifier.focusRequester(useButton)) { onPick(d) } }
                if (dir != null) PillButton("Up", onClick = ::up)
                PillButton("Cancel", onClick = onClose)
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(entries, key = { _, f -> f.absolutePath }) { i, f ->
                    SettingRow(
                        label = if (dir == null) volumeName(f) else f.name,
                        value = "›",
                        modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                    ) { dir = f }
                }
            }
            if (atRoot) Note("This is a whole storage volume. Open a folder inside it, such as Games, then press \"Use this folder\".")
            if (entries.isEmpty() && dir != null && !atRoot) Note("No folders in here. Press \"Use this folder\" to pick it.")
        }
    }
    // An empty folder has no rows: focus "Use this folder" so the controller never ends up nowhere.
    FocusOnAppear(if (entries.isEmpty() && dir != null && !atRoot) useButton else first, dir, entries.size)
}

private fun volumeName(f: File) = when {
    f.absolutePath == "/storage/emulated/0" -> "Internal storage"
    else -> "SD card (${f.name})"
}
