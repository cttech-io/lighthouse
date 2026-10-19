package io.cttech.lighthouse.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.cttech.lighthouse.theme.Lh

/**
 * A text field that never raises the keyboard just because focus landed on it.
 *
 * The D-pad focuses a button-like box showing the value. Only A (or a tap) switches to editing: the real
 * text field becomes focusable, takes focus and shows the keyboard. Done / Back / leaving the field ends
 * editing and hands focus back to the box. (Compose's showKeyboardOnFocus isn't honoured by the
 * value-based BasicTextField, so focusability itself is what's controlled here.)
 */
@Composable
fun ControllerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    secret: Boolean = false,
    monospace: Boolean = false,
    imeAction: ImeAction = ImeAction.Done,
    onSubmit: () -> Unit = {},
) {
    var editing by remember { mutableStateOf(false) }
    var boxFocused by remember { mutableStateOf(false) }
    val box = remember { FocusRequester() }
    val field = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    fun stopEditing() {
        keyboard?.hide()
        editing = false
        runCatching { box.requestFocus() }
    }

    val shape = RoundedCornerShape(10.dp)
    val highlighted = boxFocused || editing
    Box(
        modifier
            .fillMaxWidth()
            .focusRequester(box)
            .onFocusChanged { boxFocused = it.isFocused }
            .onKeyEvent { e ->
                if (!editing && e.type == KeyEventType.KeyDown && (e.key == Key.ButtonA || e.key == Key.Enter || e.key == Key.DirectionCenter)) {
                    editing = true; true
                } else false
            }
            .clickable { editing = true }
            .background(Color.White.copy(alpha = 0.08f), shape)
            .border(if (highlighted) 2.dp else 0.dp, if (highlighted) Lh.colors.focus else Color.Transparent, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            readOnly = !editing,
            textStyle = TextStyle(color = Lh.colors.text, fontSize = 16.sp, fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default),
            cursorBrush = SolidColor(if (editing) Lh.colors.text else Color.Transparent),
            visualTransformation = if (secret && !editing) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text,
                imeAction = imeAction,
                autoCorrectEnabled = !secret,
            ),
            keyboardActions = KeyboardActions(onAny = { onSubmit(); stopEditing() }),
            modifier = Modifier
                .fillMaxWidth()
                // Not focusable unless the user chose to edit: this is what keeps the keyboard away.
                .focusProperties { canFocus = editing }
                .focusRequester(field)
                .onFocusChanged { if (!it.isFocused && editing) editing = false }
                .onPreviewKeyEvent { e ->
                    if (editing && e.type == KeyEventType.KeyDown && (e.key == Key.Back || e.key == Key.ButtonB || e.key == Key.Escape)) {
                        stopEditing(); true
                    } else false
                },
        )
        if (value.isEmpty() && !editing) Text(placeholder, color = Lh.colors.textMuted.copy(alpha = 0.8f), fontSize = 16.sp)
        // While not editing, catch taps here so the inner field can't grab them (and focus) itself.
        if (!editing) Box(Modifier.matchParentSize().clickable(indication = null, interactionSource = null) { editing = true })
    }

    LaunchedEffect(editing) {
        if (editing) {
            withFrameNanos { } // let canFocus = true take effect first
            runCatching { field.requestFocus() }
            keyboard?.show()
        }
    }
}
