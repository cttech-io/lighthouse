package io.cttech.lighthouse.ui.home

import androidx.compose.foundation.layout.widthIn
import io.cttech.lighthouse.ui.components.popupWidth
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import io.cttech.lighthouse.ui.components.trapFocus
import io.cttech.lighthouse.theme.Lh
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.cttech.lighthouse.covers.SteamGridDb
import io.cttech.lighthouse.model.Game
import io.cttech.lighthouse.ui.components.ControllerTextField
import io.cttech.lighthouse.ui.components.PillButton
import io.cttech.lighthouse.ui.components.ConfirmPillButton
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import io.cttech.lighthouse.ui.components.FocusOnAppear
import kotlinx.coroutines.launch
import io.cttech.lighthouse.ui.components.popupHints
import io.cttech.lighthouse.ui.components.ProvideHints
import io.cttech.lighthouse.ui.components.HintBarHeight


/**
 * Choose a cover: libretro's box art for the ROM (no key needed) comes first, then SteamGridDB (search prefilled
 * with the title; Steam shortcuts open on their own art): pick a game, then one of its 600×900 grids.
 * Or use any image from storage, or remove the cover. Without a SteamGridDB key there's no search, and
 * "Set up key" goes to Settings → Covers.
 */
@Composable
fun CoverPicker(game: Game, vm: HomeViewModel, onSetUpKey: () -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var query by remember { mutableStateOf(game.title) }
    var hits by remember { mutableStateOf<List<SteamGridDb.SearchHit>>(emptyList()) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var grids by remember { mutableStateOf<List<SteamGridDb.Grid>>(emptyList()) }
    var libretro by remember { mutableStateOf<SteamGridDb.Grid?>(null) }
    val choices = listOfNotNull(libretro) + grids
    val storageButton = remember { FocusRequester() }
    var status by remember { mutableStateOf<String?>(null) }
    val firstGrid = remember { FocusRequester() }
    val searchBox = remember { FocusRequester() }
    val hasKey = remember { vm.covers.hasSteamGridDbKey() }
    ProvideHints(popupHints()) { if (it == "B") onClose() }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { if (!vm.covers.applyCoverImage(game, uri)) status = "Couldn't read that image" }
    }

    fun loadGrids(gameId: Int? = null, steamAppId: Int? = null) {
        status = "Loading covers…"
        scope.launch {
            grids = vm.covers.coverGrids(gameId, steamAppId)
            status = if (grids.isEmpty()) "No covers for this one: try another result or search" else null
            if (grids.isNotEmpty()) runCatching { firstGrid.requestFocus() }
        }
    }

    fun search() {
        keyboard?.hide()
        if (!hasKey) { status = "Add your SteamGridDB API key in Settings to search"; return }
        status = "Searching…"
        scope.launch {
            hits = vm.covers.searchCovers(query)
            selected = hits.firstOrNull()?.id
            if (hits.isEmpty()) { grids = emptyList(); status = "No matches for \"$query\"" } else loadGrids(gameId = hits.first().id)
        }
    }

    LaunchedEffect(game.key) {
        val appId = vm.covers.steamAppIdFor(game)
        if (hasKey) {
            launch {
                libretro = vm.covers.libretroCover(game)
                if (libretro != null && grids.isEmpty()) focusWhenShown(firstGrid) // SteamGridDB had nothing
            }
            if (appId != null) loadGrids(steamAppId = appId) else search() // exact match for Steam shortcuts
        } else {
            // No key, no search: start on libretro's box art if there is one, else on "Image from storage".
            status = "Looking for box art…"
            libretro = vm.covers.libretroCover(game)
            status = if (libretro != null) "Box art from libretro. Add a SteamGridDB key for more choices." else "No box art found. Add a SteamGridDB key to search for more."
            focusWhenShown(if (libretro != null) firstGrid else storageButton)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .trapFocus() // D-pad can't wander behind the pop-up
            .background(Lh.colors.background.copy(alpha = 0.96f))
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && (e.key == Key.ButtonB || e.key == Key.Escape)) { onClose(); true } else false
            },
    ) {
        Column(Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, top = 20.dp, bottom = HintBarHeight), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Choose cover: ${game.title}", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            if (hasKey) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ControllerTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search SteamGridDB",
                    modifier = Modifier.widthIn(max = 520.dp).weight(1f, fill = false).focusRequester(searchBox),
                    imeAction = ImeAction.Search,
                    onSubmit = ::search,
                )
                PillButton("Search", onClick = ::search)
            }
            if (hits.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(hits, key = { it.id }) { hit ->
                        PillButton((if (hit.id == selected) "● " else "") + hit.name + (hit.year?.let { " ($it)" } ?: "")) {
                            selected = hit.id; loadGrids(gameId = hit.id)
                        }
                    }
                }
            }
            status?.let { Text(it, color = Lh.colors.textMuted, fontSize = 15.sp) }
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                itemsIndexed(choices, key = { _, g -> g.url }) { i, grid ->
                    GridChoice(grid, if (i == 0) Modifier.focusRequester(firstGrid) else Modifier, label = if (grid === libretro) "libretro" else null) {
                        status = "Applying…"
                        scope.launch { if (!vm.covers.applyCover(game, grid.url)) status = "Download failed: try another" }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("Image from storage", Modifier.focusRequester(storageButton)) { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                if (game.coverPath != null) ConfirmPillButton("Remove cover", "Press A again to remove") { vm.removeCover(game) }
                if (!hasKey) PillButton("Set up SteamGridDB key", onClick = onSetUpKey)
                PillButton("Cancel", onClick = onClose)
            }
        }
    }
}

/** Focus [target] once it's laid out (it may only appear on the next frame). */
private suspend fun focusWhenShown(target: FocusRequester) {
    repeat(30) { androidx.compose.runtime.withFrameNanos { }; if (runCatching { target.requestFocus() }.getOrDefault(false)) return }
}

/** One cover to pick. [label] marks where it's from when that isn't SteamGridDB. */
@Composable
private fun GridChoice(grid: SteamGridDb.Grid, modifier: Modifier, label: String? = null, onPick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .aspectRatio(2f / 3f)
            .graphicsLayer { val s = if (focused) 1.08f else 1f; scaleX = s; scaleY = s }
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(if (focused) 3.dp else 0.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.key == Key.ButtonA) { onPick(); true } else false }
            .clickable(onClick = onPick),
    ) {
        // libretro box art isn't 2:3, so it's fitted rather than cropped.
        AsyncImage(
            model = grid.thumb,
            contentDescription = label ?: grid.style,
            contentScale = if (label != null) ContentScale.Fit else ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        label?.let {
            Text(
                it, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/** Select / long-press on a game: quick actions. B closes. */
@Composable
fun GameOptionsSheet(
    game: Game,
    isFavourite: Boolean,
    onPlay: () -> Unit,
    onToggleFavourite: () -> Unit,
    onChangeCover: () -> Unit,
    onRemoveCover: () -> Unit,
    onToggleCountsAsGame: () -> Unit,
    onHide: () -> Unit,
    onClose: () -> Unit,
) {
    val first = remember { FocusRequester() }
    ProvideHints(popupHints()) { if (it == "B") onClose() }
    Box(
        Modifier
            .fillMaxSize()
            .trapFocus() // D-pad can't wander behind the pop-up
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(indication = null, interactionSource = null, onClick = onClose) // tap outside closes
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key in setOf(Key.ButtonB, Key.Escape, Key.ButtonSelect)) { onClose(); true } else false
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .popupWidth(420.dp)
                .padding(vertical = 12.dp)
                .background(Lh.colors.surface, RoundedCornerShape(18.dp))
                .clickable(indication = null, interactionSource = null) {} // taps inside don't close
                .verticalScroll(rememberScrollState()) // small screens: focus scrolls it to the last button
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(game.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            game.subtitle?.let { Text(it, color = Lh.colors.textMuted, fontSize = 14.sp) }
            // Full-width rows read as a menu (and keep a steady focus target); Hide/Remove sits apart at the bottom.
            val row = Modifier.fillMaxWidth()
            PillButton("Play", row.focusRequester(first)) { onClose(); onPlay() }
            PillButton(if (isFavourite) "Remove from favourites" else "Add to favourites", row) { onToggleFavourite(); onClose() }
            PillButton("Change cover", row, onClick = onChangeCover)
            // The image file is deleted, so it can't be undone: ask first.
            if (game.coverPath != null) ConfirmPillButton("Remove cover", "Press A again to remove the cover", row, onConfirm = onRemoveCover)
            // Apps only: ROMs are always games. Decides whether it appears in Continue playing.
            if (game.packageName != null) PillButton(if (game.isGame) "Counts as a game: yes" else "Counts as a game: no", row, onClick = onToggleCountsAsGame)
            Spacer(Modifier.height(4.dp))
            if (game.platformId == io.cttech.lighthouse.library.PlatformCatalog.SHORTCUTS) {
                ConfirmPillButton("Remove shortcut", "Press A again to remove the shortcut", row, onConfirm = onHide)
            } else {
                PillButton("Hide from Lighthouse", row, onClick = onHide) // undone in Settings → Library
            }
        }
    }
    FocusOnAppear(first, game.key)
}

/** After a scan: games arrived without local artwork. Ask once before going online (Settings → Covers). */
@Composable
fun CoverOfferDialog(count: Int, hasKey: Boolean, onFind: () -> Unit, onAlways: () -> Unit, onNotNow: () -> Unit) {
    val first = remember { FocusRequester() }
    ProvideHints(popupHints("Not now")) { if (it == "B") onNotNow() }
    Box(
        Modifier
            .fillMaxSize()
            .trapFocus()
            .background(Color.Black.copy(alpha = 0.6f))
            .onPreviewKeyEvent { e -> if (e.type == KeyEventType.KeyDown && (e.key == Key.ButtonB || e.key == Key.Escape)) { onNotNow(); true } else false },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.popupWidth(520.dp).background(Lh.colors.surface, RoundedCornerShape(18.dp)).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (count == 1) "1 game has no cover" else "$count games have no cover", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                "None of your own images matched. Look for covers online?" +
                    if (hasKey) " (SteamGridDB, then libretro box art.)" else " (libretro box art; add a SteamGridDB key in Settings → Covers for more.)",
                color = Lh.colors.textMuted, fontSize = 15.sp, lineHeight = 20.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("Find covers", Modifier.focusRequester(first), onClick = onFind)
                PillButton("Always, don't ask", onClick = onAlways)
                PillButton("Not now", onClick = onNotNow)
            }
        }
    }
    FocusOnAppear(first, Unit)
}
