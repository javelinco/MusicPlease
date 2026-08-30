package com.javelinco.localmusicplayer.data.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object BackupCodec {
    private const val MANIFEST = "manifest.json"
    private const val USER_DATA = "user-data.json"
    private const val MAX_UNCOMPRESSED_BYTES = 32 * 1024 * 1024
    private val expectedEntries = setOf(MANIFEST, USER_DATA)
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
        prettyPrint = true
    }

    fun encode(bundle: BackupBundle): ByteArray = ByteArrayOutputStream().use { output ->
        ZipOutputStream(output).use { zip ->
            write(zip, MANIFEST, json.encodeToString(bundle.manifest))
            write(zip, USER_DATA, json.encodeToString(bundle.userData))
        }
        output.toByteArray()
    }

    fun decode(bytes: ByteArray): BackupBundle {
        val entries = readEntries(bytes)
        if (entries.keys != expectedEntries) {
            throw InvalidBackupException("Backup must contain exactly $expectedEntries")
        }
        return try {
            val manifest = json.decodeFromString<BackupManifest>(entries.getValue(MANIFEST))
            if (manifest.format != BackupManifest.FORMAT) {
                throw InvalidBackupException("Not a Local Music Player backup")
            }
            if (manifest.schemaVersion !in 1..BackupManifest.CURRENT_SCHEMA_VERSION) {
                throw InvalidBackupException("Unsupported backup schema ${manifest.schemaVersion}")
            }
            BackupBundle(
                manifest = manifest,
                userData = json.decodeFromString<BackupUserData>(entries.getValue(USER_DATA)),
            )
        } catch (error: InvalidBackupException) {
            throw error
        } catch (error: Exception) {
            throw InvalidBackupException("Backup data is invalid", error)
        }
    }

    fun entryNames(bytes: ByteArray): Set<String> = readEntries(bytes).keys

    private fun write(zip: ZipOutputStream, name: String, value: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(value.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun readEntries(bytes: ByteArray): LinkedHashMap<String, String> {
        val result = linkedMapOf<String, String>()
        var total = 0
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    if (entry.isDirectory || !isSafeEntryName(name) || name !in expectedEntries) {
                        throw InvalidBackupException("Unsafe or unexpected backup entry: $name")
                    }
                    if (name in result) throw InvalidBackupException("Duplicate backup entry: $name")
                    val data = ByteArrayOutputStream()
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_UNCOMPRESSED_BYTES) {
                            throw InvalidBackupException("Backup is too large")
                        }
                        data.write(buffer, 0, count)
                    }
                    result[name] = data.toString(Charsets.UTF_8.name())
                    zip.closeEntry()
                }
            }
        } catch (error: InvalidBackupException) {
            throw error
        } catch (error: Exception) {
            throw InvalidBackupException("Backup archive is invalid", error)
        }
        return result
    }

    private fun isSafeEntryName(name: String): Boolean =
        name.isNotBlank() &&
            !name.startsWith('/') &&
            !name.startsWith('\\') &&
            ':' !in name &&
            name.split('/', '\\').none { it == ".." }
}

object BackupRetention {
    fun filesToDelete(names: List<String>, keepAutomatic: Int = 7): List<String> =
        names.asSequence()
            .filter {
                it.startsWith("LocalMusicPlayer-auto-") &&
                    it.endsWith(".zip") &&
                    !it.endsWith(".tmp.zip")
            }
            .sortedDescending()
            .drop(keepAutomatic.coerceAtLeast(0))
            .toList()
}
