package com.javelinco.localmusicplayer.data.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ArtworkTranscoderTest {
    @Test
    fun downsamplesLargeArtworkAndRejectsCorruptOrOversizedInput() {
        ApplicationProvider.getApplicationContext<android.content.Context>()
        val bitmap = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888)
        val input = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }.toByteArray()

        val output = ArtworkTranscoder.downsample(input, maxDimension = 512)!!
        val decoded = BitmapFactory.decodeByteArray(output, 0, output.size)

        assertTrue(decoded.width <= 512 && decoded.height <= 512)
        assertNull(ArtworkTranscoder.downsample(byteArrayOf(1, 2, 3)))
        assertNull(ArtworkTranscoder.downsample(ByteArray(100), maxInputBytes = 99))
    }
}
