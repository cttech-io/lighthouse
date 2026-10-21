package io.cttech.lighthouse.launch

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import java.io.File
import java.io.FileNotFoundException

/**
 * Read-only ROM links shaped like Android document URIs (content://<authority>/document/<path>), for emulators
 * such as PPSSPP that only accept that shape. Not exported: an emulator can only read the one file it was granted
 * for a launch, and only files under /storage are served (the same scope as the FileProvider in rom_paths.xml).
 */
class RomDocumentProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val file = fileFor(uri) ?: return null
        val columns = projection ?: COLUMNS
        val row = columns.map { column ->
            when (column) {
                Document.COLUMN_DOCUMENT_ID -> file.path
                Document.COLUMN_DISPLAY_NAME -> file.name // also OpenableColumns.DISPLAY_NAME
                Document.COLUMN_SIZE -> file.length()
                Document.COLUMN_MIME_TYPE -> MIME
                Document.COLUMN_FLAGS -> 0
                Document.COLUMN_LAST_MODIFIED -> file.lastModified()
                else -> null
            }
        }
        return MatrixCursor(columns).apply { addRow(row) }
    }

    override fun getType(uri: Uri): String? = fileFor(uri)?.let { MIME }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("ROM links are read-only")
        val file = fileFor(uri) ?: throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()

    private fun fileFor(uri: Uri): File? = servable(uri.pathSegments)

    companion object {
        private const val MIME = "application/octet-stream"
        private val COLUMNS = arrayOf(
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_SIZE,
            Document.COLUMN_MIME_TYPE, Document.COLUMN_FLAGS, Document.COLUMN_LAST_MODIFIED,
        )

        /** The link for [file]; the whole path is one encoded segment, as in a real document URI. */
        fun uriFor(authority: String, file: File): Uri =
            Uri.Builder().scheme("content").authority(authority).appendPath("document").appendPath(file.absolutePath).build()

        /** The file a link's decoded [segments] name, if it's an existing file inside [root] (symlinks resolved). */
        internal fun servable(segments: List<String>, root: File = File("/storage")): File? {
            if (segments.size != 2 || segments[0] != "document") return null
            val file = runCatching { File(segments[1]).canonicalFile }.getOrNull() ?: return null
            val base = root.canonicalPath + File.separator
            return file.takeIf { it.path.startsWith(base) && it.isFile }
        }
    }
}
