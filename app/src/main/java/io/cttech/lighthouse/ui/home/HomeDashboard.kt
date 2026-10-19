package io.cttech.lighthouse.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.cttech.lighthouse.library.PlatformCatalog
import io.cttech.lighthouse.model.HomeItem
import io.cttech.lighthouse.model.HomeRow
import io.cttech.lighthouse.system.LocalAppSettings
import io.cttech.lighthouse.ui.components.CoverCard

private val RowTitle = Color(0xFFB7C1D6)

/**
 * Console-style dashboard: stacked rows, D-pad up/down between rows, left/right within a row.
 * Focus returns to the last focused item (by key) when you come back to Home.
 */
@Composable
internal fun HomeDashboard(
    rows: List<HomeRow>,
    statusLine: String,
    rememberedKey: String?,
    actions: CardActions,
    onFocusedKey: (String) -> Unit,
    onOpenPlatform: (Int) -> Unit,
    refocus: Boolean,
) {
    val requester = remember { FocusRequester() }
    // The same game can sit in several rows (e.g. Favourites and Recently added), so Home positions are
    // "row|item". No memory (fresh start or Home button) = top-left: first item of the top row.
    val positions = rows.flatMap { row -> row.items.map { homePosition(row, it) } }
    val target = positions.firstOrNull { it == rememberedKey } ?: positions.firstOrNull()
    val listState = rememberLazyListState()
    val token = LocalRefocusToken.current
    LaunchedEffect(token, rememberedKey == null) { if (rememberedKey == null) listState.scrollToItem(0) }

    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "status") {
            Text(
                statusLine,
                color = RowTitle.copy(alpha = 0.8f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 2.dp),
            )
        }
        items(rows, key = { it.id }) { row ->
            Column {
                Text(
                    row.title,
                    color = RowTitle,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 32.dp, top = 8.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(row.items, key = { it.key }) { item ->
                        val position = homePosition(row, item)
                        val focusMod = if (position == target) Modifier.focusRequester(requester) else Modifier
                        when (item) {
                            is HomeItem.GameItem -> CoverCard(
                                game = item.game,
                                accent = PlatformCatalog.byId(item.game.platformId)?.accent ?: actions.accent,
                                isFavourite = item.game.key in actions.favourites,
                                modifier = focusMod.width(if (row.hero) 150.dp else 112.dp),
                                focusedScale = 1.07f,
                                onFocused = { onFocusedKey(position) },
                                onLaunch = { actions.onLaunch(item.game) },
                                onToggleFavourite = { actions.onToggleFavourite(item.game) },
                                onOptions = { actions.onOptions(item.game) },
                            )
                            is HomeItem.PlatformItem -> PlatformTile(item, focusMod, { onFocusedKey(position) }) { onOpenPlatform(item.tabIndex) }
                        }
                    }
                }
            }
        }
    }
    RestoreFocus(requester, refocus)
}

/** A shortcut to a platform tab: name, game count, platform colour. A jumps to the tab. */
@Composable
private fun PlatformTile(item: HomeItem.PlatformItem, modifier: Modifier, onFocused: () -> Unit, onOpen: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val hapticsOn = LocalAppSettings.current.hapticsEnabled
    val scale by animateFloatAsState(if (focused) 1.07f else 1f, label = "platformScale")
    val accent = item.platform.accent
    Box(
        modifier
            .size(width = 168.dp, height = 96.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(lerp(accent, Color.Black, 0.35f), lerp(accent, Color.Black, 0.75f))))
            .then(if (focused) Modifier.background(Color.White.copy(alpha = 0.08f)) else Modifier)
            .onFocusChanged {
                if (it.isFocused && !focused) {
                    if (hapticsOn) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onFocused()
                }
                focused = it.isFocused
            }
            .onKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.key == Key.ButtonA) { onOpen(); true } else false }
            .clickable(onClick = onOpen)
            .padding(14.dp),
    ) {
        Column {
            Text(item.platform.tabLabel, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(
                if (item.platform.id == PlatformCatalog.ANDROID) "${item.count} apps" else "${item.count} ${if (item.count == 1) "game" else "games"}",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp,
            )
        }
    }
}

/** Unique position of an item on Home: its row plus its key (a game may appear in more than one row). */
private fun homePosition(row: HomeRow, item: HomeItem) = "${row.id}|${item.key}"
