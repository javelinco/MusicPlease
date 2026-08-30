package com.javelinco.localmusicplayer.data.media

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

@Serializable
data class CachedTrackMedia(
    val trackFingerprint: String,
    val companionFingerprint: String,
    val artworkKey: String?,
    val lyrics: LyricsDocument?,
)

class DerivedMediaCache(cacheDir: File, private val json: Json = Json) {
    private val root = File(cacheDir, "derived-media")
    private val records = File(root, "records")
    private val artwork = File(root, "artwork")

    fun read(trackId: String): CachedTrackMedia? = runCatching {
        val file = File(records, "${key(trackId)}.json")
        if (!file.isFile) null else json.decodeFromString<CachedTrackMedia>(file.readText())
    }.getOrNull()

    fun writeRecord(trackId: String, record: CachedTrackMedia) {
        writeAtomically(File(records, "${key(trackId)}.json"), json.encodeToString(record).encodeToByteArray())
    }

    fun writeArtwork(artworkKey: String, bytes: ByteArray) {
        val target = File(artwork, "${key(artworkKey)}.webp")
        if (!target.isFile) writeAtomically(target, bytes)
    }

    fun artworkPath(artworkKey: String): File? = File(artwork, "${key(artworkKey)}.webp").takeIf(File::isFile)
    fun artworkFiles(): List<File> = artwork.listFiles()?.filter(File::isFile).orEmpty()

    private fun writeAtomically(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, "${target.name}.${System.nanoTime()}.tmp")
        temporary.writeBytes(bytes)
        try {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temporary.delete()
        }
    }

    private fun key(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.encodeToByteArray()).joinToString("") { "%02x".format(it) }
}
