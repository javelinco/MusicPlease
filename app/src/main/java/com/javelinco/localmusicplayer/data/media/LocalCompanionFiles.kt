package com.javelinco.localmusicplayer.data.media

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import com.javelinco.localmusicplayer.data.source.SafTreeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CompanionFile(
    val name: String,
    val uri: String,
    val sizeBytes: Long,
    val modifiedAtEpochMs: Long,
)

interface CompanionFileReader {
    suspend fun list(source: SafTreeSource, parentDocumentId: String): List<CompanionFile>
    suspend fun read(file: CompanionFile, maxBytes: Int): ByteArray?
}

class AndroidCompanionFileReader(private val contentResolver: ContentResolver) : CompanionFileReader {
    override suspend fun list(source: SafTreeSource, parentDocumentId: String): List<CompanionFile> =
        withContext(Dispatchers.IO) {
            val tree = Uri.parse(source.treeUri)
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentDocumentId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            )
            buildList {
                contentResolver.query(children, projection, null, null, null)?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val documentId = cursor.getString(0) ?: continue
                        val name = cursor.getString(1) ?: continue
                        add(
                            CompanionFile(
                                name = name,
                                uri = DocumentsContract.buildDocumentUriUsingTree(tree, documentId).toString(),
                                sizeBytes = cursor.getLong(2).coerceAtLeast(0),
                                modifiedAtEpochMs = cursor.getLong(3).coerceAtLeast(0),
                            ),
                        )
                    }
                }
            }
        }

    override suspend fun read(file: CompanionFile, maxBytes: Int): ByteArray? = withContext(Dispatchers.IO) {
        if (file.sizeBytes > maxBytes) return@withContext null
        contentResolver.openInputStream(Uri.parse(file.uri))?.use { stream ->
            stream.readNBytes(maxBytes + 1).takeIf { it.size <= maxBytes }
        }
    }
}
