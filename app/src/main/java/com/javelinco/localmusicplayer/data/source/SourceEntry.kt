package com.javelinco.localmusicplayer.data.source

import com.javelinco.localmusicplayer.core.model.SourceId
import kotlinx.coroutines.flow.Flow

data class SourceEntry(
    val sourceId: SourceId,
    val stableId: String,
    val contentUri: String,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val modifiedAtEpochMs: Long?,
    val parentDocumentId: String? = null,
)

interface SourceReader {
    fun enumerate(source: MusicSource, checkpoint: String? = null): Flow<SourceEntry>
}
