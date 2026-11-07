package com.example.glasslauncher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale

/**
 * What sits behind the glass: the user's picture or the default aurora (both plain bitmaps).
 * It never animates, so the glass surfaces don't need to redraw while idle.
 */
@Composable
fun LauncherBackground(image: ImageBitmap?, modifier: Modifier = Modifier) {
    if (image != null) {
        Image(bitmap = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
    } else {
        Box(modifier.background(Color(0xFF0B0B2E)))
    }
}
