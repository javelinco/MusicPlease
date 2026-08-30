package com.javelinco.localmusicplayer.data.media

import android.content.ContentResolver
import android.net.Uri
import com.javelinco.localmusicplayer.data.db.TrackEntity
import com.javelinco.localmusicplayer.data.scan.Mp3MetadataExtractor
import com.javelinco.localmusicplayer.data.source.MusicSource
import com.javelinco.localmusicplayer.data.source.SafTreeSource
import com.javelinco.localmusicplayer.data.source.SourceEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

data class EmbeddedTrackMedia(val artwork: ByteArray?, val lyrics: EmbeddedLyrics)

interface EmbeddedMediaReader {
    suspend fun read(entry: SourceEntry): EmbeddedTrackMedia
}

class AndroidEmbeddedMediaReader(
    private val contentResolver: ContentResolver,
    private val metadataExtractor: Mp3MetadataExtractor,
) : EmbeddedMediaReader {
    override suspend fun read(entry: SourceEntry): EmbeddedTrackMedia = withContext(Dispatchers.IO) {
        val prefix = contentResolver.openInputStream(Uri.parse(entry.contentUri))?.use {
            it.readNBytes(MAX_ID3_BYTES + 1).takeIf { bytes -> bytes.size <= MAX_ID3_BYTES }
        }
        EmbeddedTrackMedia(
            artwork = runCatching { metadataExtractor.extractArtwork(entry) }.getOrNull(),
            lyrics = prefix?.let(Id3LyricsParser::parse) ?: EmbeddedLyrics(),
        )
    }

    private companion object { const val MAX_ID3_BYTES = 4 * 1024 * 1024 + 10 }
}

data class ResolvedTrackMedia(
    val artwork: ByteArray?,
    val lyrics: LyricsDocument?,
    val companionFingerprint: String,
)

class LocalMediaResolver(
    private val companionReader: CompanionFileReader,
    private val embeddedReader: EmbeddedMediaReader,
) {
    suspend fun resolve(source: MusicSource, entry: SourceEntry, track: TrackEntity): ResolvedTrackMedia {
        val embedded = embeddedReader.read(entry)
        if (source !is SafTreeSource || entry.parentDocumentId == null) {
            return ResolvedTrackMedia(embedded.artwork, embedded.lyrics.synchronized ?: embedded.lyrics.plain, "")
        }

        val files = companionReader.list(source, entry.parentDocumentId)
        val byName = files.associateBy { it.name.lowercase() }
        val baseName = track.fileName.substringBeforeLast('.', track.fileName).lowercase()
        val sidecar = byName["$baseName.lrc"]
        val folderArt = listOf("cover.jpg", "folder.jpg", "front.jpg")
            .firstNotNullOfOrNull(byName::get)
        val sidecarLyrics = sidecar
            ?.let { companionReader.read(it, LRC_LIMIT) }
            ?.let(LrcParser::parse)
        val fallbackArtwork = folderArt
            ?.let { companionReader.read(it, ART_LIMIT) }

        val matched = listOfNotNull(sidecar, folderArt)
            .sortedBy { it.name.lowercase() }
            .joinToString("\n") { "${it.name.lowercase()}:${it.sizeBytes}:${it.modifiedAtEpochMs}" }
        val fingerprint = if (matched.isEmpty()) "" else MessageDigest.getInstance("SHA-256")
            .digest(matched.encodeToByteArray()).joinToString("") { "%02x".format(it) }

        return ResolvedTrackMedia(
            artwork = embedded.artwork ?: fallbackArtwork,
            lyrics = sidecarLyrics ?: embedded.lyrics.synchronized ?: embedded.lyrics.plain,
            companionFingerprint = fingerprint,
        )
    }

    private companion object {
        const val LRC_LIMIT = 2 * 1024 * 1024
        const val ART_LIMIT = 12 * 1024 * 1024
    }
}
