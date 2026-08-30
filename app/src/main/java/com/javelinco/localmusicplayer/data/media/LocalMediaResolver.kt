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
        val prefix = contentResolver.openInputStream(Uri.parse(entry.contentUri))?.use { stream ->
            val header = stream.readNBytes(10)
            if (header.size != 10 || !header.copyOfRange(0, 3).contentEquals("ID3".encodeToByteArray())) {
                null
            } else {
                val bodySize = ((header[6].toInt() and 0x7f) shl 21) or
                    ((header[7].toInt() and 0x7f) shl 14) or
                    ((header[8].toInt() and 0x7f) shl 7) or
                    (header[9].toInt() and 0x7f)
                if (bodySize > MAX_ID3_BODY_BYTES) null else {
                    val footerSize = if (header[5].toInt() and 0x10 != 0) 10 else 0
                    val remainder = stream.readNBytes(bodySize + footerSize)
                    (header + remainder).takeIf { remainder.size == bodySize + footerSize }
                }
            }
        }
        EmbeddedTrackMedia(
            artwork = runCatching { metadataExtractor.extractArtwork(entry) }.getOrNull(),
            lyrics = prefix?.let(Id3LyricsParser::parse) ?: EmbeddedLyrics(),
        )
    }

    private companion object { const val MAX_ID3_BODY_BYTES = 4 * 1024 * 1024 }
}

data class ResolvedTrackMedia(
    val artwork: ByteArray?,
    val lyrics: LyricsDocument?,
    val companionFingerprint: String,
)

interface TrackMediaResolver {
    suspend fun resolve(source: MusicSource, entry: SourceEntry, track: TrackEntity): ResolvedTrackMedia
}

class LocalMediaResolver(
    private val companionReader: CompanionFileReader,
    private val embeddedReader: EmbeddedMediaReader,
) : TrackMediaResolver {
    override suspend fun resolve(source: MusicSource, entry: SourceEntry, track: TrackEntity): ResolvedTrackMedia {
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
