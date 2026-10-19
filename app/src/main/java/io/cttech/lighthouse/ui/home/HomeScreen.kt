package io.cttech.lighthouse.ui.home

import android.widget.Toast
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
import io.cttech.lighthouse.ui.components.HintBar
import io.cttech.lighthouse.ui.components.TopBar
import kotlinx.coroutines.launch

private val NeutralAccent = Color(0xFF8A93A6)
private val CarouselSlot = 150.dp
private val CarouselExtra = 70.dp            // focused card is 220dp wide
private const val CarouselGrowth = 70f / 150f

@Composable
fun HomeScreen(vm: HomeViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val tab = state.currentTab
    val accent = tab.accent()
    // Read only in the draw phase, so the colour animation never recomposes the screen.
    val bgTop = animateColorAsState(lerpToDark(accent, 0.82f), tween(450), label = "bgTop")

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Brush.verticalGradient(listOf(bgTop.value, Color(0xFF040507)))) }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.ButtonL1 -> { vm.moveTab(-1); true }
                    Key.ButtonR1 -> { vm.moveTab(+1); true }
                    Key.ButtonY -> { vm.toggleLayout(); true }
                    else -> false
                }
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            TopBar(state.tabs, state.selectedTab)
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
                    onLaunch = { g -> Toast.makeText(context, "Launch: ${g.title} (milestone 3)", Toast.LENGTH_SHORT).show() },
                    onToggleFavourite = vm::toggleFavourite,
                )
                // Re-key on size so removing the focused favourite rebuilds the shelf and restores focus.
                key(games.size) {
                    when {
                        games.isEmpty() -> EmptyShelf(shownTab)
                        layout == HomeLayout.Carousel -> CarouselShelf(games, state.focusIndexFor(shownTab), actions)
                        else -> GridShelf(games, state.focusIndexFor(shownTab), actions)
                    }
                }
            }
            HintBar(
                listOf(
                    "Y" to if (state.layout == HomeLayout.Carousel) "Grid" else "Carousel",
                    "X" to "Favourite",
                    "A" to "Play",
                ),
            )
        }
    }
}

private class CardActions(
    val accent: Color,
    val favourites: Set<String>,
    val onFocused: (Int) -> Unit,
    val onLaunch: (Game) -> Unit,
    val onToggleFavourite: (Game) -> Unit,
)

@Composable
private fun CarouselShelf(games: List<Game>, initialFocus: Int, actions: CardActions) {
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
            itemsIndexed(games, key = { _, g -> g.id }) { i, game ->
                // Growth is drawn, not laid out: the focused card scales from its top-left corner and the
                // cards after it slide right by the extra width. No re-measuring while the row scrolls.
                val grow by animateFloatAsState(if (i == focused) 1f else 0f, tween(200), label = "grow")
                val shift by animateFloatAsState(if (i > focused) 1f else 0f, tween(200), label = "shift")
                CoverCard(
                    game = game,
                    accent = actions.accent,
                    isFavourite = game.id in actions.favourites,
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
    RestoreFocus(requester)
}

@Composable
private fun GridShelf(games: List<Game>, initialFocus: Int, actions: CardActions) {
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
            itemsIndexed(games, key = { _, g -> g.id }) { i, game ->
                CoverCard(
                    game = game,
                    accent = actions.accent,
                    isFavourite = game.id in actions.favourites,
                    modifier = if (i == initialFocus) Modifier.focusRequester(requester) else Modifier,
                    onFocused = { focused = i; actions.onFocused(i) },
                    onLaunch = { actions.onLaunch(game) },
                    onToggleFavourite = { actions.onToggleFavourite(game) },
                )
            }
        }
    }
    RestoreFocus(requester)
}

/** Puts focus back on the remembered item once the shelf has been laid out. */
@Composable
private fun RestoreFocus(requester: FocusRequester) {
    LaunchedEffect(requester) {
        withFrameNanos { }
        runCatching { requester.requestFocus() }
    }
}

@Composable
private fun EmptyShelf(tab: HomeTab) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            if (tab is HomeTab.Favourites) "No favourites yet. Press X on a game to add it." else "Nothing here yet.",
            color = Color(0xFFB7C1D6),
            fontSize = 18.sp,
        )
    }
    Spacer(Modifier.height(1.dp))
}

private fun Game.detailLine(): String = listOfNotNull(subtitle, year?.toString()).joinToString(" · ").ifEmpty { " " }

private fun HomeTab.accent(): Color = when (this) {
    HomeTab.Favourites -> NeutralAccent
    is HomeTab.PlatformTab -> platform.accent
}

private fun lerpToDark(c: Color, amount: Float) = androidx.compose.ui.graphics.lerp(c, Color(0xFF05060A), amount)
