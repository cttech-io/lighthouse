package io.cttech.lighthouse.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import io.cttech.lighthouse.system.HapticStrength
import io.cttech.lighthouse.system.LocalAppSettings

/** The navigation "tick" at the user's chosen strength (Settings → Controls). Does nothing when Off. */
@Composable
fun rememberNavTick(): () -> Unit {
    val haptics = LocalHapticFeedback.current
    val type = when (LocalAppSettings.current.haptics) {
        HapticStrength.Off -> return {}
        HapticStrength.Light -> HapticFeedbackType.SegmentFrequentTick
        HapticStrength.Normal -> HapticFeedbackType.SegmentTick
        HapticStrength.Strong -> HapticFeedbackType.ContextClick
    }
    return { haptics.performHapticFeedback(type) }
}
