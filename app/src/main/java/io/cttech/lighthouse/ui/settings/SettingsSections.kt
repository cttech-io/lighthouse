package io.cttech.lighthouse.ui.settings

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
import io.cttech.lighthouse.model.HomeLayout
import io.cttech.lighthouse.system.HapticStrength
import io.cttech.lighthouse.system.HomeButtonAction
import io.cttech.lighthouse.system.HomeRole
import io.cttech.lighthouse.system.KeyReceiver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.size
import androidx.compose.ui.input.key.onPreviewKeyEvent
import io.cttech.lighthouse.ui.components.trapFocus
import io.cttech.lighthouse.ui.components.FocusOnAppear
import io.cttech.lighthouse.system.LocalAppSettings
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

/** ROM folders: found automatically or added by hand. Change a folder's platform, remove it, or add one. */
@Composable
internal fun FoldersSection(vm: HomeViewModel, info: HomeViewModel.LibraryInfo, hasAccess: Boolean, first: Modifier = Modifier) {
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
                note = if (row.source.autoDetected) "Found automatically" else "Added by you",
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
                            if (it == "remove") vm.removeFolder(row.source)
                            else choosePlatform("Games in this folder are…", row.source.platformId, r) { p -> vm.setFolderPlatform(row.source, p) }
                        },
                        onClose = overlay::close,
                    )
                }
            }
        }
    }
    removed.forEach { row ->
        key(row.source.id) {
            SettingRow(row.source.path.prettyPath(), "Removed · Use again", note = "Found automatically, removed by you") { vm.setFolderPlatform(row.source, row.source.platformId) }
        }
    }
    SettingRow("Add a folder…", null, (if (active.isEmpty()) first else Modifier).focusRequester(addButton)) {
        overlay.show(addButton) {
            FolderBrowser(
                roots = vm.storageRoots(),
                onPick = { dir ->
                    overlay.close()
                    choosePlatform("Games in ${dir.name} are…", vm.guessPlatform(dir.absolutePath), addButton) { p -> vm.addFolder(dir.absolutePath, p) }
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
internal fun LibraryAppsSection(vm: HomeViewModel, info: HomeViewModel.LibraryInfo, hasAccess: Boolean, first: Modifier = Modifier) {
    val apps = vm.syncApps(info)
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
                vm.prepareSyncFolder(app)
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
internal fun GameAppsSection(vm: HomeViewModel, apps: List<HomeViewModel.AppRow>) {
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
internal fun PlatformsSection(vm: HomeViewModel, info: HomeViewModel.LibraryInfo, first: Modifier = Modifier) {
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
                    onSubmit = { vm.renamePlatform(row.def.id, name) },
                )
                Text(
                    (if (row.def.id == PlatformCatalog.ANDROID) "apps" else "${row.count} games") + if (row.hidden) " · hidden" else "",
                    color = Lh.colors.textMuted, fontSize = 14.sp, modifier = Modifier.width(130.dp),
                )
                PillButton(if (row.hidden) "Show" else "Hide") { vm.setPlatformHidden(row.def.id, !row.hidden) }
                PillButton("◀") { vm.movePlatform(row.def.id, -1) }
                PillButton("▶") { vm.movePlatform(row.def.id, +1) }
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
    val platforms by produceState<List<HomeViewModel.PlatformEmulators>?>(null, refresh) { value = vm.emulatorStatus() }
    // Placeholder rows hold focus while loading; when they go, the page's refocus safety net moves it on.
    val list = platforms ?: run { SettingRow("Checking emulators…", modifier = first) {}; return }
    if (list.isEmpty()) { SettingRow("No ROM platforms yet", note = "Add a ROM folder in Settings → Library.", modifier = first) {}; return }
    list.forEachIndexed { i, p ->
        key(p.platformId) {
            val r = remember { FocusRequester() }
            val active = p.options.firstOrNull { it.profile.id == p.activeId }
            val installed = p.options.filter { it.pkg != null }
            SectionTitle(p.label)
            SettingRow(
                label = "Emulator",
                value = active?.profile?.label ?: "None installed",
                modifier = (if (i == 0) first else Modifier).focusRequester(r),
                note = when {
                    active == null -> "Install one of: ${p.options.joinToString { it.profile.label }}"
                    installed.size > 1 -> "A to choose between ${installed.size} installed emulators"
                    else -> null
                },
            ) {
                if (installed.size > 1) overlay.show(r) {
                    OptionPicker(
                        title = "${p.label}: emulator",
                        options = installed,
                        selected = active,
                        label = { it.profile.label },
                        onPick = { vm.chooseEmulator(p.platformId, it.profile.id); overlay.close(); refresh++ },
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
                        if (rootAvailable) scope.launch { vm.grantStorage(active.pkg); refresh++ } else vm.openAppInfo(active.pkg)
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
                if (active.storageOk && active.corePresent != false) Note("Ready")
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
    var keyResult by remember(settings.steamGridDbKey) { mutableStateOf<String?>(null) }
    ChoiceRow(
        label = "Games without your own artwork",
        options = CoverAutoMode.entries,
        selected = settings.coverAuto,
        display = { when (it) { CoverAutoMode.Ask -> "Ask first"; CoverAutoMode.Always -> "Find online automatically"; CoverAutoMode.Never -> "Leave them" } },
        modifier = first,
        note = "Your own images are always used first: a picture named like the ROM next to it, or in Lighthouse/covers/<platform>/.",
    ) { settings.updateCoverAuto(it) }
    SectionTitle("SteamGridDB")
    Text("API key", color = Color.White, fontSize = 16.sp)
    val overlay = LocalOverlayHost.current
    val phoneButton = remember { FocusRequester() }
    ControllerTextField(
        value = settings.steamGridDbKey,
        onValueChange = { settings.updateSteamGridDbKey(it.trim()) },
        placeholder = "Not set: press A to enter",
        // Down goes to the first button; the geometric search picked the last one under the wide field.
        modifier = Modifier.focusProperties { down = phoneButton },
        secret = true,
        monospace = true,
    )
    fun useKey(key: String) {
        settings.updateSteamGridDbKey(key.trim())
        keyResult = "Testing…"
        scope.launch { keyResult = if (vm.testSteamGridDbKey(key.trim())) "✓ Key works" else "✗ Key rejected (or no network)" }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PillButton("Send from phone", Modifier.focusRequester(phoneButton)) {
            overlay.show(phoneButton) { KeyFromPhoneDialog(onKey = { useKey(it); overlay.close() }, onClose = overlay::close) }
        }
        PillButton("Paste") {
            val clip = context.getSystemService(android.content.ClipboardManager::class.java)?.primaryClip
            val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
            if (text.isEmpty() || text.length > 200 || text.any { it.isWhitespace() }) keyResult = "Nothing key-like on the clipboard"
            else useKey(text)
        }
        PillButton("Test key") {
            if (settings.steamGridDbKey.isBlank()) { keyResult = "Enter a key first"; return@PillButton }
            useKey(settings.steamGridDbKey)
        }
        PillButton("Get a key (browser)") {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.steamgriddb.com/profile/preferences/api")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        keyResult?.let { Text(it, color = if (it.startsWith("✓")) Color(0xFF5BD68A) else Lh.colors.textMuted, fontSize = 15.sp) }
    }
    Note("Free from steamgriddb.com → sign in → Preferences → API. Easiest: \"Send from phone\" and paste it there. Without a key, Lighthouse uses libretro box art only.")
    if (full) {
        SectionTitle("Fetch")
        SettingRow("Find missing covers", null, note = "Searches online for every game that has no cover yet.") { vm.findMissingCovers() }
        SettingRow("Re-download all covers", null, note = "Replaces downloaded covers. Your own images and covers you picked stay.") { vm.redownloadAllCovers() }
    }
}

/** QR code for [KeyReceiver]'s one-shot page: scan with a phone on the same Wi-Fi, paste the key, Send. */
@Composable
private fun KeyFromPhoneDialog(onKey: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val main = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    val currentOnKey by androidx.compose.runtime.rememberUpdatedState(onKey)
    // null = starting (the keystore can take a moment the first time); Result.failure = no Wi-Fi/couldn't start.
    val started by androidx.compose.runtime.produceState<Result<KeyReceiver>?>(null) {
        val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            KeyReceiver.start(context) { key -> main.post { currentOnKey(key) } }
        }
        value = r?.let { Result.success(it) } ?: Result.failure(IllegalStateException())
        awaitDispose { r?.stop() }
    }
    val receiver = started?.getOrNull()
    val close = remember { FocusRequester() }
    androidx.activity.compose.BackHandler(onBack = onClose)
    Box(
        Modifier
            .fillMaxSize()
            .trapFocus()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(indication = null, interactionSource = null, onClick = onClose)
            .onPreviewKeyEvent { e -> if (e.type == KeyEventType.KeyDown && (e.key == Key.ButtonB || e.key == Key.Escape)) { onClose(); true } else false },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .background(Lh.colors.surface, RoundedCornerShape(18.dp))
                .clickable(indication = null, interactionSource = null) {}
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (receiver != null) QrCode(receiver.url, Modifier.size(220.dp))
            Column(Modifier.width(380.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Send the key from your phone", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                when {
                    started == null -> Note("Starting…")
                    receiver == null -> Note("Connect to Wi-Fi first: your phone sends the key over your own network.")
                    else -> {
                        Note(
                            "1. Scan the code with your phone's camera (same Wi-Fi as this device).\n" +
                                "2. Your phone warns the connection isn't private: expected for a page on your own network. " +
                                "iPhone: Show Details → visit this website. Android: Advanced → Proceed.\n" +
                                "3. Press Paste key (or paste into the box) and Send.",
                        )
                        Text(receiver.url, color = Lh.colors.textMuted, fontSize = 13.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        Text("Certificate ${receiver.fingerprint}…", color = Lh.colors.textMuted, fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        Note("Waiting for your phone…")
                    }
                }
                PillButton("Cancel", Modifier.focusRequester(close), onClick = onClose)
            }
        }
    }
    FocusOnAppear(close, Unit)
}

@Composable
private fun QrCode(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) { com.google.zxing.qrcode.QRCodeWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, 0, 0) }
    androidx.compose.foundation.Canvas(modifier.background(Color.White, RoundedCornerShape(8.dp))) {
        val quiet = 2 // modules of white border inside the white box
        val cells = matrix.width + quiet * 2
        val cell = size.minDimension / cells
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            if (matrix[x, y]) drawRect(Color.Black, androidx.compose.ui.geometry.Offset((x + quiet) * cell, (y + quiet) * cell), androidx.compose.ui.geometry.Size(cell + 0.5f, cell + 0.5f))
        }
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

/** Theme preview: its HOME background (or a swatch of its colours), name, and a ring when focused. */
@Composable
private fun ThemeCard(theme: LhTheme, selected: Boolean, modifier: Modifier = Modifier, onSelect: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier
            .width(176.dp)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.key == Key.ButtonA) { onSelect(); true } else false }
            .clickable(onClick = onSelect),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(99.dp)
                .background(Brush.linearGradient(listOf(theme.colors.accent.copy(alpha = 0.5f), theme.colors.background)), RoundedCornerShape(10.dp))
                .border(if (focused) 3.dp else if (selected) 2.dp else 0.dp, if (focused) Color.White else theme.colors.accent, RoundedCornerShape(10.dp)),
        ) {
            theme.backgroundFor("home")?.let { bg ->
                coil3.compose.AsyncImage(
                    model = bg, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().padding(if (focused) 3.dp else if (selected) 2.dp else 0.dp).clip(RoundedCornerShape(8.dp)),
                )
            }
        }
        Text((if (selected) "● " else "") + theme.name + if (theme.userTheme) " (yours)" else "", color = Color.White, fontSize = 14.sp, maxLines = 1)
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
    ToggleRow(
        "Root extras", settings.rootExtrasEnabled,
        note = "Uses root, when the device has it, to switch Wi-Fi, Bluetooth and battery saver silently, read the Wi-Fi name, " +
            "check emulator cores and fix emulator permissions. Off behaves exactly like a non-rooted device." +
            if (settings.rootExtrasEnabled && !info.rootAvailable) " (Root isn't available right now.)" else "",
    ) { settings.setRootExtras(it) }
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
    SettingRow("Lighthouse", "${BuildConfig.VERSION_NAME} (${BuildConfig.FLAVOR})", first, note = "GPL-3.0 · github.com/cttech-io/lighthouse") {}
    SectionTitle("Privacy")
    Note(
        "No analytics, no tracking, no accounts. Lighthouse only goes online for cover art:\n" +
            "• SteamGridDB (with your own key): game names are searched to find covers.\n" +
            "• thumbnails.libretro.com: box art fetched by ROM file name.\n" +
            "This happens only when you allow it (Settings → Covers, or the \"find covers?\" prompt). Everything else stays on the device.\n" +
            "• \"Send from phone\" (Settings → Covers) opens a one-time HTTPS page on your own Wi-Fi while its QR code is showing, and closes once the key arrives.",
    )
}

private fun String.prettyPath(): String = this
    .replace("/storage/emulated/0", "Internal")
    .replace(Regex("^/storage/([0-9A-F]{4}-[0-9A-F]{4})"), "SD")
