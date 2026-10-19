package io.cttech.lighthouse.ui.components

import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val iconCache = LruCache<String, ImageBitmap>(64)

/** Loads an app's launcher icon off the main thread, cached across recompositions and tabs. */
@Composable
fun rememberAppIcon(packageName: String?): ImageBitmap? {
    if (packageName == null) return null
    val context = LocalContext.current
    val icon by produceState(iconCache.get(packageName), packageName) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    context.packageManager.getApplicationIcon(packageName).toBitmap(192, 192).asImageBitmap()
                }.getOrNull()
            }?.also { iconCache.put(packageName, it) }
        }
    }
    return icon
}
