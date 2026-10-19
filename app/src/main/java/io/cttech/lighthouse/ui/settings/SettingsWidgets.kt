package io.cttech.lighthouse.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.cttech.lighthouse.theme.Lh
import io.cttech.lighthouse.ui.components.FocusOnAppear
import io.cttech.lighthouse.ui.components.trapFocus

private val RowShape = RoundedCornerShape(12.dp)

@Composable
internal fun SectionTitle(title: String) {
    Spacer(Modifier.height(8.dp))
    Text(title.uppercase(), color = Lh.colors.textMuted.copy(alpha = 0.75f), fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
}

@Composable
internal fun Note(text: String, modifier: Modifier = Modifier) =
    Text(text, color = Lh.colors.textMuted, fontSize = 14.sp, lineHeight = 19.sp, modifier = modifier)

/**
 * The basic settings line: a label on the left, a value on the right, optional note underneath.
 * Focusable; A or a tap runs [onClick]. [onLeft]/[onRight] (D-pad) make it a ◀ value ▶ chooser.
 */
@Composable
internal fun SettingRow(
    label: String,
    value: String? = null,
    modifier: Modifier = Modifier,
    note: String? = null,
    enabled: Boolean = true,
    onLeft: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxWidth()
            .background(if (focused) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.05f), RowShape)
            .border(if (focused) 2.dp else 0.dp, if (focused) Lh.colors.focus else Color.Transparent, RowShape)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> onLeft?.let { it(); true } ?: false
                    Key.DirectionRight -> onRight?.let { it(); true } ?: false
                    else -> false
                }
            }
            .onKeyEvent { e -> if (enabled && e.type == KeyEventType.KeyDown && e.key == Key.ButtonA) { onClick(); true } else false }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = if (enabled) Color.White else Lh.colors.textMuted, fontSize = 16.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (value != null) {
                val arrows = onLeft != null || onRight != null
                Text(
                    if (arrows && focused) "◀  $value  ▶" else value,
                    color = if (focused) Color.White else Lh.colors.textMuted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                )
            }
        }
        note?.let { Note(it) }
    }
}

@Composable
internal fun ToggleRow(label: String, checked: Boolean, modifier: Modifier = Modifier, note: String? = null, onToggle: (Boolean) -> Unit) =
    SettingRow(label, if (checked) "On" else "Off", modifier, note, onLeft = { onToggle(!checked) }, onRight = { onToggle(!checked) }) { onToggle(!checked) }

/** ◀ option ▶: A or D-pad right picks the next option, D-pad left the previous one. */
@Composable
internal fun <T> ChoiceRow(
    label: String,
    options: List<T>,
    selected: T,
    display: (T) -> String,
    modifier: Modifier = Modifier,
    note: String? = null,
    onSelect: (T) -> Unit,
) {
    val i = options.indexOf(selected).coerceAtLeast(0)
    fun step(d: Int) = onSelect(options[(i + d).mod(options.size)])
    SettingRow(label, display(selected), modifier, note, onLeft = { step(-1) }, onRight = { step(+1) }) { step(+1) }
}

/** A small list pop-up: pick one of [options]. B / Back / tap outside closes. */
@Composable
internal fun <T> OptionPicker(
    title: String,
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    detail: (T) -> String? = { null },
    onPick: (T) -> Unit,
    onClose: () -> Unit,
) {
    val first = remember { FocusRequester() }
    BackHandler(onBack = onClose)
    Box(
        Modifier
            .fillMaxSize()
            .trapFocus()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(indication = null, interactionSource = null, onClick = onClose)
            .onPreviewKeyEvent { e -> if (e.type == KeyEventType.KeyDown && (e.key == Key.ButtonB || e.key == Key.Escape)) { onClose(); true } else false },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(460.dp)
                .heightIn(max = 520.dp)
                .background(Lh.colors.surface, RoundedCornerShape(18.dp))
                .clickable(indication = null, interactionSource = null) {}
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            val focusIndex = options.indexOf(selected).coerceAtLeast(0)
            options.forEachIndexed { i, o ->
                SettingRow(
                    label = (if (o == selected) "● " else "") + label(o),
                    note = detail(o),
                    modifier = if (i == focusIndex) Modifier.focusRequester(first) else Modifier,
                ) { onPick(o) }
            }
        }
    }
    FocusOnAppear(first, Unit)
}
