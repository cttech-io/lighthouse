package io.cttech.lighthouse.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester

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
