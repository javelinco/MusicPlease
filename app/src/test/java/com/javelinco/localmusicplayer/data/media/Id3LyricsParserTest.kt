package com.javelinco.localmusicplayer.data.media

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Id3LyricsParserTest {
    @Test
    fun parsesUtf8UsltAndMillisecondSyltFromId3v23() {
        val tag = id3Tag(
            version = 3,
            frames = listOf(
                frame(3, "USLT", usltPayload("Plain words")),
                frame(3, "SYLT", syltPayload(1_000 to "One", 2_500 to "Two")),
            ),
        )

        val parsed = Id3LyricsParser.parse(tag)

        assertEquals("Plain words", parsed.plain!!.lines.single().text)
        assertEquals(listOf(1_000L, 2_500L), parsed.synchronized!!.lines.map { it.timeMs })
        assertEquals(listOf("One", "Two"), parsed.synchronized!!.lines.map { it.text })
    }

    @Test
    fun parsesSynchsafeFrameSizesFromId3v24() {
        val tag = id3Tag(
            version = 4,
            frames = listOf(frame(4, "USLT", usltPayload("Version four"))),
        )

        assertEquals("Version four", Id3LyricsParser.parse(tag).plain!!.lines.single().text)
    }

    @Test
    fun unsupportedSyltTimestampFormatDoesNotHidePlainLyrics() {
        val unsupportedSylt = syltPayload(1_000 to "Frame count").copyOf().also { it[4] = 1 }
        val parsed = Id3LyricsParser.parse(
            id3Tag(
                version = 3,
                frames = listOf(
                    frame(3, "SYLT", unsupportedSylt),
                    frame(3, "USLT", usltPayload("Still available")),
                ),
            ),
        )

        assertNull(parsed.synchronized)
        assertEquals("Still available", parsed.plain!!.lines.single().text)
    }

    @Test
    fun malformedAndOversizedTagsReturnNoLyrics() {
        assertEquals(EmbeddedLyrics(), Id3LyricsParser.parse(byteArrayOf(1, 2, 3)))
        val oversizedHeader = byteArrayOf(
            'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 3, 0, 0,
            0x02, 0x00, 0x00, 0x01,
        )
        assertEquals(EmbeddedLyrics(), Id3LyricsParser.parse(oversizedHeader))
    }

    private fun usltPayload(text: String): ByteArray = ByteArrayOutputStream().apply {
        write(3)
        write("eng".encodeToByteArray())
        write(0)
        write(text.encodeToByteArray())
    }.toByteArray()

    private fun syltPayload(vararg lines: Pair<Int, String>): ByteArray = ByteArrayOutputStream().apply {
        write(3)
        write("eng".encodeToByteArray())
        write(2)
        write(1)
        write(0)
        lines.forEach { (timeMs, text) ->
            write(text.encodeToByteArray())
            write(0)
            write(int32(timeMs))
        }
    }.toByteArray()

    private fun frame(version: Int, id: String, payload: ByteArray): ByteArray =
        ByteArrayOutputStream().apply {
            write(id.encodeToByteArray())
            write(if (version == 4) synchsafe(payload.size) else int32(payload.size))
            write(byteArrayOf(0, 0))
            write(payload)
        }.toByteArray()

    private fun id3Tag(version: Int, frames: List<ByteArray>): ByteArray {
        val body = frames.fold(ByteArrayOutputStream()) { output, bytes -> output.apply { write(bytes) } }
            .toByteArray()
        return ByteArrayOutputStream().apply {
            write("ID3".encodeToByteArray())
            write(byteArrayOf(version.toByte(), 0, 0))
            write(synchsafe(body.size))
            write(body)
        }.toByteArray()
    }

    private fun int32(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte(),
    )

    private fun synchsafe(value: Int): ByteArray = byteArrayOf(
        (value ushr 21 and 0x7f).toByte(),
        (value ushr 14 and 0x7f).toByte(),
        (value ushr 7 and 0x7f).toByte(),
        (value and 0x7f).toByte(),
    )
}
