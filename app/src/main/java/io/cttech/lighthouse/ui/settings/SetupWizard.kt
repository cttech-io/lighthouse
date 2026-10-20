package io.cttech.lighthouse.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.cttech.lighthouse.theme.Lh
import io.cttech.lighthouse.ui.components.FocusOnAppear
import io.cttech.lighthouse.ui.components.HintBar
import io.cttech.lighthouse.ui.components.PillButton
import io.cttech.lighthouse.ui.components.refocusWhenLost
import io.cttech.lighthouse.ui.components.trapFocus
import io.cttech.lighthouse.ui.home.HomeViewModel

private val Steps = listOf("Welcome", "Your games", "Emulators", "Covers", "Look & buttons", "Home app")

/**
 * First-run setup (also Settings → System → Run setup again). Auto-detects, then asks you to confirm:
 * storage access and ROM folders, emulators, covers, theme and button layout, and the Home app.
 * Every step uses the same controls as Settings, so nothing here is a one-off.
 */
@Composable
fun SetupWizard(vm: HomeViewModel, onFinish: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val overlay = remember { OverlayHost() }
    val info by vm.settingsInfo.collectAsStateWithLifecycle()
    val library by vm.libraryInfo.collectAsStateWithLifecycle()
    val themes by vm.themes.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) { vm.refreshSettingsInfo(); onPauseOrDispose { } }
    val currentFirst = remember { arrayOf(FocusRequester()) }
    val last = Steps.lastIndex
    fun back() { if (step > 0) step-- }
    fun next() { if (step < last) step++ else onFinish() }
    androidx.activity.compose.BackHandler { back() } // as the home app, Back never leaves setup

    CompositionLocalProvider(LocalOverlayHost provides overlay) {
        Box(
            Modifier
                .fillMaxSize()
                .refocusWhenLost { currentFirst[0] }
                .trapFocus()
                .background(Lh.colors.background)
                .onKeyEvent { e -> if (e.type == KeyEventType.KeyDown && (e.key == Key.ButtonB || e.key == Key.Escape)) { back(); true } else false },
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Lighthouse setup", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(20.dp))
                    Steps.forEachIndexed { i, s ->
                        Text(
                            "${i + 1}. $s", fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp),
                            color = if (i == step) Color.White else Lh.colors.textMuted.copy(alpha = if (i < step) 0.9f else 0.5f),
                            fontWeight = if (i == step) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
                key(step) {
                    val first = remember { FocusRequester() }
                    currentFirst[0] = first
                    val firstMod = Modifier.focusRequester(first)
                    Column(
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Column(Modifier.width(760.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            when (step) {
                                0 -> {
                                    Text("Welcome to Lighthouse", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                                    Note("A few quick steps: Lighthouse has already looked for your games and emulators, so mostly you just check what it found. Everything here can be changed later in Settings (Start).")
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        PillButton("Get started", firstMod) { next() }
                                        PillButton("Skip setup", onClick = onFinish)
                                    }
                                }
                                1 -> {
                                    Title("Your games")
                                    StorageSection(vm, info.storageAccess, firstMod)
                                    FoldersSection(vm, library, info.storageAccess)
                                    val total = library.platforms.filter { it.def.id != io.cttech.lighthouse.library.PlatformCatalog.ANDROID }.sumOf { it.count }
                                    if (info.storageAccess) Note("Found $total games so far. Check each folder's platform; A on a folder changes it.")
                                }
                                2 -> {
                                    Title("Emulators")
                                    Note("The emulator each platform uses. If one's missing, install it and come back; Lighthouse notices.")
                                    EmulatorsSection(vm, info.rootAvailable, firstMod)
                                }
                                3 -> {
                                    Title("Covers")
                                    CoversSection(vm, firstMod, full = false)
                                    SettingRow("Find covers now", null, note = "Runs in the background; you can carry on.") { vm.findMissingCovers() }
                                }
                                4 -> {
                                    Title("Look & buttons")
                                    ThemeSection(themes, firstMod)
                                    ShelfSection()
                                    ButtonLayoutRow()
                                }
                                else -> {
                                    Title("Make Lighthouse your Home screen")
                                    HomeAppSection(firstMod)
                                    Note("You can do this later in Settings → System.")
                                }
                            }
                            if (step > 0) {
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    PillButton(if (step == last) "Finish" else "Next") { next() }
                                    PillButton("Back") { back() }
                                }
                            }
                        }
                    }
                    FocusOnAppear(first, step, themes.size)
                    LaunchedEffect(overlay.closedCount) {
                        if (overlay.closedCount == 0) return@LaunchedEffect
                        val target = overlay.returnTo ?: first
                        repeat(30) { withFrameNanos { }; if (runCatching { target.requestFocus() }.isSuccess) return@LaunchedEffect }
                        runCatching { first.requestFocus() }
                    }
                }
                HintBar(
                    hints = buildList { if (step > 0) add("B" to "Back"); add("A" to "Select") },
                    onTap = { b -> if (b == "B") back() },
                )
            }
            overlay.content?.invoke()
        }
    }
}

@Composable
private fun Title(text: String) = Text(text, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
