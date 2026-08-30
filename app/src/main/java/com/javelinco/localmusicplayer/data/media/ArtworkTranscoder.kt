package com.javelinco.localmusicplayer.data.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

object ArtworkTranscoder {
    fun downsample(
        bytes: ByteArray,
        maxDimension: Int = 1024,
        maxInputBytes: Int = 12 * 1024 * 1024,
    ): ByteArray? = runCatching {
        if (bytes.isEmpty() || bytes.size > maxInputBytes || maxDimension <= 0 || !hasSupportedHeader(bytes)) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxDimension || bounds.outHeight / (sample * 2) >= maxDimension) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val longest = maxOf(decoded.width, decoded.height)
        val outputBitmap = if (longest > maxDimension) {
            val scale = maxDimension.toFloat() / longest
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt().coerceAtLeast(1), (decoded.height * scale).toInt().coerceAtLeast(1), true)
                .also { decoded.recycle() }
        } else decoded
        ByteArrayOutputStream().use { output ->
            var success = outputBitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 86, output)
            if (!success || output.size() == 0) {
                output.reset()
                success = outputBitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)
            }
            outputBitmap.recycle()
            output.toByteArray().takeIf { success && it.isNotEmpty() }
        }
    }.getOrNull()

    private fun hasSupportedHeader(bytes: ByteArray): Boolean {
        val jpeg = bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte()
        val png = bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a))
        val webp = bytes.size >= 12 && bytes.copyOfRange(0, 4).decodeToString() == "RIFF" && bytes.copyOfRange(8, 12).decodeToString() == "WEBP"
        return jpeg || png || webp
    }
}
