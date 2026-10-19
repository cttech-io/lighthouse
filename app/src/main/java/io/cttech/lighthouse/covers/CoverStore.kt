package io.cttech.lighthouse.covers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import java.io.File
import java.security.MessageDigest
import kotlin.math.abs

/** Covers live in app storage as 600×900 JPEGs, one per game key. */
class CoverStore(context: Context) {
    private val dir = File(context.filesDir, "covers").apply { mkdirs() }

    /** Normalises [bytes] (any image) to a 2:3 cover and saves it; returns the file, or null if undecodable. */
    fun save(gameKey: String, bytes: ByteArray): File? {
        val src = decodeBounded(bytes) ?: return null
        val cover = CoverNormalizer.toCover(src)
        // Unique name per save so image caches never show a stale cover after a replacement.
        val out = File(dir, "${sha1(gameKey)}-${System.currentTimeMillis().toString(36)}.jpg")
        out.outputStream().use { cover.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        if (cover !== src) cover.recycle()
        src.recycle()
        dir.listFiles { f -> f.name.startsWith(sha1(gameKey)) && f != out }?.forEach { it.delete() }
        return out
    }

    /** Decodes with subsampling so a huge source image never costs more than ~2× the cover size. */
    private fun decodeBounded(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= CoverNormalizer.W && bounds.outHeight / (sample * 2) >= CoverNormalizer.H) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun sha1(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(20)
}

/** Fits any artwork into 600×900: near-2:3 images are cropped to fill; others sit on a blurred fill of themselves. */
object CoverNormalizer {
    const val W = 600
    const val H = 900
    private const val TARGET = W / H.toFloat()

    fun toCover(src: Bitmap): Bitmap {
        val aspect = src.width / src.height.toFloat()
        val out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        if (abs(aspect - TARGET) < 0.07f) {
            canvas.drawBitmap(src, centreCrop(src.width, src.height), Rect(0, 0, W, H), paint)
            return out
        }
        // Cheap blur: shrink a centre crop to a few pixels, then scale it back up with filtering.
        val tiny = Bitmap.createBitmap(18, 27, Bitmap.Config.ARGB_8888)
        Canvas(tiny).drawBitmap(src, centreCrop(src.width, src.height), Rect(0, 0, 18, 27), paint)
        canvas.drawBitmap(tiny, null, Rect(0, 0, W, H), paint)
        tiny.recycle()
        canvas.drawColor(Color.argb(110, 0, 0, 0))
        val scale = minOf(W / src.width.toFloat(), H / src.height.toFloat())
        val dw = src.width * scale
        val dh = src.height * scale
        canvas.drawBitmap(src, null, RectF((W - dw) / 2, (H - dh) / 2, (W + dw) / 2, (H + dh) / 2), paint)
        return out
    }

    private fun centreCrop(w: Int, h: Int): Rect {
        val aspect = w / h.toFloat()
        return if (aspect > TARGET) {
            val cw = (h * TARGET).toInt(); Rect((w - cw) / 2, 0, (w + cw) / 2, h)
        } else {
            val ch = (w / TARGET).toInt(); Rect(0, (h - ch) / 2, w, (h + ch) / 2)
        }
    }
}
