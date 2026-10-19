package io.cttech.lighthouse.ui.home

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
    val bgTop = animateColorAsState(lerpToDark(accent, 0.82f), tween(450), label = "bgTop")

    val rootFocus = remember { FocusRequester() }
    var openPanel by remember { mutableStateOf<QuickPanel?>(null) }
    val panelIcons = remember { QuickPanel.entries.associateWith { FocusRequester() } }
    val (btEpoch, bumpBt) = rememberBtEpoch()
    val requestBt = rememberBluetoothPermissionRequest(onResult = bumpBt)
    val overlay = state.showAccessPrompt || openPanel != null

    // As the home app, Back must never leave Lighthouse: it only closes a panel.
    BackHandler(enabled = true) { openPanel = null }
    LaunchedEffect(vm) { vm.homePresses.collect { openPanel = null } }
    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(rootFocus)
            .focusable() // last-resort focus target: D-pad from here moves into the shelf
            .drawBehind { drawRect(Brush.verticalGradient(listOf(bgTop.value, Color(0xFF040507)))) }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || overlay) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.ButtonL1 -> { vm.moveTab(-1); true }
                    Key.ButtonR1 -> { vm.moveTab(+1); true }
                    Key.ButtonY -> { if (tab !is HomeTab.Home) vm.toggleLayout(); true }
                    else -> false
                }
            },
    ) {
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
                    onLaunch = { g -> if (launch(context, g)) vm.recordLaunch(g) },
                    onToggleFavourite = vm::toggleFavourite,
                )
                // Re-key on size so removing the focused favourite rebuilds the shelf and restores focus.
                key(games.size) {
                    when {
                        shownTab is HomeTab.Home -> HomeDashboard(
                            rows = state.homeRows,
                            statusLine = state.statusLine,
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
        if (state.showAccessPrompt) {
            AccessPrompt(
                onGrant = { vm.storageAccessIntent()?.let { runCatching { context.startActivity(it) } } },
                onDismiss = vm::dismissAccessPrompt,
            )
        }
    }
}

/** Returns true if something was actually started (so it counts towards Continue playing). */
private fun launch(context: android.content.Context, game: Game): Boolean {
    val pkg = game.packageName
    if (pkg != null) {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
        if (intent != null) { context.startActivity(intent); return true }
        Toast.makeText(context, "Can't open ${game.title}", Toast.LENGTH_SHORT).show()
    } else {
        Toast.makeText(context, "Launching games arrives in milestone 3", Toast.LENGTH_SHORT).show()
    }
    return false
}

internal class CardActions(
    val accent: Color,
    val favourites: Set<String>,
    val onFocused: (Int) -> Unit,
    val onLaunch: (Game) -> Unit,
    val onToggleFavourite: (Game) -> Unit,
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
            Text(current.detailLine(), color = Color(0xFFB7C1D6), fontSize = 15.sp)
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
    LaunchedEffect(requester, overlayShowing) {
        if (overlayShowing) return@LaunchedEffect
        withFrameNanos { }
        runCatching { requester.requestFocus() }
    }
}

@Composable
private fun EmptyShelf(tab: HomeTab, refocus: Boolean) {
    // Focusable so the controller always has somewhere to be (L1/R1 only reach focused content).
    val requester = remember { FocusRequester() }
    Box(Modifier.fillMaxSize().focusRequester(requester).focusable(), contentAlignment = Alignment.Center) {
        Text(
            if (tab is HomeTab.Home) "No favourites yet. Press X on a game to add it." else "Nothing here yet.",
            color = Color(0xFFB7C1D6),
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
