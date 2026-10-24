package io.cttech.lighthouse.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.cttech.lighthouse.system.KeyReceiver
import io.cttech.lighthouse.system.LocalAppSettings
import io.cttech.lighthouse.theme.Lh
import io.cttech.lighthouse.ui.components.ControllerTextField
import io.cttech.lighthouse.ui.components.FocusOnAppear
import io.cttech.lighthouse.ui.components.PillButton
import io.cttech.lighthouse.ui.components.trapFocus
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** The text on the clipboard as an API key, or null when it doesn't look like one (empty, too long, or with spaces). */
internal fun keyFromClipboard(text: String): String? =
    text.trim().takeIf { it.isNotEmpty() && it.length <= 200 && it.none(Char::isWhitespace) }

/**
 * The SteamGridDB API key: type it, paste it, or send it from a phone, then test it with one search call.
 * [testKey] does that check (off the main thread).
 */
@Composable
internal fun SteamGridDbKeySection(testKey: suspend (String) -> Boolean) {
    val settings = LocalAppSettings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var keyResult by remember(settings.steamGridDbKey) { mutableStateOf<String?>(null) }
    SectionTitle("SteamGridDB")
    Text("API key", color = Color.White, fontSize = 16.sp)
    val overlay = LocalOverlayHost.current
    val phoneButton = remember { FocusRequester() }
    ControllerTextField(
        value = settings.steamGridDbKey,
        onValueChange = { settings.updateSteamGridDbKey(it.trim()) },
        placeholder = "Not set: press A to enter",
        // Down goes to the first button; the geometric search picked the last one under the wide field.
        modifier = Modifier.focusProperties { down = phoneButton },
        secret = true,
        monospace = true,
    )
    fun useKey(key: String) {
        settings.updateSteamGridDbKey(key.trim())
        keyResult = "Testing…"
        scope.launch { keyResult = if (testKey(key.trim())) "✓ Key works" else "✗ Key rejected (or no network)" }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PillButton("Send from phone", Modifier.focusRequester(phoneButton)) {
            overlay.show(phoneButton) { KeyFromPhoneDialog(onKey = { useKey(it); overlay.close() }, onClose = overlay::close) }
        }
        PillButton("Paste") {
            val clip = context.getSystemService(android.content.ClipboardManager::class.java)?.primaryClip
            val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
            val key = text?.let(::keyFromClipboard)
            if (key == null) keyResult = "Nothing key-like on the clipboard" else useKey(key)
        }
        PillButton("Test key") {
            if (settings.steamGridDbKey.isBlank()) { keyResult = "Enter a key first"; return@PillButton }
            useKey(settings.steamGridDbKey)
        }
        PillButton("Get a key (browser)") {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.steamgriddb.com/profile/preferences/api")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        keyResult?.let { Text(it, color = if (it.startsWith("✓")) Color(0xFF5BD68A) else Lh.colors.textMuted, fontSize = 15.sp) }
    }
    Note("Free from steamgriddb.com → sign in → Preferences → API. Easiest: \"Send from phone\" and paste it there. Without a key, Lighthouse uses libretro box art only.")
}

/** QR code for [KeyReceiver]'s one-shot page: scan with a phone on the same Wi-Fi, paste the key, Send. */
@Composable
private fun KeyFromPhoneDialog(onKey: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val main = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    val currentOnKey by androidx.compose.runtime.rememberUpdatedState(onKey)
    // null = starting (the keystore can take a moment the first time); Result.failure = no Wi-Fi/couldn't start.
    val started by androidx.compose.runtime.produceState<Result<KeyReceiver>?>(null) {
        // NonCancellable: if the pop-up closes while the server is starting, we still get it back to stop it.
        // Otherwise it would keep listening (and accepting a key) with nothing left to shut it down.
        val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
            KeyReceiver.start(context) { key -> main.post { currentOnKey(key) } }
        }
        if (!isActive) { r?.stop(); return@produceState }
        value = r?.let { Result.success(it) } ?: Result.failure(IllegalStateException())
        awaitDispose { r?.stop() }
    }
    val receiver = started?.getOrNull()
    val close = remember { FocusRequester() }
    androidx.activity.compose.BackHandler(onBack = onClose)
    Box(
        Modifier
            .fillMaxSize()
            .trapFocus()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(indication = null, interactionSource = null, onClick = onClose)
            .onPreviewKeyEvent { e -> if (e.type == KeyEventType.KeyDown && (e.key == Key.ButtonB || e.key == Key.Escape)) { onClose(); true } else false },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .background(Lh.colors.surface, RoundedCornerShape(18.dp))
                .clickable(indication = null, interactionSource = null) {}
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (receiver != null) QrCode(receiver.url, Modifier.size(220.dp))
            Column(Modifier.widthIn(max = 380.dp).weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Send the key from your phone", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                when {
                    started == null -> Note("Starting…")
                    receiver == null -> Note("Connect to Wi-Fi first: your phone sends the key over your own network.")
                    else -> {
                        Note(
                            "1. Scan the code with your phone's camera (same Wi-Fi as this device).\n" +
                                "2. Your phone warns the connection isn't private: expected for a page on your own network. " +
                                "iPhone: Show Details → visit this website. Android: Advanced → Proceed.\n" +
                                "3. Press Paste key (or paste into the box) and Send.",
                        )
                        Text(receiver.url, color = Lh.colors.textMuted, fontSize = 13.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        Text("Certificate ${receiver.fingerprint}…", color = Lh.colors.textMuted, fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        Note("Waiting for your phone…")
                    }
                }
                PillButton("Cancel", Modifier.focusRequester(close), onClick = onClose)
            }
        }
    }
    FocusOnAppear(close, Unit)
}

@Composable
private fun QrCode(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) { com.google.zxing.qrcode.QRCodeWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, 0, 0) }
    androidx.compose.foundation.Canvas(modifier.background(Color.White, RoundedCornerShape(8.dp))) {
        val quiet = 2 // modules of white border inside the white box
        val cells = matrix.width + quiet * 2
        val cell = size.minDimension / cells
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            if (matrix[x, y]) drawRect(Color.Black, androidx.compose.ui.geometry.Offset((x + quiet) * cell, (y + quiet) * cell), androidx.compose.ui.geometry.Size(cell + 0.5f, cell + 0.5f))
        }
    }
}
