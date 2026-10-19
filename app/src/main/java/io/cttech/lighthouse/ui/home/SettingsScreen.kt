package io.cttech.lighthouse.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.FocusRequester
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.cttech.lighthouse.BuildConfig
import io.cttech.lighthouse.system.LocalAppSettings
import io.cttech.lighthouse.ui.components.PillButton
import io.cttech.lighthouse.ui.components.FocusOnAppear
import kotlinx.coroutines.launch

private val Muted = Color(0xFFB7C1D6)
private val Dim = Color(0xFF8A93A6)

/** Full-screen Settings (Start button). B / Back / Start closes it. */
@Composable
fun SettingsScreen(
    info: HomeViewModel.SettingsInfo,
    onTestKey: suspend (String) -> Boolean,
    onFindCovers: () -> Unit,
    onRescan: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onClose: () -> Unit,
) {
    val settings = LocalAppSettings.current
    val context = LocalContext.current
    val first = remember { FocusRequester() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xF20B0E14))
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key in setOf(Key.ButtonB, Key.Escape, Key.ButtonStart)) { onClose(); true } else false
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .width(640.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Settings", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)

            Section("Covers")
            Text("SteamGridDB API key", color = Color.White, fontSize = 16.sp)
            ApiKeyField(settings.steamGridDbKey, Modifier.focusRequester(first), settings::updateSteamGridDbKey)
            KeyTest(settings.steamGridDbKey, onTestKey)
            Note("Free from steamgriddb.com → sign in → Preferences → API. Without a key, Lighthouse uses libretro box art only.")
            PillButton("Open SteamGridDB in browser") {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.steamgriddb.com/profile/preferences/api")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            PillButton("Find missing covers") { onFindCovers(); onClose() }
            Note("Your own images always win: put a picture named like the ROM next to it, or in Lighthouse/covers/<platform>/.")

            Section("Library")
            Note(if (info.storageAccess) "All files access: allowed (folders are watched for new games)" else "All files access: not allowed: ROM folders can't be read")
            PillButton("Rescan library") { onRescan(); onClose() }

            Section("System")
            if (!info.network) {
                Note("Lighthouse has no network access, so covers can't be downloaded. On LineageOS, allow it under App info → Mobile data & Wi-Fi.")
                PillButton("Open Lighthouse app info", onClick = onOpenAppInfo)
            }
            PillButton("Root extras: ${if (settings.rootExtrasEnabled) "on" else "off"}") { settings.setRootExtras(!settings.rootExtrasEnabled) }
            Note(
                "Uses root, when the device has it, to switch Wi-Fi, Bluetooth and battery saver silently, read the Wi-Fi name, " +
                    "and fix emulator permissions. Off behaves exactly like a non-rooted device." +
                    if (settings.rootExtrasEnabled && !info.rootAvailable) " (Root isn't available on this device right now.)" else "",
            )
            PillButton("Haptics: ${if (settings.hapticsEnabled) "on" else "off"}") { settings.setHaptics(!settings.hapticsEnabled) }

            Section("About")
            Note("Lighthouse ${BuildConfig.VERSION_NAME} (${BuildConfig.FLAVOR}) · GPL-3.0 · github.com/cttech-io/lighthouse · no analytics or tracking")
            Spacer(Modifier.height(8.dp))
            PillButton("Close", onClick = onClose)
        }
    }
    FocusOnAppear(first, Unit)
}

/** Single-line key entry. A (or a tap) opens the keyboard; the value is masked unless the field is focused. */
@Composable
private fun ApiKeyField(value: String, modifier: Modifier, onChange: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    var text by remember(value) { mutableStateOf(value) }
    val keyboard = LocalSoftwareKeyboardController.current
    BasicTextField(
        value = text,
        onValueChange = { text = it.trim(); onChange(text) },
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontFamily = FontFamily.Monospace),
        cursorBrush = SolidColor(Color.White),
        visualTransformation = if (focused) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.key == Key.ButtonA) { keyboard?.show(); true } else false },
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                    .border(if (focused) 2.dp else 0.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (text.isEmpty()) Text(if (focused) "Paste or type your key" else "Not set: press A to enter", color = Dim, fontSize = 16.sp)
                inner()
            }
        },
    )
}

@Composable
private fun KeyTest(key: String, onTest: suspend (String) -> Boolean) {
    val scope = rememberCoroutineScope()
    var result by remember(key) { mutableStateOf<String?>(null) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        PillButton("Test key") {
            if (key.isBlank()) { result = "Enter a key first"; return@PillButton }
            result = "Testing…"
            scope.launch { result = if (onTest(key)) "✓ Key works" else "✗ Key rejected (or no network)" }
        }
        result?.let { Text(it, color = if (it.startsWith("✓")) Color(0xFF5BD68A) else Muted, fontSize = 15.sp) }
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(10.dp))
    Text(title.uppercase(), color = Dim, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
}

@Composable
private fun Note(text: String) = Text(text, color = Muted, fontSize = 14.sp, lineHeight = 19.sp)
