package com.javelinco.localmusicplayer.data.backup

import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupManagerTest {
    @Test
    fun automaticBackupIsDailyAtomicAndRotatesOnlyAutomaticFiles() = runTest {
        val storage = FakeBackupStorage()
        val clock = { Instant.parse("2026-08-20T12:00:00Z").toEpochMilli() }
        val manager = BackupManager(storage, { bundle(clock()) }, clock)
        (1..8).forEach { storage.files["LocalMusicPlayer-auto-2026081$it-000000.zip"] = validBytes(it.toLong()) }
        storage.files["LocalMusicPlayer-manual-kept.zip"] = validBytes(1)

        assertTrue(manager.createAutomaticIfDue())
        assertFalse(manager.createAutomaticIfDue())

        assertEquals(7, storage.files.keys.count { "-auto-" in it })
        assertTrue("LocalMusicPlayer-manual-kept.zip" in storage.files)
        assertTrue(storage.files.keys.none { it.endsWith(".tmp.zip") })
    }

    @Test
    fun invalidRestoreNeverMutatesAndSafetyBackupPrecedesRestore() = runTest {
        val storage = FakeBackupStorage().apply { files["bad.zip"] = byteArrayOf(1, 2, 3) }
        val events = mutableListOf<String>()
        val manager = BackupManager(
            storage = storage,
            snapshot = { events += "snapshot"; bundle(10) },
            nowEpochMs = { 10 },
            restore = { events += "restore" },
        )

        val failure = runCatching { manager.restore("bad.zip") }.exceptionOrNull()
        assertTrue(failure is InvalidBackupException)
        assertTrue(events.isEmpty())

        storage.files["good.zip"] = validBytes(20)
        manager.restore("good.zip")
        assertEquals(listOf("snapshot", "restore"), events)
        assertTrue(storage.files.keys.any { "-safety-" in it })
    }

    @Test
    fun manualBackupKeepsLegacyFilenamePrefixAfterBrandRename() = runTest {
        val storage = FakeBackupStorage()
        val instant = Instant.parse("2026-08-21T12:34:56Z")
        val manager = BackupManager(
            storage = storage,
            snapshot = { bundle(instant.toEpochMilli()) },
            nowEpochMs = { instant.toEpochMilli() },
        )

        val name = manager.createManual()

        assertEquals("LocalMusicPlayer-manual-20260821-123456.zip", name)
        assertTrue(name in storage.files)
    }

    @Test
    fun manualBackupUsesZipSuffixedTemporaryNameForDocumentProviders() = runTest {
        val storage = ZipExtensionAddingBackupStorage()
        val instant = Instant.parse("2026-08-30T19:20:37Z")
        val manager = BackupManager(
            storage = storage,
            snapshot = { bundle(instant.toEpochMilli()) },
            nowEpochMs = { instant.toEpochMilli() },
        )

        val name = manager.createManual()

        assertEquals("LocalMusicPlayer-manual-20260830-192037.zip", name)
        assertEquals(listOf(name), storage.files.keys.toList())
    }

    @Test
    fun temporaryZipFilesAreNotShownAsRestorableBackups() = runTest {
        val storage = FakeBackupStorage().apply {
            files["LocalMusicPlayer-manual-complete.zip"] = validBytes(1)
            files["LocalMusicPlayer-manual-interrupted.tmp.zip"] = validBytes(2)
            files["LocalMusicPlayer-manual-legacy.zip.tmp.zip"] = validBytes(3)
        }
        val manager = BackupManager(storage, { bundle(4) })

        assertEquals(
            listOf("LocalMusicPlayer-manual-complete.zip"),
            manager.listBackups(),
        )
    }

    private fun bundle(now: Long) = BackupBundle(
        BackupManifest(createdAtEpochMs = now, appVersion = "test"),
        BackupUserData(settings = mapOf("theme" to "dark")),
    )

    private fun validBytes(now: Long) = BackupCodec.encode(bundle(now))
}

private class FakeBackupStorage : BackupStorage {
    val files = linkedMapOf<String, ByteArray>()

    override suspend fun listNames(): List<String> = files.keys.toList()
    override suspend fun read(name: String): ByteArray = files.getValue(name)
    override suspend fun write(name: String, bytes: ByteArray) { files[name] = bytes }
    override suspend fun delete(name: String) { files.remove(name) }
    override suspend fun promote(temporaryName: String, finalName: String) {
        files[finalName] = files.remove(temporaryName) ?: error("missing temporary backup")
    }
}

/** Matches providers that append .zip when an application/zip display name lacks that suffix. */
private class ZipExtensionAddingBackupStorage : BackupStorage {
    val files = linkedMapOf<String, ByteArray>()

    override suspend fun listNames(): List<String> = files.keys.toList()
    override suspend fun read(name: String): ByteArray = files[name]
        ?: throw InvalidBackupException("Backup not found: $name")
    override suspend fun write(name: String, bytes: ByteArray) {
        val providerName = if (name.endsWith(".zip")) name else "$name.zip"
        files[providerName] = bytes
    }
    override suspend fun delete(name: String) { files.remove(name) }
    override suspend fun promote(temporaryName: String, finalName: String) {
        files[finalName] = files.remove(temporaryName) ?: error("missing temporary backup")
    }
}
