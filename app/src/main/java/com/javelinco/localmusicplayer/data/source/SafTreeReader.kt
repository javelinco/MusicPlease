package com.javelinco.localmusicplayer.data.source

import android.content.ContentResolver
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class SafTreeReader(
    private val contentResolver: ContentResolver,
) : SourceReader {
    override fun enumerate(source: MusicSource, checkpoint: String?): Flow<SourceEntry> = flow {
        require(source is SafTreeSource)
        val treeUri = android.net.Uri.parse(source.treeUri)
        val pendingDirectories = ArrayDeque<String>()
        pendingDirectories += DocumentsContract.getTreeDocumentId(treeUri)
        var checkpointReached = checkpoint == null

        while (pendingDirectories.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val parentId = pendingDirectories.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            contentResolver.query(childrenUri, PROJECTION, null, null, null)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                val modifiedColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                while (cursor.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    val documentId = cursor.getString(idColumn)
                    val displayName = cursor.getString(nameColumn).orEmpty()
                    val mimeType = cursor.getString(mimeColumn)
                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        pendingDirectories += documentId
                        continue
                    }
                    if (!checkpointReached) {
                        checkpointReached = documentId == checkpoint
                        continue
                    }
                    if (mimeType != SourcePickerContracts.MP3_MIME_TYPE && !displayName.endsWith(".mp3", true)) continue
                    val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                    emit(
                        SourceEntry(
                            sourceId = source.id,
                            stableId = documentId,
                            contentUri = documentUri.toString(),
                            displayName = displayName,
                            mimeType = mimeType,
                            sizeBytes = cursor.nullableLong(sizeColumn),
                            modifiedAtEpochMs = cursor.nullableLong(modifiedColumn),
                            parentDocumentId = parentId,
                        ),
                    )
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private companion object {
        val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
    }
}

internal fun android.database.Cursor.nullableLong(column: Int): Long? =
    if (isNull(column)) null else getLong(column)
