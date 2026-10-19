package io.cttech.lighthouse.ui.home

import io.cttech.lighthouse.theme.Lh
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.cttech.lighthouse.model.Game
import io.cttech.lighthouse.model.HomeLayout
import io.cttech.lighthouse.model.HomeTab
import io.cttech.lighthouse.ui.components.CoverCard
import io.cttech.lighthouse.ui.components.QuickPanel
import io.cttech.lighthouse.ui.components.QuickSettingsIcons
import io.cttech.lighthouse.ui.components.QuickSettingsPanel
import io.cttech.lighthouse.ui.components.rememberBluetoothPermissionRequest
import io.cttech.lighthouse.ui.components.rememberBtEpoch
import io.cttech.lighthouse.ui.components.HintBar
import io.cttech.lighthouse.ui.components.TopBar
import kotlinx.coroutines.launch

private val NeutralAccent = Color(0xFF8A93A6)
private val CarouselSlot = 150.dp
private val CarouselExtra = 70.dp            // focused card is 220dp wide
private const val CarouselGrowth = 70f / 150f

@Composable
fun HomeScreen(vm: HomeViewModel = viewModel()) {
    if (!vm.state.collectAsStateWithLifecycle().value.loaded) return
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val tab = state.currentTab
    val accent = tab.accent()
    // Read only in the draw phase, so the colour animation never recomposes the screen.
    val themeBg = Lh.colors.background
    val bgTop = animateColorAsState(lerpToDark(accent, 0.82f), tween(450), label = "bgTop")

    val rootFocus = remember { FocusRequester() }
    var openPanel by remember { mutableStateOf<QuickPanel?>(null) }
    val panelIcons = remember { QuickPanel.entries.associateWith { FocusRequester() } }
    val (btEpoch, bumpBt) = rememberBtEpoch()
    val requestBt = rememberBluetoothPermissionRequest(onResult = bumpBt)
    val launchIssue by vm.launchIssue.collectAsStateWithLifecycle()
    val coverJob by vm.coverJob.collectAsStateWithLifecycle()
    val settingsInfo by vm.settingsInfo.collectAsStateWithLifecycle()
    val themes by vm.themes.collectAsStateWithLifecycle()
    val optionsFor by vm.optionsFor.collectAsStateWithLifecycle()
    val pickerFor by vm.pickerFor.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) } // Settings page
    val overlay = state.showAccessPrompt || openPanel != null || launchIssue != null || menuOpen || optionsFor != null || pickerFor != null

    var rootFocused by remember { mutableStateOf(false) }
    var refocusToken by remember { mutableIntStateOf(0) }
    // As the home app, Back must never leave Lighthouse: it only closes a panel.
    BackHandler(enabled = true) {
        when {
            pickerFor != null -> vm.closeCoverPicker()
            optionsFor != null -> vm.closeOptions()
            launchIssue != null -> vm.dismissLaunchIssue()
            menuOpen -> menuOpen = false
            openPanel != null -> openPanel = null
            else -> return@BackHandler // nothing open: stay put (home app)
        }
        refocusToken++
    }
    LaunchedEffect(vm) { vm.homePresses.collect { openPanel = null; refocusToken++ } }

    // Focus safety net. Controller input only reaches Compose when something is focused, so the root takes
    // focus at once; the shelf then claims it. If a D-pad press ever lands on the root itself (shelf not
    // laid out yet, or focus lost after returning from another app), bump the token so the shelf re-claims it.
    LaunchedEffect(Unit) { runCatching { rootFocus.requestFocus() } }
    CompositionLocalProvider(LocalRefocusToken provides refocusToken) {
    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(rootFocus)
            .onFocusChanged { rootFocused = it.isFocused }
            .focusable()
            // No theme image for this view: the original platform-tinted gradient.
            .drawBehind { drawRect(Brush.verticalGradient(listOf(bgTop.value, themeBg))) }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || overlay) return@onPreviewKeyEvent false
                if (event.key == Key.ButtonStart) { vm.refreshSettingsInfo(); menuOpen = true; return@onPreviewKeyEvent true }
                if (rootFocused && event.key in DPadKeys) { refocusToken++; return@onPreviewKeyEvent true }
                when (event.key) {
                    Key.ButtonL1 -> { vm.moveTab(-1); true }
                    Key.ButtonR1 -> { vm.moveTab(+1); true }
                    Key.ButtonY -> { if (tab !is HomeTab.Home) vm.toggleLayout(); true }
                    else -> false
                }
            },
    ) {
        ThemeBackground(tab.key)
        Column(Modifier.fillMaxSize()) {
            TopBar(state.tabs, state.selectedTab, onSelect = vm::selectTab) {
                QuickSettingsIcons(panelIcons, btEpoch) { openPanel = it }
            }
            AnimatedContent(
                targetState = tab to state.layout,
                transitionSpec = {
                    val dir = state.tabDirection
                    val tabChanged = initialState.first.key != targetState.first.key
                    if (tabChanged) {
                        (slideInHorizontally(tween(260)) { it / 6 * dir } + fadeIn(tween(260)))
                            .togetherWith(slideOutHorizontally(tween(200)) { -it / 6 * dir } + fadeOut(tween(160)))
                    } else {
                        fadeIn(tween(220)).togetherWith(fadeOut(tween(160)))
                    }
                },
                contentKey = { (t, l) -> t.key + l.name },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                label = "homeBody",
            ) { (shownTab, layout) ->
                val games = state.gamesFor(shownTab)
                val actions = CardActions(
                    accent = shownTab.accent(),
                    favourites = state.favourites,
                    onFocused = { i -> vm.onFocused(shownTab, i) },
                    onLaunch = vm::launch,
                    onToggleFavourite = vm::toggleFavourite,
                    onOptions = vm::openOptions,
                )
                // Re-key on size so removing the focused favourite rebuilds the shelf and restores focus.
                key(games.size) {
                    when {
                        shownTab is HomeTab.Home -> HomeDashboard(
                            rows = state.homeRows,
                            statusLine = state.statusLine, // cover progress shows in the pill instead
                            rememberedKey = state.focusByTab[HomeTab.Home.key]?.gameKey,
                            actions = actions,
                            onFocusedKey = vm::onHomeFocused,
                            onOpenPlatform = vm::selectTab,
                            refocus = state.showAccessPrompt, // quick-settings panels hand focus back to their icon instead
                        )
                        games.isEmpty() -> EmptyShelf(shownTab, refocus = state.showAccessPrompt)
                        layout == HomeLayout.Carousel -> CarouselShelf(games, state.focusIndexFor(shownTab), actions, refocus = state.showAccessPrompt)
                        else -> GridShelf(games, state.focusIndexFor(shownTab), actions, refocus = state.showAccessPrompt)
                    }
                }
            }
            HintBar(
                buildList {
                    if (tab !is HomeTab.Home) add("Y" to if (state.layout == HomeLayout.Carousel) "Grid" else "Carousel")
                    add("☰" to "Settings")
                    add("⧉" to "Options")
                    add("X" to "Favourite")
                    add("A" to "Play")
                },
            )
        }
        openPanel?.let { panel ->
            QuickSettingsPanel(panel, btEpoch, onBtPermission = requestBt) {
                openPanel = null
                // Back to the icon that opened it, like a console's quick menu.
                runCatching { panelIcons.getValue(panel).requestFocus() }
            }
        }
        CoverJobPill(coverJob, Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = 52.dp))
        optionsFor?.let { g ->
            GameOptionsSheet(
                game = g,
                isFavourite = g.key in state.favourites,
                onPlay = { vm.launch(g) },
                onToggleFavourite = { vm.toggleFavourite(g) },
                onChangeCover = { vm.openCoverPicker(g) },
                onRemoveCover = { vm.removeCover(g); refocusToken++ },
                onHide = {
                    vm.hide(g); refocusToken++
                    Toast.makeText(context, "${g.title} hidden. Unhide it in Settings → Library.", Toast.LENGTH_LONG).show()
                },
                onClose = { vm.closeOptions(); refocusToken++ },
            )
        }
        pickerFor?.let { g -> CoverPicker(g, vm) { vm.closeCoverPicker(); refocusToken++ } }
        if (menuOpen) {
            SettingsScreen(
                info = settingsInfo,
                themes = themes,
                hidden = state.hidden,
                onUnhide = vm::unhide,
                onTestKey = vm::testSteamGridDbKey,
                onFindCovers = vm::findMissingCovers,
                onRescan = vm::rescanLibrary,
                onOpenAppInfo = { vm.openAppInfo(context.packageName) },
                onClose = { menuOpen = false; refocusToken++ },
            )
        }
        launchIssue?.let { issue ->
            LaunchIssueDialog(
                issue = issue,
                onDismiss = { vm.dismissLaunchIssue(); refocusToken++ },
                onGrantWithRoot = vm::grantStorageAndRetry,
                onOpenAppInfo = vm::openAppInfo,
                onOpenApp = vm::openApp,
            )
        }
        if (state.showAccessPrompt) {
            AccessPrompt(
                onGrant = { vm.storageAccessIntent()?.let { runCatching { context.startActivity(it) } } },
                onDismiss = vm::dismissAccessPrompt,
            )
        }
    }
    }
}


internal class CardActions(
    val accent: Color,
    val favourites: Set<String>,
    val onFocused: (Int) -> Unit,
    val onLaunch: (Game) -> Unit,
    val onToggleFavourite: (Game) -> Unit,
    val onOptions: (Game) -> Unit,
)

@Composable
private fun CarouselShelf(games: List<Game>, initialFocus: Int, actions: CardActions, refocus: Boolean) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialFocus)
    val scope = rememberCoroutineScope()
    val requester = remember { FocusRequester() }
    var focused by remember { mutableIntStateOf(initialFocus) }
    val current = games.getOrNull(focused) ?: games.first()

    Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
        Column(Modifier.padding(horizontal = 40.dp).height(64.dp)) {
            Text(current.title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(current.detailLine(), color = Lh.colors.textMuted, fontSize = 15.sp)
        }
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(start = 40.dp, end = 600.dp, top = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth().height(CarouselSlot * 1.5f * (1f + CarouselGrowth) + 32.dp),
        ) {
            itemsIndexed(games, key = { _, g -> g.key }) { i, game ->
                // Growth is drawn, not laid out: the focused card scales from its top-left corner and the
                // cards after it slide right by the extra width. No re-measuring while the row scrolls.
                val grow by animateFloatAsState(if (i == focused) 1f else 0f, tween(200), label = "grow")
                val shift by animateFloatAsState(if (i > focused) 1f else 0f, tween(200), label = "shift")
                CoverCard(
                    game = game,
                    accent = actions.accent,
                    isFavourite = game.key in actions.favourites,
                    focusedScale = 1f,
                    modifier = Modifier
                        .width(CarouselSlot)
                        .graphicsLayer {
                            val s = 1f + CarouselGrowth * grow
                            scaleX = s
                            scaleY = s
                            transformOrigin = TransformOrigin(0f, 0f)
                            translationX = CarouselExtra.toPx() * shift
                        }
                        .then(if (i == initialFocus) Modifier.focusRequester(requester) else Modifier),
                    onFocused = {
                        focused = i
                        actions.onFocused(i)
                        scope.launch { listState.animateScrollToItem(i) }
                    },
                    onLaunch = { actions.onLaunch(game) },
                    onToggleFavourite = { actions.onToggleFavourite(game) },
                    onOptions = { actions.onOptions(game) },
                )
            }
        }
    }
    RestoreFocus(requester, refocus)
}

@Composable
private fun GridShelf(games: List<Game>, initialFocus: Int, actions: CardActions, refocus: Boolean) {
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = initialFocus)
    val requester = remember { FocusRequester() }
    var focused by remember { mutableIntStateOf(initialFocus) }
    val current = games.getOrNull(focused) ?: games.first()

    Column(Modifier.fillMaxSize()) {
        Text(
            current.title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 6.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            state = gridState,
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            itemsIndexed(games, key = { _, g -> g.key }) { i, game ->
                CoverCard(
                    game = game,
                    accent = actions.accent,
                    isFavourite = game.key in actions.favourites,
                    modifier = if (i == initialFocus) Modifier.focusRequester(requester) else Modifier,
                    onFocused = { focused = i; actions.onFocused(i) },
                    onLaunch = { actions.onLaunch(game) },
                    onToggleFavourite = { actions.onToggleFavourite(game) },
                    onOptions = { actions.onOptions(game) },
                )
            }
        }
    }
    RestoreFocus(requester, refocus)
}

/**
 * Puts focus on the remembered item once the shelf is laid out, and again whenever an overlay
 * (e.g. the access prompt) goes away — otherwise focus is left nowhere and the controller does nothing.
 */
@Composable
internal fun RestoreFocus(requester: FocusRequester, overlayShowing: Boolean) {
    val token = LocalRefocusToken.current
    LaunchedEffect(requester, overlayShowing, token) {
        if (overlayShowing) return@LaunchedEffect
        // The target item may not be composed on the first frame (rows still loading, lazy lists measuring):
        // keep trying for a few frames instead of failing silently once.
        // requestFocus() throws until the requester's item is attached.
        repeat(30) {
            withFrameNanos { }
            if (runCatching { requester.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
}

/** Bumped when focus must be re-claimed by the visible shelf (see the root's D-pad safety net). */
internal val LocalRefocusToken = androidx.compose.runtime.compositionLocalOf { 0 }

private val DPadKeys = setOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight)

@Composable
private fun EmptyShelf(tab: HomeTab, refocus: Boolean) {
    // Focusable so the controller always has somewhere to be (L1/R1 only reach focused content).
    val requester = remember { FocusRequester() }
    Box(Modifier.fillMaxSize().focusRequester(requester).focusable(), contentAlignment = Alignment.Center) {
        Text(
            if (tab is HomeTab.Home) "No favourites yet. Press X on a game to add it." else "Nothing here yet.",
            color = Lh.colors.textMuted,
            fontSize = 18.sp,
        )
    }
    RestoreFocus(requester, refocus)
}

private fun Game.detailLine(): String = listOfNotNull(subtitle, year?.toString()).joinToString(" · ").ifEmpty { " " }

private fun HomeTab.accent(): Color = when (this) {
    HomeTab.Home -> NeutralAccent
    is HomeTab.PlatformTab -> platform.accent
}

private fun lerpToDark(c: Color, amount: Float) = androidx.compose.ui.graphics.lerp(c, Color(0xFF05060A), amount)

/** "  ·  Finding covers 12/31" while a cover job runs; a short summary once it has finished. */
private fun io.cttech.lighthouse.covers.CoverJob?.statusSuffix(): String = when {
    this == null -> ""
    running -> "  ·  $label $done/$total"
    else -> "  ·  $label: $found new"
}

/** Small non-blocking progress pill (bottom-left) while covers download; fades out a few seconds after. */
@Composable
private fun CoverJobPill(job: io.cttech.lighthouse.covers.CoverJob?, modifier: Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(job) {
        visible = job != null
        if (job != null && !job.running) { kotlinx.coroutines.delay(4_000); visible = false }
    }
    androidx.compose.animation.AnimatedVisibility(visible && job != null, modifier, enter = fadeIn(), exit = fadeOut()) {
        val j = job ?: return@AnimatedVisibility
        Column(
            Modifier
                .background(Lh.colors.surface.copy(alpha = 0.92f), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .width(260.dp),
        ) {
            Text(
                if (j.running) "${j.label} · ${j.done}/${j.total}" else "${j.label}: ${j.found} new",
                color = Color.White, fontSize = 14.sp,
            )
            if (j.running && j.total > 0) {
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.15f), androidx.compose.foundation.shape.RoundedCornerShape(50))) {
                    Box(Modifier.fillMaxWidth(j.done / j.total.toFloat()).height(4.dp).background(Lh.colors.accent, androidx.compose.foundation.shape.RoundedCornerShape(50)))
                }
            }
        }
    }
}

/**
 * The theme's background for this view (HOME, a platform tab…), cross-faded on tab changes, with the
 * theme's scrim on top. Draws nothing when the theme has no image, leaving the tinted gradient.
 */
@Composable
private fun ThemeBackground(viewKey: String) {
    val theme = Lh.theme
    val model = theme.backgroundFor(viewKey) ?: return
    androidx.compose.animation.Crossfade(model, animationSpec = tween(350), label = "themeBackground") { m ->
        coil3.compose.AsyncImage(
            model = m,
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
    if (theme.scrim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = theme.scrim)))
}
