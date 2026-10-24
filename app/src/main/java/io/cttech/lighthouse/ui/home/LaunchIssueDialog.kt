package io.cttech.lighthouse.ui.home

import io.cttech.lighthouse.ui.components.popupWidth
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import io.cttech.lighthouse.ui.components.trapFocus
import io.cttech.lighthouse.theme.Lh
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.cttech.lighthouse.launch.LaunchResult
import io.cttech.lighthouse.system.CoreInstaller
import io.cttech.lighthouse.system.Root
import io.cttech.lighthouse.ui.components.PillButton
import io.cttech.lighthouse.ui.components.FocusOnAppear
import io.cttech.lighthouse.ui.components.popupHints
import io.cttech.lighthouse.ui.components.ProvideHints

/** Explains why a game didn't start and offers the fix. B / Back closes it. */
@Composable
fun LaunchIssueDialog(
    issue: LaunchResult,
    onDismiss: () -> Unit,
    onGrantWithRoot: (String) -> Unit,
    onOpenAppInfo: (String) -> Unit,
    onOpenApp: (String) -> Unit,
    onPlayAnyway: () -> Unit = {},
) {
    val first = remember { FocusRequester() }
    val rootAvailable by produceState(false) { value = Root.available() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var coreInstall by remember { mutableStateOf(CoreInstall.Idle) }
    ProvideHints(popupHints()) { if (it == "B") onDismiss() }
    Box(
        Modifier
            .fillMaxSize()
            .trapFocus() // D-pad can't wander behind the pop-up
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(indication = null, interactionSource = null, onClick = onDismiss) // tap outside closes
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && (e.key == Key.ButtonB || e.key == Key.Back || e.key == Key.Escape)) { onDismiss(); true } else false
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .popupWidth(600.dp)
                .background(Lh.colors.surface, RoundedCornerShape(18.dp))
                .clickable(indication = null, interactionSource = null) {}
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val (title, body) = when (issue) {
                is LaunchResult.EmulatorMissing -> "No emulator installed" to emulatorMissingBody(issue)
                is LaunchResult.NeedsStoragePermission -> "${issue.profile.label.substringBefore(" ·")} can't read your games" to
                    "It needs storage access (\"Files and media\") to open ROMs from your SD card and internal storage."
                is LaunchResult.CoreMissing -> when (coreInstall) {
                    CoreInstall.Done -> "Core installed" to "Press A on the game again to play it."
                    else -> "Core not installed" to coreMissingBody(issue, rootAvailable, coreInstall)
                }
                is LaunchResult.CoreUnchecked -> "Check you have the core" to coreHintBody(issue)
                is LaunchResult.Failed -> "Couldn't start the game" to issue.message
                LaunchResult.Started -> "" to ""
            }
            Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            Text(body, color = Lh.colors.text.copy(alpha = 0.85f), fontSize = 16.sp, lineHeight = 22.sp)
            // Somewhere to get it, not just its name. RetroArch profiles share one site, so one button each.
            val gets = (issue as? LaunchResult.EmulatorMissing)?.apps.orEmpty().filter { it.website != null }.distinctBy { it.website }.take(3)
            if (gets.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                gets.forEachIndexed { i, app ->
                    PillButton("Get ${app.name.substringBefore(" (")} (browser)", if (i == 0) Modifier.focusRequester(first) else Modifier) {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(app.website)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        onDismiss()
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                when (issue) {
                    is LaunchResult.NeedsStoragePermission -> {
                        if (rootAvailable) PillButton("Allow with root", Modifier.focusRequester(first)) { onGrantWithRoot(issue.pkg) }
                        PillButton("Open app settings", if (rootAvailable) Modifier else Modifier.focusRequester(first)) { onOpenAppInfo(issue.pkg); onDismiss() }
                    }
                    is LaunchResult.CoreMissing -> if (coreInstall != CoreInstall.Done) {
                        val canDownload = rootAvailable && CoreInstaller.parse(issue.corePath) != null
                        if (canDownload && coreInstall != CoreInstall.Working) {
                            PillButton("Download core", Modifier.focusRequester(first)) {
                                coreInstall = CoreInstall.Working
                                scope.launch { coreInstall = if (CoreInstaller.install(context, issue.corePath)) CoreInstall.Done else CoreInstall.Failed }
                            }
                        }
                        if (coreInstall != CoreInstall.Working) {
                            PillButton("Open RetroArch", if (canDownload) Modifier else Modifier.focusRequester(first)) {
                                onOpenApp(issue.corePath.removePrefix("/data/user/0/").substringBefore('/')); onDismiss()
                            }
                        }
                    }
                    is LaunchResult.CoreUnchecked -> {
                        PillButton("Play anyway", Modifier.focusRequester(first), onClick = onPlayAnyway)
                        PillButton("Open RetroArch") {
                            onOpenApp(issue.corePath.removePrefix("/data/user/0/").substringBefore('/')); onDismiss()
                        }
                    }
                    else -> Unit
                }
                val finished = issue is LaunchResult.CoreMissing && coreInstall == CoreInstall.Done
                PillButton(
                    when {
                        (issue is LaunchResult.NeedsStoragePermission || issue is LaunchResult.CoreMissing || issue is LaunchResult.CoreUnchecked) && !finished -> "Cancel"
                        issue is LaunchResult.EmulatorMissing -> "Close"
                        else -> "OK"
                    },
                    if ((issue is LaunchResult.EmulatorMissing && gets.isEmpty()) || issue is LaunchResult.Failed || finished) Modifier.focusRequester(first) else Modifier,
                    onClick = onDismiss)
            }
        }
    }
    FocusOnAppear(first, issue, rootAvailable, coreInstall)
}

private enum class CoreInstall { Idle, Working, Done, Failed }

/** "Install RetroArch (mGBA core) to play this", or the choice when several emulators would do. */
private fun emulatorMissingBody(issue: LaunchResult.EmulatorMissing): String {
    val names = issue.apps.map { it.name }.distinct()
    return when (names.size) {
        0 -> "There's no emulator Lighthouse knows for this platform yet."
        1 -> "Install ${names[0]} to play this, then press A on the game again."
        else -> "Install one of these to play this: ${names.dropLast(1).joinToString(", ")} or ${names.last()}. Then press A on the game again."
    }
}

private fun coreName(path: String) = path.substringAfterLast('/').removeSuffix("_libretro_android.so")

/** What's missing, what else would do, and the way to get it (a one-tap download only where root allows it). */
private fun coreMissingBody(issue: LaunchResult.CoreMissing, rootAvailable: Boolean, state: CoreInstall): String {
    val name = coreName(issue.corePath)
    val others = issue.alternatives.map(::coreName).filter { it != name }
    val needs = "${issue.profile.label} needs the $name core" +
        if (others.isEmpty()) "." else " (or one of: ${others.joinToString(", ")})."
    return when (state) {
        CoreInstall.Working -> "Downloading $name from libretro's build server\u2026"
        CoreInstall.Failed -> "$needs Couldn't download or install it. Check your connection, or install it in RetroArch: " +
            "Main Menu → Online Updater → Core Downloader."
        else -> if (rootAvailable && CoreInstaller.parse(issue.corePath) != null) {
            "$needs Download core fetches $name from buildbot.libretro.com and installs it into RetroArch (uses root). " +
                "Or install it yourself in RetroArch: Main Menu → Online Updater → Core Downloader."
        } else {
            "$needs Open RetroArch → Main Menu → Online Updater → Core Downloader and install it."
        }
    }
}

/** Shown once per core where Lighthouse can't look inside RetroArch (no root): RetroArch itself would only show a vague error. */
private fun coreHintBody(issue: LaunchResult.CoreUnchecked): String {
    val name = coreName(issue.corePath)
    val others = issue.alternatives.map(::coreName).filter { it != name }
    val needs = "${issue.profile.label} needs the $name core" + if (others.isEmpty()) "" else " (or one of: ${others.joinToString(", ")})"
    return "$needs. Lighthouse can't see inside RetroArch without root, so it can't check. If the game doesn't start, install the core in " +
        "RetroArch: Main Menu → Online Updater → Core Downloader. You'll only see this once for this core."
}
