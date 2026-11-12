package id.haseda.margaretha.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException
import java.io.OutputStream

sealed interface StorageStatus {
    /** No folder chosen: everything stays in app-managed storage and the gallery. */
    data object AppManaged : StorageStatus
    data class Ready(val folderName: String) : StorageStatus

    /** A folder was chosen but can no longer be used (permission revoked, card or drive removed). */
    data class Unavailable(val reason: String) : StorageStatus
}

/**
 * The folder the user picked through the system file picker (internal storage, SD card, USB drive
 * or any document provider). Everything goes through the Storage Access Framework, so the app needs
 * no storage permission and only ever touches the folder it was given.
 *
 * Layout inside the folder: MargrethaEditor/{Exports,Backups,Projects}/...
 */
class UserStorage(private val context: Context, private val settings: AppSettings) {
    private val resolver = context.contentResolver

    /** Remembers the picked folder and keeps access across restarts. */
    fun adopt(treeUri: Uri): StorageStatus {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val granted = runCatching { resolver.takePersistableUriPermission(treeUri, flags) }.isSuccess
        if (!granted) return StorageStatus.Unavailable("Penyedia penyimpanan ini tidak mengizinkan akses jangka panjang. Pilih folder lain.")
        release()
        settings.storageTreeUri = treeUri.toString()
        return status()
    }

    /** Forgets the folder. Files already written there are left alone. */
    fun release() {
        val saved = settings.storageTreeUri ?: return
        runCatching {
            resolver.releasePersistableUriPermission(Uri.parse(saved), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        settings.storageTreeUri = null
    }

    private fun tree(): Uri? = settings.storageTreeUri?.let(Uri::parse)

    fun status(): StorageStatus {
        val tree = tree() ?: return StorageStatus.AppManaged
        val held = resolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission }
        if (!held) return StorageStatus.Unavailable("Izin ke folder sudah dicabut. Pilih foldernya lagi.")
        val name = runCatching {
            resolver.query(rootDocument(tree), arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: return StorageStatus.Unavailable("Folder tidak bisa dibuka. Kartu SD atau drive mungkin dilepas.")
        return StorageStatus.Ready(name)
    }

    val isReady: Boolean get() = status() is StorageStatus.Ready

    private fun rootDocument(tree: Uri): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    private fun child(tree: Uri, parent: Uri, name: String): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(parent))
        resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1) == name) return DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
            }
        }
        return null
    }

    private fun directory(tree: Uri, parent: Uri, name: String): Uri =
        child(tree, parent, name)
            ?: DocumentsContract.createDocument(resolver, parent, DocumentsContract.Document.MIME_TYPE_DIR, name)
            ?: throw IOException("Tidak bisa membuat folder $name")

    /**
     * Writes a file into MargrethaEditor/<path...>/ in the user folder, replacing a file of the
     * same name. Throws IOException with a readable reason when the folder is unusable or full.
     */
    fun write(path: List<String>, fileName: String, mimeType: String, writer: (OutputStream) -> Unit): Uri {
        val tree = tree() ?: throw IOException("Belum ada folder yang dipilih.")
        (status() as? StorageStatus.Unavailable)?.let { throw IOException(it.reason) }
        try {
            var dir = rootDocument(tree)
            (listOf(ROOT) + path).forEach { dir = directory(tree, dir, it) }
            child(tree, dir, fileName)?.let { DocumentsContract.deleteDocument(resolver, it) }
            val file = DocumentsContract.createDocument(resolver, dir, mimeType, fileName) ?: throw IOException("Tidak bisa membuat file $fileName")
            try {
                resolver.openOutputStream(file)?.use(writer) ?: throw IOException("Tidak bisa menulis $fileName")
            } catch (e: Exception) {
                // Never leave a half-written file behind.
                runCatching { DocumentsContract.deleteDocument(resolver, file) }
                throw e
            }
            return file
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("Penyimpanan tidak bisa dipakai (${e.javaClass.simpleName}). Mungkin penuh atau dilepas.", e)
        }
    }

    companion object {
        const val ROOT = "MargrethaEditor"
        const val EXPORTS = "Exports"
        const val BACKUPS = "Backups"
        const val PROJECTS = "Projects"
    }
}
