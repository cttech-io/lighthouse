package io.cttech.lighthouse.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.cttech.lighthouse.model.Game
import io.cttech.lighthouse.model.GameCollection
import io.cttech.lighthouse.theme.Lh
import io.cttech.lighthouse.ui.components.ControllerTextField
import io.cttech.lighthouse.ui.components.FocusOnAppear
import io.cttech.lighthouse.ui.components.PillButton
import io.cttech.lighthouse.ui.components.ProvideHints
import io.cttech.lighthouse.ui.components.popupHints
import io.cttech.lighthouse.ui.components.popupWidth
import io.cttech.lighthouse.ui.components.trapFocus

/**
 * From the game menu: which of the user's collections [game] is in. A on a collection adds or removes it and
 * keeps the pop-up open, so a game can go in several; typing a name at the bottom makes a new collection
 * with the game in it. B closes.
 */
@Composable
fun CollectionsPicker(
    game: Game,
    collections: List<GameCollection>,
    onToggle: (collectionId: Long) -> Unit,
    onCreate: (name: String) -> Unit,
    onClose: () -> Unit,
) {
    val first = remember { FocusRequester() }
    var newName by remember { mutableStateOf("") }
    fun create() {
        if (newName.isNotBlank()) onCreate(newName)
        newName = ""
    }
    ProvideHints(popupHints()) { if (it == "B") onClose() }
    Box(
        Modifier
            .fillMaxSize()
            .trapFocus()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(indication = null, interactionSource = null, onClick = onClose)
            // After the children: B while naming a collection only closes the keyboard.
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && (e.key == Key.ButtonB || e.key == Key.Escape)) { onClose(); true } else false
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .popupWidth(420.dp)
                .padding(vertical = 12.dp)
                .background(Lh.colors.surface, RoundedCornerShape(18.dp))
                .clickable(indication = null, interactionSource = null) {}
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Collections", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            Text(game.title, color = Lh.colors.textMuted, fontSize = 14.sp, maxLines = 2)
            if (collections.isEmpty()) Text("No collections yet. Name your first one below.", color = Lh.colors.textMuted, fontSize = 15.sp)
            val row = Modifier.fillMaxWidth()
            collections.forEachIndexed { i, c ->
                val inIt = game.key in c.keys
                PillButton(
                    if (inIt) "✓  ${c.name}" else c.name,
                    (if (i == 0) row.focusRequester(first) else row)
                        .semantics { stateDescription = if (inIt) "In this collection" else "Not in this collection" },
                ) { onToggle(c.id) }
            }
            Spacer(Modifier.height(4.dp))
            ControllerTextField(
                value = newName,
                onValueChange = { newName = it },
                placeholder = "New collection",
                // With nothing to pick, naming the first collection is the only thing to do here.
                editRequest = if (collections.isEmpty()) 1 else 0,
                modifier = if (collections.isEmpty()) Modifier.focusRequester(first) else Modifier,
                onSubmit = ::create,
            )
            if (newName.isNotBlank()) PillButton("Create “${newName.trim()}”", row, onClick = ::create)
        }
    }
    FocusOnAppear(first, game.key)
}
