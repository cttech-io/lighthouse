package io.cttech.lighthouse.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * Moves focus into a pop-up once its first control exists. A single requestFocus() on the first frame can
 * fail silently (the node isn't attached yet), leaving focus behind the pop-up where its B handler never
 * sees key events. requestFocus() throws until attached, so retry for a few frames.
 */
@Composable
fun FocusOnAppear(requester: FocusRequester, vararg keys: Any?) {
    LaunchedEffect(*keys) {
        repeat(30) {
            withFrameNanos { }
            if (runCatching { requester.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
}

/**
 * Keeps D-pad focus inside a pop-up while it's open. Without this, moving past an edge (e.g. right of the
 * last theme card) searched the whole screen and landed on the top bar behind the pop-up, where the
 * pop-up's key handlers no longer see presses. Apply to the pop-up's outermost container.
 */
fun androidx.compose.ui.Modifier.trapFocus(): androidx.compose.ui.Modifier =
    this
        .then(androidx.compose.ui.Modifier.focusProperties { onExit = { cancelFocusChange() } })
        .then(androidx.compose.ui.Modifier.focusGroup())

/**
 * Safety net for full-screen pages: if focus leaves this subtree entirely (the focused row was removed, a
 * list reloaded), hand it to [fallback]. Without focus, Compose gets no key events, so B would stop working.
 * Apply before [trapFocus] so it observes the page's focus group.
 */
@Composable
fun androidx.compose.ui.Modifier.refocusWhenLost(fallback: () -> FocusRequester): androidx.compose.ui.Modifier {
    var lost by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    LaunchedEffect(lost) {
        if (lost == 0) return@LaunchedEffect
        repeat(60) {
            withFrameNanos { }
            if (runCatching { fallback().requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
    return this.then(androidx.compose.ui.Modifier.onFocusChanged { if (!it.hasFocus) lost++ })
}
