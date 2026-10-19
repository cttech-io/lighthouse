package io.cttech.lighthouse.ui.home

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
import io.cttech.lighthouse.ui.components.PillButton
import kotlinx.coroutines.launch

private val Muted = Color(0xFFB7C1D6)

/**
 * Choose a cover: search SteamGridDB (prefilled with the title; Steam shortcuts open on their own art),
 * pick a game, then pick one of its 600×900 grids. Or use any image from storage, or remove the cover.
 */
@Composable
fun CoverPicker(game: Game, vm: HomeViewModel, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var query by remember { mutableStateOf(game.title) }
    var hits by remember { mutableStateOf<List<SteamGridDb.SearchHit>>(emptyList()) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var grids by remember { mutableStateOf<List<SteamGridDb.Grid>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    val firstGrid = remember { FocusRequester() }
    val searchBox = remember { FocusRequester() }
    val hasKey = remember { vm.hasSteamGridDbKey() }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { if (!vm.applyCoverImage(game, uri)) status = "Couldn't read that image" }
    }

    fun loadGrids(gameId: Int? = null, steamAppId: Int? = null) {
        status = "Loading covers…"
        scope.launch {
            grids = vm.coverGrids(gameId, steamAppId)
            status = if (grids.isEmpty()) "No covers for this one: try another result or search" else null
            if (grids.isNotEmpty()) runCatching { firstGrid.requestFocus() }
        }
    }

    fun search() {
        keyboard?.hide()
        if (!hasKey) { status = "Add your SteamGridDB API key in Settings to search"; return }
        status = "Searching…"
        scope.launch {
            hits = vm.searchCovers(query)
            selected = hits.firstOrNull()?.id
            if (hits.isEmpty()) { grids = emptyList(); status = "No matches for \"$query\"" } else loadGrids(gameId = hits.first().id)
        }
    }

    LaunchedEffect(game.key) {
        val appId = vm.steamAppIdFor(game)
        when {
            !hasKey -> { status = "Add your SteamGridDB API key in Settings to search"; runCatching { searchBox.requestFocus() } }
            appId != null -> loadGrids(steamAppId = appId) // exact match for Steam shortcuts
            else -> search()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xF50B0E14))
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && (e.key == Key.ButtonB || e.key == Key.Escape)) { onClose(); true } else false
            },
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Choose cover: ${game.title}", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                var focused by remember { mutableStateOf(false) }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                    modifier = Modifier
                        .width(520.dp)
                        .focusRequester(searchBox)
                        .onFocusChanged { focused = it.isFocused }
                        .onKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.key == Key.ButtonA) { keyboard?.show(); true } else false },
                    decorationBox = { inner ->
                        Box(
                            Modifier
                                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                                .border(if (focused) 2.dp else 0.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) { inner() }
                    },
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
            status?.let { Text(it, color = Muted, fontSize = 15.sp) }
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                itemsIndexed(grids, key = { _, g -> g.url }) { i, grid ->
                    GridChoice(grid, if (i == 0) Modifier.focusRequester(firstGrid) else Modifier) {
                        status = "Applying…"
                        scope.launch { if (!vm.applyCover(game, grid.url)) status = "Download failed: try another" }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("Image from storage") { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                PillButton("Remove cover") { vm.removeCover(game) }
                PillButton("Cancel", onClick = onClose)
            }
        }
    }
}

@Composable
private fun GridChoice(grid: SteamGridDb.Grid, modifier: Modifier, onPick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    AsyncImage(
        model = grid.thumb,
        contentDescription = grid.style,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .aspectRatio(2f / 3f)
            .graphicsLayer { val s = if (focused) 1.08f else 1f; scaleX = s; scaleY = s }
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(if (focused) 3.dp else 0.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.key == Key.ButtonA) { onPick(); true } else false }
            .clickable(onClick = onPick),
    )
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
    onClose: () -> Unit,
) {
    val first = remember { FocusRequester() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key in setOf(Key.ButtonB, Key.Escape, Key.ButtonSelect)) { onClose(); true } else false
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.width(420.dp).background(Color(0xFF151A26), RoundedCornerShape(18.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(game.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            game.subtitle?.let { Text(it, color = Muted, fontSize = 14.sp) }
            PillButton("Play", Modifier.focusRequester(first)) { onClose(); onPlay() }
            PillButton(if (isFavourite) "Remove from favourites" else "Add to favourites") { onToggleFavourite(); onClose() }
            PillButton("Change cover", onClick = onChangeCover)
            if (game.coverPath != null) PillButton("Remove cover", onClick = onRemoveCover)
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}
