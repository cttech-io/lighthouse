package io.cttech.lighthouse.ui.settings

import androidx.compose.foundation.layout.widthIn
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.cttech.lighthouse.BuildConfig
import io.cttech.lighthouse.library.PlatformCatalog
import io.cttech.lighthouse.model.Game
import io.cttech.lighthouse.system.CardSize
import io.cttech.lighthouse.system.CoverAutoMode
import io.cttech.lighthouse.system.CrashReports
import io.cttech.lighthouse.model.HomeLayout
import io.cttech.lighthouse.system.HapticStrength
import io.cttech.lighthouse.system.HomeButtonAction
import io.cttech.lighthouse.system.HomeRole
import io.cttech.lighthouse.system.KeyReceiver
import kotlinx.coroutines.isActive
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.size
import androidx.compose.ui.input.key.onPreviewKeyEvent
import io.cttech.lighthouse.ui.components.trapFocus
import io.cttech.lighthouse.ui.components.FocusOnAppear
import io.cttech.lighthouse.system.LocalAppSettings
import io.cttech.lighthouse.system.PlayTime
import io.cttech.lighthouse.system.WifiNameSource
import io.cttech.lighthouse.theme.Lh
import io.cttech.lighthouse.theme.LhTheme
import io.cttech.lighthouse.ui.components.ControllerTextField
import io.cttech.lighthouse.ui.components.PillButton
import io.cttech.lighthouse.ui.components.rememberNavTick
import io.cttech.lighthouse.ui.home.HomeViewModel
import kotlinx.coroutines.launch

/**
 * Pop-ups opened from a settings section (folder browser, option pickers) are drawn by the screen that
 * hosts the sections, full-size and above everything, never inside the scrolling column. Closing one hands
 * focus back to the control that opened it.
 */
internal class OverlayHost {
    var content by mutableStateOf<(@Composable () -> Unit)?>(null)
        private set
    var returnTo: FocusRequester? = null
        private set
    var closedCount by mutableIntStateOf(0)
        private set

    fun show(returnTo: FocusRequester?, content: @Composable () -> Unit) {
        this.returnTo = returnTo
        this.content = content
    }

    fun close() {
        content = null
        closedCount++
    }
}

internal val LocalOverlayHost = staticCompositionLocalOf<OverlayHost> { error("OverlayHost not provided") }

// --- Library ----------------------------------------------------------------------------------------

/** All files access status, with a button to grant it. */
@Composable
internal fun StorageSection(vm: HomeViewModel, hasAccess: Boolean, first: Modifier = Modifier) {
    val context = LocalContext.current
    if (!BuildConfig.ALL_FILES_ACCESS) {
        SettingRow("ROM folders", "Not in this build", first, note = "This build can't read ROM folders yet: only Android apps are listed. (The sideload build can.)") {}
        return
    }
    if (hasAccess) {
        SettingRow("All files access", "Allowed", first, note = "Lighthouse can read your ROM folders and spots new games as soon as they're copied.") {}
    } else {
        SettingRow("All files access", "Grant…", first, note = "Needed to find ROM folders (like Games/PS2 on your SD card). Lighthouse only reads your game folders.") {
            vm.storageAccessIntent()?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        }
    }
}

/** Usage access, which play time needs. Rechecked on return from Android's settings. */
@Composable
internal fun PlayTimeRow() {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(PlayTime.hasAccess(context)) }
    LifecycleResumeEffect(Unit) { allowed = PlayTime.hasAccess(context); onPauseOrDispose { } }
    val open = {
        runCatching { context.startActivity(PlayTime.settingsIntent(context)) }.onFailure { runCatching { context.startActivity(PlayTime.listIntent()) } }
        Unit
    }
    if (allowed) {
        SettingRow("Play time", "On", note = "Lighthouse counts how long each game you start from here is actually in front, using Android's Usage access. " +
            "Sleep and other apps don't count. Turn it off in Android's Usage access settings.", onClick = open)
    } else {
        SettingRow("Play time", "Allow Usage access…", note = "To count play time, Lighthouse needs Android's Usage access. It only looks at the game's app, " +
            "between starting a game and coming back to Lighthouse, and the hours stay on this device.", onClick = open)
    }
}

/** ROM folders: found automatically or added by hand. Change a folder's platform, remove it, or add one. */
@Composable
internal fun FoldersSection(vm: HomeViewModel, info: LibraryInfo, hasAccess: Boolean, first: Modifier = Modifier) {
    val overlay = LocalOverlayHost.current
    val addButton = remember { FocusRequester() }
    val platformChoices = remember { PlatformCatalog.all.filter { it.id != PlatformCatalog.ANDROID } }
    fun label(id: String) = info.platforms.firstOrNull { it.def.id == id }?.def?.tabLabel ?: PlatformCatalog.byId(id)?.tabLabel ?: id

    fun choosePlatform(title: String, current: String?, returnTo: FocusRequester, onPick: (String) -> Unit) = overlay.show(returnTo) {
        OptionPicker(
            title = title,
            options = platformChoices.map { it.id },
            selected = current,
            label = ::label,
            onPick = { onPick(it); overlay.close() },
            onClose = overlay::close,
        )
    }

    SectionTitle("ROM folders")
    if (!hasAccess) { Note("Allow All files access above to see and add folders."); return }
    val active = info.folders.filter { !it.source.ignored }
    val removed = info.folders.filter { it.source.ignored }
    if (active.isEmpty()) Note("No ROM folders found yet. Lighthouse looks for folders named after a system (PS2, GBA, Switch…) inside Games, ROMs or Emulation on each drive.")
    active.forEachIndexed { i, row ->
        key(row.source.id) {
            val r = remember { FocusRequester() }
            SettingRow(
                label = row.source.path.prettyPath(),
                value = "${row.platformLabel} · ${row.count}",
                modifier = (if (i == 0) first else Modifier).focusRequester(r),
                note = if (row.source.autoDetected) null else "Added by you",
            ) {
                overlay.show(r) {
                    OptionPicker(
                        title = row.source.path.prettyPath(),
                        options = listOf("platform", "remove"),
                        selected = null,
                        label = { if (it == "platform") "Platform: ${row.platformLabel}" else "Remove this folder" },
                        detail = { if (it == "remove") "Its games leave Lighthouse. Nothing is deleted from storage." else null },
                        onPick = {
                            overlay.close()
                            if (it == "remove") vm.library.removeFolder(row.source)
                            else choosePlatform("Games in this folder are…", row.source.platformId, r) { p -> vm.library.setFolderPlatform(row.source, p) }
                        },
                        onClose = overlay::close,
                    )
                }
            }
        }
    }
    removed.forEach { row ->
        key(row.source.id) {
            SettingRow(row.source.path.prettyPath(), "Removed · Use again", note = "Found automatically, removed by you") { vm.library.setFolderPlatform(row.source, row.source.platformId) }
        }
    }
    SettingRow("Add a folder…", null, (if (active.isEmpty()) first else Modifier).focusRequester(addButton)) {
        overlay.show(addButton) {
            FolderBrowser(
                roots = vm.library.storageRoots(),
                onPick = { dir ->
                    overlay.close()
                    choosePlatform("Games in ${dir.name} are…", vm.library.guessPlatform(dir.absolutePath), addButton) { p -> vm.library.addFolder(dir.absolutePath, p) }
                },
                onClose = overlay::close,
            )
        }
    }
    SettingRow("Rescan library", null, note = "Lighthouse also rescans whenever you come back to it.") { vm.rescanLibrary() }
}

/**
 * PC-game apps (DroidDeck, GameNative) that can keep a folder of shortcut files in sync themselves:
 * set it up once in the app and installs/uninstalls show up here without exporting anything by hand.
 * A opens the app, after making sure the folder exists so it's there to pick.
 */
@Composable
internal fun LibraryAppsSection(vm: HomeViewModel, info: LibraryInfo, hasAccess: Boolean, first: Modifier = Modifier) {
    val apps = vm.library.syncApps(info)
    if (apps.isEmpty() || !hasAccess) return
    SectionTitle("PC game apps")
    apps.forEachIndexed { i, app ->
        key(app.pkg) {
            val folder = app.folder
            SettingRow(
                label = "${app.label}: keep games in sync",
                value = when {
                    app.count > 0 -> "${app.count} game${if (app.count == 1) "" else "s"} · Open"
                    else -> "Not set up · Open"
                },
                modifier = if (i == 0) first else Modifier,
                note = app.steps + " " + (folder?.let { "Folder: ${it.prettyPath()}." } ?: "Lighthouse makes a ${app.label} folder next to your other game folders.") +
                    if (app.count == 0) " Games then appear on the ${app.label} tab as you install them, and leave when you uninstall." else "",
            ) {
                vm.library.prepareSyncFolder(app)
                vm.openApp(app.pkg)
            }
        }
    }
}

@Composable
internal fun HiddenSection(hidden: List<Game>, onUnhide: (Game) -> Unit) {
    SectionTitle("Hidden")
    if (hidden.isEmpty()) Note("Nothing hidden. Hide apps or games from their options (Select, or long-press).")
    hidden.forEach { g ->
        key(g.key) { SettingRow(g.title, "Unhide", note = g.subtitle) { onUnhide(g) } }
    }
}

/** Which Android apps count as games (Continue playing). Automatic guess unless overridden. */
@Composable
internal fun GameAppsSection(vm: HomeViewModel, apps: List<AppRow>) {
    SectionTitle("Apps that count as games")
    Note("Games show up in Continue playing; other apps don't. Lighthouse guesses; change any app here or from its options.")
    apps.forEach { row ->
        key(row.game.key) {
            SettingRow(row.game.title, (if (row.game.isGame) "Game" else "Not a game") + if (row.automatic) " (auto)" else "") { vm.toggleCountsAsGame(row.game) }
        }
    }
}

// --- Platforms ---------------------------------------------------------------------------------------

/** Tab names, order and visibility. HOME is always first. */
@Composable
internal fun PlatformsSection(vm: HomeViewModel, info: LibraryInfo, first: Modifier = Modifier) {
    Note("HOME always comes first. Rename a tab with A on its name; ◀ ▶ move it; hidden platforms leave the tabs and HOME.")
    info.platforms.forEachIndexed { i, row ->
        key(row.def.id) {
            var name by remember(row.def.tabLabel) { mutableStateOf(row.def.tabLabel) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ControllerTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = row.defaultLabel,
                    modifier = (if (i == 0) first else Modifier).width(220.dp),
                    onSubmit = { vm.library.renamePlatform(row.def.id, name) },
                )
                Text(
                    (if (row.def.id == PlatformCatalog.ANDROID) "${row.count} apps" else "${row.count} games") + if (row.hidden) " · hidden" else "",
                    color = Lh.colors.textMuted, fontSize = 14.sp, modifier = Modifier.width(130.dp),
                )
                PillButton(if (row.hidden) "Show" else "Hide") { vm.library.setPlatformHidden(row.def.id, !row.hidden) }
                PillButton("◀") { vm.library.movePlatform(row.def.id, -1) }
                PillButton("▶") { vm.library.movePlatform(row.def.id, +1) }
            }
        }
    }
}

// --- Emulators -----------------------------------------------------------------------------------------

/** Which emulator runs each platform, and whether it's ready (installed, storage access, core). */
@Composable
internal fun EmulatorsSection(vm: HomeViewModel, rootAvailable: Boolean, first: Modifier = Modifier) {
    val overlay = LocalOverlayHost.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { refresh++; onPauseOrDispose { } } // re-check after visiting an emulator's settings
    val platforms by produceState<List<PlatformEmulators>?>(null, refresh) { value = vm.library.emulatorStatus() }
    // Placeholder rows hold focus while loading; when they go, the page's refocus safety net moves it on.
    val list = platforms ?: run { SettingRow("Checking emulators…", modifier = first) {}; return }
    if (list.isEmpty()) { SettingRow("No ROM platforms yet", note = "Add a ROM folder in Settings → Library.", modifier = first) {}; return }
    list.forEachIndexed { i, p ->
        key(p.platformId) {
            val r = remember { FocusRequester() }
            val active = p.options.firstOrNull { it.profile.id == p.activeId }
            val installed = p.options.filter { it.pkg != null }
            val ready = active?.pkg != null && active.storageOk && active.corePresent != false
            SectionTitle(p.label)
            SettingRow(
                label = "Emulator",
                value = active?.profile?.displayName ?: "None installed",
                modifier = (if (i == 0) first else Modifier).focusRequester(r),
                note = when {
                    active == null -> "Install one of: ${p.options.joinToString { it.profile.displayName }}"
                    installed.size > 1 -> (if (ready) "Ready · " else "") + "A to choose between ${installed.size} installed emulators"
                    ready -> "Ready"
                    else -> null
                },
            ) {
                if (installed.size > 1) overlay.show(r) {
                    OptionPicker(
                        title = "${p.label}: emulator",
                        options = installed,
                        selected = active,
                        label = { it.profile.displayName },
                        onPick = { vm.library.chooseEmulator(p.platformId, it.profile.id); overlay.close(); refresh++ },
                        onClose = overlay::close,
                    )
                }
            }
            if (active != null && active.pkg != null) {
                if (!active.storageOk) {
                    SettingRow(
                        "Storage access", if (rootAvailable) "Grant with root" else "Open app info",
                        note = "${active.profile.label} needs \"Files and media\" access to open ROMs by path.",
                    ) {
                        if (rootAvailable) scope.launch { vm.library.grantStorage(active.pkg); refresh++ } else vm.openAppInfo(active.pkg)
                    }
                }
                when (active.corePresent) {
                    false -> SettingRow(
                        "Core missing", "Open ${active.profile.label.substringBefore(" ·")}",
                        note = "In RetroArch: Main Menu → Load Core → Download a Core → ${active.profile.label.substringAfter("· ")}.",
                    ) { vm.openApp(active.pkg) }
                    null -> Note("Core: can't check without root (it lives in the emulator's private storage).")
                    true -> Unit
                }
            }
        }
    }
}

// --- Covers ----------------------------------------------------------------------------------------------

@Composable
internal fun CoversSection(vm: HomeViewModel, first: Modifier = Modifier, full: Boolean = true) {
    val settings = LocalAppSettings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    ChoiceRow(
        label = "Games without your own artwork",
        options = CoverAutoMode.entries,
        selected = settings.coverAuto,
        display = { when (it) { CoverAutoMode.Ask -> "Ask first"; CoverAutoMode.Always -> "Find online automatically"; CoverAutoMode.Never -> "Leave them" } },
        modifier = first,
        note = "Your own images are always used first: a picture named like the ROM next to it, or in Lighthouse/covers/<platform>/.",
    ) { settings.updateCoverAuto(it) }
    ToggleRow(
        "Covers for Android apps", settings.coverForApps,
        note = "Off: apps keep their icons and are never searched for or offered covers. On: they're treated like games.",
    ) { settings.updateCoverForApps(it) }
    SteamGridDbKeySection(testKey = vm.covers::testSteamGridDbKey)
    if (full) {
        SectionTitle("Fetch")
        SettingRow("Find missing covers", null, note = "Searches online for every game that has no cover yet.") { vm.covers.findMissing() }
        SettingRow("Re-download all covers", null, note = "Replaces downloaded covers. Your own images and covers you picked stay.") { vm.covers.redownloadAll() }
    }
}

// --- Appearance ----------------------------------------------------------------------------------------

@Composable
internal fun ThemeSection(themes: List<LhTheme>, first: Modifier = Modifier) {
    val settings = LocalAppSettings.current
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(themes.size) { i ->
            val t = themes[i]
            val current = t.id == settings.themeId || (i == 0 && themes.none { it.id == settings.themeId })
            ThemeCard(t, selected = t.id == settings.themeId, modifier = if (current) first else Modifier) { settings.updateTheme(t.id) }
        }
    }
    Note("Add your own: put a folder with a theme.json (and background images) in Lighthouse/themes/ on your storage. See docs/THEMES.md in the Lighthouse repo.")
}

/** Layout and card size for the game shelves. */
@Composable
internal fun ShelfSection() {
    val settings = LocalAppSettings.current
    SectionTitle("Shelves")
    ChoiceRow(
        label = "Layout",
        options = HomeLayout.entries,
        selected = settings.layout,
        display = { if (it == HomeLayout.Carousel) "Carousel" else "Grid" },
        note = "How platform tabs show their games. Y on a shelf switches too.",
    ) { settings.updateLayout(it) }
    ChoiceRow(
        label = "Card size",
        options = CardSize.entries,
        selected = settings.cardSize,
        display = { it.name },
        note = "Sizes follow your screen, so Medium suits most devices.",
    ) { settings.updateCardSize(it) }
}

/** The HOME page's sections: their order and which are shown. A on one: move up/down, hide/show. */
@Composable
internal fun HomeSectionsSection() {
    val settings = LocalAppSettings.current
    val overlay = LocalOverlayHost.current
    SectionTitle("Home sections")
    Note("The first section shown gets the large covers. Empty sections are skipped.")
    ToggleRow(
        "Library stats", settings.showHomeStats,
        note = "A line at the top of HOME: games, apps, free space and when the library was last scanned.",
    ) { settings.updateShowHomeStats(it) }
    val order = settings.homeSectionOrder
    order.forEachIndexed { i, id ->
        key(id) {
            val r = remember { FocusRequester() }
            val hidden = id in settings.hiddenHomeSections
            val title = io.cttech.lighthouse.ui.home.HOME_SECTION_TITLES[id] ?: id
            fun move(delta: Int) {
                val j = (i + delta).coerceIn(0, order.lastIndex)
                if (j != i) settings.updateHomeSectionOrder(order.toMutableList().apply { add(j, removeAt(i)) })
            }
            SettingRow("${i + 1}. $title", if (hidden) "Hidden" else "Shown", Modifier.focusRequester(r)) {
                overlay.show(r) {
                    OptionPicker(
                        title = title,
                        options = listOfNotNull("up".takeIf { i > 0 }, "down".takeIf { i < order.lastIndex }, "toggle"),
                        selected = null,
                        label = { when (it) { "up" -> "Move up"; "down" -> "Move down"; else -> if (hidden) "Show on HOME" else "Hide from HOME" } },
                        onPick = {
                            overlay.close()
                            when (it) { "up" -> move(-1); "down" -> move(+1); else -> settings.setHomeSectionHidden(id, !hidden) }
                        },
                        onClose = overlay::close,
                    )
                }
            }
        }
    }
}

/** Theme preview: its HOME background (or a swatch of its colours), name, and a ring when focused. */
@Composable
private fun ThemeCard(theme: LhTheme, selected: Boolean, modifier: Modifier = Modifier, onSelect: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier
            .width(176.dp)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.key == Key.ButtonA) { onSelect(); true } else false }
            .semantics { this.selected = selected } // TalkBack says "selected" rather than reading out "Current"
            .clickable(onClick = onSelect),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(99.dp)
                .background(Brush.linearGradient(listOf(theme.colors.accent.copy(alpha = 0.5f), theme.colors.background)), RoundedCornerShape(10.dp))
                .then(if (focused || selected) Modifier.border(3.dp, if (focused) Color.White else theme.colors.accent, RoundedCornerShape(10.dp)) else Modifier),
        ) {
            theme.backgroundFor("home")?.let { bg ->
                coil3.compose.AsyncImage(
                    model = bg, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().padding(if (focused || selected) 3.dp else 0.dp).clip(RoundedCornerShape(8.dp)),
                )
            }
        }
        Text(theme.name + if (theme.userTheme) " (yours)" else "", color = Color.White, fontSize = 14.sp, maxLines = 1)
        if (selected) Text("Current", color = theme.colors.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clearAndSetSemantics {})
    }
}

// --- Controls ----------------------------------------------------------------------------------------------

@Composable
internal fun ButtonLayoutRow(modifier: Modifier = Modifier) {
    val settings = LocalAppSettings.current
    ChoiceRow(
        label = "Button layout",
        options = listOf(false, true),
        selected = settings.swapAB,
        display = { if (it) "Nintendo (right button confirms)" else "Standard (bottom button confirms)" },
        modifier = modifier,
        note = "Swaps A and B everywhere in Lighthouse. Takes effect straight away.",
    ) { settings.updateSwapAB(it) }
}

@Composable
internal fun ControlsSection(first: Modifier = Modifier) {
    val settings = LocalAppSettings.current
    val tick = rememberNavTick()
    ChoiceRow(
        label = "Haptics",
        options = HapticStrength.entries,
        selected = settings.haptics,
        display = { it.name },
        modifier = first,
        note = "The small tick when moving between items.",
    ) { settings.updateHaptics(it) }
    // Feel the new strength right away (tick is rebuilt with the new setting on recomposition).
    androidx.compose.runtime.LaunchedEffect(settings.haptics) { tick() }
    ButtonLayoutRow()
    ChoiceRow(
        label = "Home button",
        options = HomeButtonAction.entries,
        selected = settings.homeButton,
        display = { if (it == HomeButtonAction.TopLeft) "Top of HOME" else "Where you left off on HOME" },
    ) { settings.updateHomeButton(it) }
}

// --- System -----------------------------------------------------------------------------------------------

/** Whether Lighthouse is Android's Home app, with the system's own "set as default" prompt. */
@Composable
internal fun HomeAppSection(first: Modifier = Modifier) {
    val context = LocalContext.current
    var isDefault by remember { mutableStateOf(HomeRole.isDefault(context)) }
    LifecycleResumeEffect(Unit) { isDefault = HomeRole.isDefault(context); onPauseOrDispose { } }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { isDefault = HomeRole.isDefault(context) }
    if (isDefault) {
        SettingRow("Home app", "Lighthouse ✓", first, note = "The Home button always brings you back here. Change it in Android's Default apps.") {
            runCatching { context.startActivity(HomeRole.settingsIntent()) }
        }
    } else {
        SettingRow("Home app", "Set Lighthouse…", first, note = "Make Lighthouse open when you press Home or turn the device on. Your old launcher stays installed.") {
            val intent = HomeRole.requestIntent(context)
            if (intent != null) runCatching { request.launch(intent) }.onFailure { runCatching { context.startActivity(HomeRole.settingsIntent()) } }
            else runCatching { context.startActivity(HomeRole.settingsIntent()) }
        }
    }
}

@Composable
internal fun SystemSection(vm: HomeViewModel, info: HomeViewModel.SettingsInfo, onRunSetup: () -> Unit, first: Modifier = Modifier) {
    val settings = LocalAppSettings.current
    val context = LocalContext.current
    HomeAppSection(first)
    val rootOn = settings.rootExtrasEnabled
    SettingRow(
        "Root extras",
        if (!rootOn) "Off" else if (info.rootAvailable) "On" else "On · no root",
        note = "Uses root, when the device has it, to switch Wi-Fi, Bluetooth and battery saver silently, read the Wi-Fi name, " +
            "check emulator cores and fix emulator permissions. Off behaves exactly like a non-rooted device.",
        onLeft = { settings.setRootExtras(!rootOn) },
        onRight = { settings.setRootExtras(!rootOn) },
    ) { settings.setRootExtras(!rootOn) }
    ChoiceRow(
        label = "Wi-Fi network name",
        options = WifiNameSource.entries,
        selected = settings.wifiNameSource,
        display = { when (it) { WifiNameSource.Auto -> "Automatic"; WifiNameSource.Location -> "Location permission"; WifiNameSource.Root -> "Root"; WifiNameSource.Off -> "Don't show" } },
        note = "Android only tells apps the network name if they're allowed to use location (with Location on), or through root. " +
            "Automatic tries location first, then root. Shown in the Wi-Fi panel only; Lighthouse doesn't use your location for anything else.",
    ) { settings.updateWifiNameSource(it) }
    if (!info.network) {
        SettingRow("Network access", "Blocked · App info", note = "Lighthouse has no network access, so covers can't be downloaded. On LineageOS, allow it under App info → Mobile data & Wi-Fi.") {
            vm.openAppInfo(context.packageName)
        }
    }
    SettingRow("Run setup again", null, note = "The first-run walkthrough: folders, emulators, covers, look, Home app.", onClick = onRunSetup)
}

@Composable
internal fun AboutSection(first: Modifier = Modifier) {
    val overlay = LocalOverlayHost.current
    val context = LocalContext.current
    val licences = remember { FocusRequester() }
    SettingRow("Lighthouse", "${BuildConfig.VERSION_NAME} (${BuildConfig.FLAVOR})", first, note = "GPL-3.0 · github.com/cttech-io/lighthouse") {}
    SettingRow("Open-source licences", "${OpenSourceLibraries.size} libraries", Modifier.focusRequester(licences)) {
        overlay.show(licences) {
            OptionPicker(
                title = "Open-source licences",
                options = OpenSourceLibraries,
                selected = null,
                label = { it.name },
                detail = { "${it.licence} · ${it.url.removePrefix("https://")}" },
                onPick = { lib ->
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(lib.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                },
                onClose = overlay::close,
            )
        }
    }
    CrashReportRow()
    SectionTitle("Privacy")
    Note(
        "No analytics, no tracking, no accounts. Lighthouse only goes online for cover art:\n" +
            "• SteamGridDB (with your own key): game names are searched to find covers.\n" +
            "• thumbnails.libretro.com: box art fetched by ROM file name.\n" +
            "This happens only when you allow it (Settings → Covers, or the \"find covers?\" prompt). Everything else stays on the device.\n" +
            "• \"Send from phone\" (Settings → Covers) opens a one-time HTTPS page on your own Wi-Fi while its QR code is showing, and closes once the key arrives.\n" +
            "• Crash reports are saved on the device and never sent automatically. After a crash Lighthouse asks; you can send one yourself " +
            "(GitHub, email or the share sheet), and you see it first.\n" +
            "• Play time (with Usage access, Settings → Library) is worked out from when the game's app was in front. Only games " +
            "started from Lighthouse are counted, and the hours stay on the device.",
    )
}

/** The newest crash report (view, send, delete); in debug builds, a way to make one. */
@Composable
private fun CrashReportRow() {
    val overlay = LocalOverlayHost.current
    val context = LocalContext.current
    val r = remember { FocusRequester() }
    var refresh by remember { mutableIntStateOf(0) }
    val report by produceState<CrashReports.Report?>(null, refresh) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { CrashReports.latest(context) }
    }
    val saved = report?.let { java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(it.time)) }
    SettingRow(
        "Last crash report", saved ?: "None", Modifier.focusRequester(r),
        note = if (report != null) "Saved on this device only. A to read it, send it yourself, or delete it." else null,
    ) {
        val shown = report ?: return@SettingRow
        overlay.show(r) {
            io.cttech.lighthouse.ui.home.CrashReportDialog(
                shown, afterCrash = false,
                onDelete = { CrashReports.deleteAll(context); refresh++; overlay.close() },
                onClose = overlay::close,
            )
        }
    }
    if (BuildConfig.DEBUG) SettingRow("Crash now", "Debug build", note = "Throws on purpose, to try the crash report.") {
        throw IllegalStateException("Crash now (Settings → About, debug build)")
    }
}

internal data class OpenSourceLibrary(val name: String, val licence: String, val url: String)

// Everything bundled in the APK. Keep in step with gradle/libs.versions.toml and NOTICE.
internal val OpenSourceLibraries = listOf(
    OpenSourceLibrary("AndroidX (Jetpack Compose, Activity, Lifecycle, Core, Room)", "Apache-2.0", "https://developer.android.com/jetpack/androidx"),
    OpenSourceLibrary("Kotlin standard library", "Apache-2.0", "https://github.com/JetBrains/kotlin"),
    OpenSourceLibrary("kotlinx.coroutines", "Apache-2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
    OpenSourceLibrary("Compose Multiplatform", "Apache-2.0", "https://github.com/JetBrains/compose-multiplatform"),
    OpenSourceLibrary("Coil", "Apache-2.0", "https://github.com/coil-kt/coil"),
    OpenSourceLibrary("Accompanist Drawable Painter", "Apache-2.0", "https://github.com/google/accompanist"),
    OpenSourceLibrary("OkHttp and Okio", "Apache-2.0", "https://github.com/square/okhttp"),
    OpenSourceLibrary("ZXing", "Apache-2.0", "https://github.com/zxing/zxing"),
)

private fun String.prettyPath(): String = this
    .replace("/storage/emulated/0", "Internal")
    .replace(Regex("^/storage/([0-9A-F]{4}-[0-9A-F]{4})"), "SD")
