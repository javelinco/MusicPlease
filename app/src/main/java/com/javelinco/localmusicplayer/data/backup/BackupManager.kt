package com.javelinco.localmusicplayer.data.backup

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface BackupStorage {
    suspend fun listNames(): List<String>
    suspend fun read(name: String): ByteArray
    suspend fun write(name: String, bytes: ByteArray)
    suspend fun delete(name: String)
    suspend fun promote(temporaryName: String, finalName: String)
}

class BackupManager(
    private val storage: BackupStorage,
    private val snapshot: suspend () -> BackupBundle,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val restore: suspend (BackupBundle) -> Unit = {},
) {
    suspend fun listBackups(): List<String> = storage.listNames()
        .filter {
            it.startsWith("LocalMusicPlayer-") &&
                it.endsWith(".zip") &&
                !it.endsWith(".tmp.zip")
        }
        .sortedDescending()

    suspend fun createAutomaticIfDue(): Boolean {
        val now = nowEpochMs()
        val day = DAY_FORMAT.format(Instant.ofEpochMilli(now))
        if (storage.listNames().any { it.startsWith("LocalMusicPlayer-auto-$day-") }) return false
        writeValidated("LocalMusicPlayer-auto-${timestamp(now)}.zip", snapshot())
        BackupRetention.filesToDelete(storage.listNames()).forEach { storage.delete(it) }
        return true
    }

    suspend fun createManual(): String {
        val name = "LocalMusicPlayer-manual-${timestamp(nowEpochMs())}.zip"
        writeValidated(name, snapshot())
        return name
    }

    suspend fun restore(name: String) {
        // Validate completely before taking a safety snapshot or mutating user data.
        val incoming = BackupCodec.decode(storage.read(name))
        val safetyName = "LocalMusicPlayer-safety-${timestamp(nowEpochMs())}.zip"
        writeValidated(safetyName, snapshot())
        restore(incoming)
    }

    private suspend fun writeValidated(finalName: String, bundle: BackupBundle) {
        // Some document providers append .zip to application/zip names unless the requested
        // display name already has that suffix. Keep the temporary name ZIP-suffixed so the
        // provider stores exactly the name that validation and promotion subsequently use.
        val temporaryName = "${finalName.removeSuffix(".zip")}.tmp.zip"
        val bytes = BackupCodec.encode(bundle)
        try {
            storage.write(temporaryName, bytes)
            BackupCodec.decode(storage.read(temporaryName))
            storage.promote(temporaryName, finalName)
        } catch (error: Exception) {
            runCatching { storage.delete(temporaryName) }
            throw error
        }
    }

    private fun timestamp(epochMs: Long): String = TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(epochMs))

    private companion object {
        val DAY_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC)
        val TIMESTAMP_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)
    }
}

/** A USB-visible user-selected Storage Access Framework folder. */
class SafBackupStorage(
    private val contentResolver: ContentResolver,
    private val treeUri: Uri,
) : BackupStorage {
    override suspend fun listNames(): List<String> = withContext(Dispatchers.IO) {
        queryDocuments().keys.toList()
    }

    override suspend fun read(name: String): ByteArray = withContext(Dispatchers.IO) {
        val uri = queryDocuments()[name] ?: throw InvalidBackupException("Backup not found: $name")
        contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw InvalidBackupException("Cannot read backup: $name")
    }

    override suspend fun write(name: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        queryDocuments()[name]?.let { DocumentsContract.deleteDocument(contentResolver, it) }
        val parent = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        val uri = DocumentsContract.createDocument(contentResolver, parent, ZIP_MIME, name)
            ?: error("Cannot create backup: $name")
        contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
            ?: error("Cannot write backup: $name")
    }

    override suspend fun delete(name: String) = withContext(Dispatchers.IO) {
        queryDocuments()[name]?.let { DocumentsContract.deleteDocument(contentResolver, it) }
        Unit
    }

    override suspend fun promote(temporaryName: String, finalName: String) = withContext(Dispatchers.IO) {
        val temporary = queryDocuments()[temporaryName] ?: error("Temporary backup is missing")
        queryDocuments()[finalName]?.let { DocumentsContract.deleteDocument(contentResolver, it) }
        DocumentsContract.renameDocument(contentResolver, temporary, finalName)
            ?: error("Cannot finish backup")
        Unit
    }

    private fun queryDocuments(): Map<String, Uri> {
        val documentId = DocumentsContract.getTreeDocumentId(treeUri)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val result = linkedMapOf<String, Uri>()
        contentResolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                result[cursor.getString(nameColumn)] =
                    DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idColumn))
            }
        }
        return result
    }

    private companion object {
        const val ZIP_MIME = "application/zip"
    }
}
