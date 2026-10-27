package io.cttech.lighthouse.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.cttech.lighthouse.library.PlatformDef
import io.cttech.lighthouse.library.TitleCleaner
import io.cttech.lighthouse.model.Game
import io.cttech.lighthouse.theme.Lh
import io.cttech.lighthouse.ui.components.ButtonGlyph
import io.cttech.lighthouse.ui.components.ControllerTextField
import io.cttech.lighthouse.ui.components.CoverCard
import io.cttech.lighthouse.ui.components.HintBarHeight
import io.cttech.lighthouse.ui.components.PillButton
import io.cttech.lighthouse.ui.components.ProvideHints
import io.cttech.lighthouse.ui.components.fadingEdges
import io.cttech.lighthouse.ui.components.refocusWhenLost
import io.cttech.lighthouse.ui.components.trapFocus

/**
 * Search the whole library (every shelf, not hidden items), with filters for platform, played and
 * favourites. Opens straight into typing; Y goes back to the text, L1/R1 step through the platforms,
 * B closes. Cards behave as on the shelves: A plays, X favourites, Select opens the game menu.
 *
 * [overlays] are the pop-ups a card can open (game menu, cover picker, launch problem). They're drawn
 * inside this page because it keeps focus to itself, which would refuse them focus if drawn outside.
 */
@Composable
internal fun SearchScreen(
    games: List<Game>,
    platforms: List<PlatformDef>,
    favourites: Set<String>,
    played: Set<String>,
    initialPlatform: String?,
    actions: CardActions,
    onClose: () -> Unit,
    overlays: @Composable () -> Unit,
) {
    var filters by remember { mutableStateOf(SearchFilters(platformId = initialPlatform)) }
    var editRequest by remember { mutableIntStateOf(1) } // start typing straight away
    var focusedKey by remember { mutableStateOf<String?>(null) }
    val field = remember { FocusRequester() }
    val lastCard = remember { FocusRequester() }
    val sortKeys = remember(games) { games.associate { it.key to TitleCleaner.sortKey(it.title) } }
    val results = remember(games, filters, favourites, played) {
        searchLibrary(games, filters, favourites, played) { sortKeys[it.key] ?: TitleCleaner.sortKey(it.title) }
    }
    val accents = remember(platforms) { platforms.associate { it.id to it.accent } }

    fun stepPlatform(delta: Int) {
        // null ("All platforms") sits at both ends of the list, so L1/R1 wrap through it.
        val ids = listOf(null) + platforms.map { it.id }
        val i = ids.indexOf(filters.platformId).coerceAtLeast(0)
        filters = filters.copy(platformId = ids[(i + delta).mod(ids.size)])
    }

    ProvideHints(listOf("B" to "Close", "Y" to "Type", "⧉" to "Options", "X" to "Favourite", "A" to "Play")) {
        when (it) {
            "B" -> onClose()
            "Y" -> editRequest++
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            // Focus lost (a pop-up closed, or back from a game): back to the last card, or the text if it has gone.
            .refocusWhenLost { if (results.any { it.key == focusedKey }) lastCard else field }
            .trapFocus()
            .background(Lh.colors.background.copy(alpha = 0.97f))
            // After the children: B while typing only closes the keyboard (the text field takes it first).
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.ButtonB, Key.Escape -> { onClose(); true }
                    Key.ButtonY -> { editRequest++; true }
                    Key.ButtonL1 -> { stepPlatform(-1); true }
                    Key.ButtonR1 -> { stepPlatform(+1); true }
                    else -> false
                }
            },
    ) {
        Column(
            Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, top = 18.dp, bottom = HintBarHeight),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Search", color = Lh.colors.text, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                ControllerTextField(
                    value = filters.query,
                    onValueChange = { filters = filters.copy(query = it) },
                    placeholder = "Search your games",
                    imeAction = androidx.compose.ui.text.input.ImeAction.Search,
                    editRequest = editRequest,
                    modifier = Modifier.focusRequester(field).widthIn(max = 480.dp).weight(1f, fill = false),
                )
                Text(if (results.size == 1) "1 result" else "${results.size} results", color = Lh.colors.textMuted, fontSize = 14.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ButtonGlyph("L1", decorative = true)
                PillButton(platforms.firstOrNull { it.id == filters.platformId }?.tabLabel ?: "All platforms") { stepPlatform(+1) }
                ButtonGlyph("R1", decorative = true)
                PillButton(filters.played.label) {
                    val all = PlayedFilter.entries
                    filters = filters.copy(played = all[(filters.played.ordinal + 1) % all.size])
                }
                PillButton(if (filters.favouritesOnly) "Favourites only" else "Favourites and others") {
                    filters = filters.copy(favouritesOnly = !filters.favouritesOnly)
                }
                if (filters.anyFilter) PillButton("Clear filters") { filters = SearchFilters(query = filters.query) }
            }
            val focusedTitle = results.firstOrNull { it.key == focusedKey }?.title
            Text(
                focusedTitle ?: " ",
                color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (results.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        if (filters.query.isBlank() && !filters.anyFilter) "Nothing in your library yet." else "No games match.",
                        color = Lh.colors.textMuted, fontSize = 18.sp,
                    )
                }
            } else {
                val gridState = rememberLazyGridState()
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(LocalCardMetrics.current.gridMin),
                    state = gridState,
                    modifier = Modifier.weight(1f).fillMaxWidth().fadingEdges(gridState, 40.dp, vertical = true),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    itemsIndexed(results, key = { _, g -> g.key }) { _, game ->
                        CoverCard(
                            game = game,
                            accent = accents[game.platformId] ?: actions.accent,
                            isFavourite = game.key in favourites,
                            modifier = if (game.key == focusedKey) Modifier.focusRequester(lastCard) else Modifier,
                            onFocused = { focusedKey = game.key },
                            onLaunch = { actions.onLaunch(game) },
                            onToggleFavourite = { actions.onToggleFavourite(game) },
                            onOptions = { actions.onOptions(game) },
                        )
                    }
                }
            }
        }
        overlays()
    }
}
