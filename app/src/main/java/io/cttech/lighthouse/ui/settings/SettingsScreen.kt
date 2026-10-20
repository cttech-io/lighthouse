package io.cttech.lighthouse.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.cttech.lighthouse.model.Game
import io.cttech.lighthouse.theme.Lh
import io.cttech.lighthouse.ui.components.ButtonGlyph
import io.cttech.lighthouse.ui.components.FocusOnAppear
import io.cttech.lighthouse.ui.components.HintBar
import io.cttech.lighthouse.ui.components.trapFocus
import io.cttech.lighthouse.ui.components.refocusWhenLost

private enum class SettingsTab(val label: String) {
    Library("Library"), Platforms("Platforms"), Emulators("Emulators"), Covers("Covers"),
    Appearance("Appearance"), Controls("Controls"), System("System"), About("About"),
}

/**
 * Full-screen Settings (Start button), tabbed like the main screen: L1/R1 or a tap switch sections.
 * B / Back / Start closes. Pop-ups opened from a section take the keys until they close.
 */
@Composable
fun SettingsScreen(
    vm: io.cttech.lighthouse.ui.home.HomeViewModel,
    hidden: List<Game>,
    onUnhide: (Game) -> Unit,
    onRunSetup: () -> Unit,
    onClose: () -> Unit,
) {
    var tabIndex by remember { mutableIntStateOf(SettingsTab.Appearance.ordinal) }
    val tab = SettingsTab.entries[tabIndex]
    val overlay = remember { OverlayHost() }
    val info by vm.settingsInfo.collectAsStateWithLifecycle()
    val library by vm.libraryInfo.collectAsStateWithLifecycle()
    val themes by vm.themes.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) { vm.refreshSettingsInfo(); onPauseOrDispose { } } // e.g. back from granting access
    fun move(d: Int) { tabIndex = (tabIndex + d).mod(SettingsTab.entries.size) }
    val currentFirst = remember { arrayOf(FocusRequester()) } // plain holder: not state, written during composition

    CompositionLocalProvider(LocalOverlayHost provides overlay) {
        Box(
            Modifier
                .fillMaxSize()
                .refocusWhenLost { currentFirst[0] }
                .trapFocus() // D-pad can't wander behind the page
                .background(Lh.colors.background.copy(alpha = 0.97f))
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown || overlay.content != null) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.ButtonL1 -> { move(-1); true }
                        Key.ButtonR1 -> { move(+1); true }
                        Key.ButtonStart -> { onClose(); true }
                        else -> false
                    }
                }
                // B bubbles up here only if nothing inside used it (a text field being edited does).
                .onKeyEvent { e -> if (e.type == KeyEventType.KeyDown && (e.key == Key.ButtonB || e.key == Key.Escape)) { onClose(); true } else false },
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Settings", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(20.dp))
                    ButtonGlyph("L1")
                    Spacer(Modifier.width(8.dp))
                    SettingsTab.entries.forEachIndexed { i, t ->
                        val active = i == tabIndex
                        val bg by animateColorAsState(if (active) Color.White.copy(alpha = 0.16f) else Color.Transparent, label = "settingsTabBg")
                        Box(
                            Modifier.clip(RoundedCornerShape(50)).background(bg)
                                .pointerInput(i) { detectTapGestures { tabIndex = i } }
                                .padding(horizontal = 11.dp, vertical = 6.dp),
                        ) {
                            Text(t.label.uppercase(), color = if (active) Color.White else Lh.colors.textMuted, fontSize = 13.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, letterSpacing = 0.5.sp, maxLines = 1)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    ButtonGlyph("R1")
                }
                key(tab) {
                    val first = remember { FocusRequester() }
                    currentFirst[0] = first
                    val firstMod = Modifier.focusRequester(first)
                    Column(
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Column(Modifier.width(760.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            when (tab) {
                                SettingsTab.Library -> {
                                    StorageSection(vm, info.storageAccess, firstMod)
                                    FoldersSection(vm, library, info.storageAccess)
                                    HiddenSection(hidden, onUnhide)
                                    GameAppsSection(vm, library.apps)
                                }
                                SettingsTab.Platforms -> PlatformsSection(vm, library, firstMod)
                                SettingsTab.Emulators -> EmulatorsSection(vm, info.rootAvailable, firstMod)
                                SettingsTab.Covers -> CoversSection(vm, firstMod)
                                SettingsTab.Appearance -> { ThemeSection(themes, firstMod); ShelfSection() }
                                SettingsTab.Controls -> ControlsSection(firstMod)
                                SettingsTab.System -> SystemSection(vm, info, onRunSetup, firstMod)
                                SettingsTab.About -> AboutSection(firstMod)
                            }
                        }
                    }
                    FocusOnAppear(first, tab, themes.size)
                    // A pop-up closed: back to the control that opened it (or the top of the section).
                    LaunchedEffect(overlay.closedCount) {
                        if (overlay.closedCount == 0) return@LaunchedEffect
                        val target = overlay.returnTo ?: first
                        repeat(30) { withFrameNanos { }; if (runCatching { target.requestFocus() }.isSuccess) return@LaunchedEffect }
                        runCatching { first.requestFocus() } // the opener is gone (e.g. a removed folder)
                    }
                }
                HintBar(
                    hints = listOf("L1" to "", "R1" to "Section", "B" to "Close", "A" to "Select"),
                    onTap = { b -> when (b) { "L1" -> move(-1); "R1" -> move(+1); "B" -> onClose() } },
                )
            }
            overlay.content?.invoke()
        }
    }
}
