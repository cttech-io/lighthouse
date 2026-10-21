package io.cttech.lighthouse

import android.app.Activity
import android.content.pm.LauncherApps
import android.os.Bundle
import android.util.Log
import android.widget.Toast

/**
 * Android sends "pin this shortcut" requests (an app's "Create shortcut" / "Add to home screen") to the default
 * home app. The user already chose to add it in that app, so Lighthouse accepts at once, without another prompt
 * a controller would have to dismiss, and the shortcut appears on the Shortcuts tab (Moonlight games: Moonlight).
 */
class PinShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val request = runCatching { getSystemService(LauncherApps::class.java).getPinItemRequest(intent) }.getOrNull()
        val info = request?.shortcutInfo
        if (request != null && request.isValid && request.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT && info != null) {
            val accepted = runCatching { request.accept() }.getOrDefault(false)
            Log.i("LighthouseShortcuts", "pin ${info.`package`}/${info.id} accepted=$accepted")
            if (accepted) {
                (application as LighthouseApp).repository.refreshAll(force = true)
                Toast.makeText(this, "Added to Lighthouse: ${info.shortLabel}", Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }
}
