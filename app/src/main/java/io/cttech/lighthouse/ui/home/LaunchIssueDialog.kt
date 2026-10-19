package io.cttech.lighthouse.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.cttech.lighthouse.launch.LaunchResult
import io.cttech.lighthouse.system.Root
import io.cttech.lighthouse.ui.components.PillButton
import io.cttech.lighthouse.ui.components.FocusOnAppear

/** Explains why a game didn't start and offers the fix. B / Back closes it. */
@Composable
fun LaunchIssueDialog(
    issue: LaunchResult,
    onDismiss: () -> Unit,
    onGrantWithRoot: (String) -> Unit,
    onOpenAppInfo: (String) -> Unit,
    onOpenApp: (String) -> Unit,
) {
    val first = remember { FocusRequester() }
    val rootAvailable by produceState(false) { value = Root.available() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(indication = null, interactionSource = null, onClick = onDismiss) // tap outside closes
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && (e.key == Key.ButtonB || e.key == Key.Back || e.key == Key.Escape)) { onDismiss(); true } else false
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(600.dp)
                .background(Color(0xFF151A26), RoundedCornerShape(18.dp))
                .clickable(indication = null, interactionSource = null) {}
                .padding(30.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val (title, body) = when (issue) {
                is LaunchResult.EmulatorMissing -> "No emulator installed" to
                    "Install ${issue.names.joinToString(" or ")} to play this."
                is LaunchResult.NeedsStoragePermission -> "${issue.profile.label.substringBefore(" ·")} can't read your games" to
                    "It needs storage access (\"Files and media\") to open ROMs from your SD card and internal storage."
                is LaunchResult.CoreMissing -> "Core not installed" to
                    "${issue.profile.label} needs the ${issue.corePath.substringAfterLast('/').removeSuffix("_libretro_android.so")} core. " +
                    "Open RetroArch → Main Menu → Online Updater → Core Downloader and install it."
                is LaunchResult.Failed -> "Couldn't start the game" to issue.message
                LaunchResult.Started -> "" to ""
            }
            Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(body, color = Color(0xFFCDD6E8), fontSize = 16.sp, lineHeight = 22.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                when (issue) {
                    is LaunchResult.NeedsStoragePermission -> {
                        if (rootAvailable) PillButton("Allow with root", Modifier.focusRequester(first)) { onGrantWithRoot(issue.pkg) }
                        PillButton("Open app settings", if (rootAvailable) Modifier else Modifier.focusRequester(first)) { onOpenAppInfo(issue.pkg); onDismiss() }
                    }
                    is LaunchResult.CoreMissing -> PillButton("Open RetroArch", Modifier.focusRequester(first)) {
                        onOpenApp(issue.corePath.removePrefix("/data/user/0/").substringBefore('/')); onDismiss()
                    }
                    else -> Unit
                }
                PillButton(if (issue is LaunchResult.NeedsStoragePermission || issue is LaunchResult.CoreMissing) "Cancel" else "OK",
                    if (issue is LaunchResult.EmulatorMissing || issue is LaunchResult.Failed) Modifier.focusRequester(first) else Modifier,
                    onClick = onDismiss)
            }
        }
    }
    FocusOnAppear(first, issue, rootAvailable)
}
