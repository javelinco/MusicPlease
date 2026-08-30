package com.javelinco.localmusicplayer.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LocalArtwork(
    path: String?,
    description: String?,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    cornerRadius: Dp = 14.dp,
) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, path) {
        value = path?.let { withContext(Dispatchers.IO) { BitmapFactory.decodeFile(it) } }
    }
    val styled = modifier.size(size).clip(RoundedCornerShape(cornerRadius)).testTag("local-artwork")
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = description,
            contentScale = ContentScale.Crop,
            modifier = styled,
        )
    } else {
        Box(styled.background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.MusicNote, "Artwork unavailable", tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}
