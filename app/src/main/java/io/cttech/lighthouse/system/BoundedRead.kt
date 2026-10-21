package io.cttech.lighthouse.system

import java.io.File
import java.io.InputStream

/** Largest image or HTTP response Lighthouse will hold in memory. */
const val MAX_IMAGE_BYTES = 20 * 1024 * 1024

/** Largest `.steam`/`.epic` ID file: a decimal ID is at most 20 digits. */
const val MAX_ID_FILE_BYTES = 64

/** Reads the whole stream, or returns null if it holds more than [max] bytes. */
fun InputStream.readBounded(max: Int = MAX_IMAGE_BYTES): ByteArray? {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(8192)
    var total = 0
    while (true) {
        val n = read(buf)
        if (n < 0) break
        total += n
        if (total > max) return null
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}

/** The file's bytes, or null if it is larger than [max]. */
fun File.readBounded(max: Int = MAX_IMAGE_BYTES): ByteArray? =
    if (length() > max) null else inputStream().use { it.readBounded(max) }

/** The file's trimmed text, or null if it is larger than [max] (ID files are tiny). */
fun File.readIdText(max: Int = MAX_ID_FILE_BYTES): String? =
    readBounded(max)?.toString(Charsets.UTF_8)?.trim()
