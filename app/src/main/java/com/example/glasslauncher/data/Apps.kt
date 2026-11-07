package com.example.glasslauncher.data

import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette

data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
    /** The app's normal icon. */
    val icon: ImageBitmap,
    /** White symbol (or foreground art) that sits inside the glass tile. */
    val glyph: ImageBitmap,
    /** Color picked automatically from the app's icon. */
    val autoColor: Int,
) {
    val key: String get() = "$packageName/$activityName"
}

data class ShortcutItem(
    val info: ShortcutInfo,
    val label: String,
    val icon: ImageBitmap?,
)

/** Turns any app icon into the pieces needed for a liquid-glass icon. */
object IconFactory {

    fun original(drawable: Drawable, px: Int): Bitmap =
        drawable.toBitmap(px, px, Bitmap.Config.ARGB_8888)

    fun glyph(drawable: Drawable, px: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        if (drawable is AdaptiveIconDrawable) {
            // Android 13 themed-icon layer: a clean single-color symbol. Perfect for glass.
            val mono = drawable.monochrome?.constantState?.newDrawable()?.mutate()
            if (mono != null) {
                mono.colorFilter = PorterDuffColorFilter(android.graphics.Color.WHITE, PorterDuff.Mode.SRC_IN)
                drawLayer(mono, canvas, px)
                return bitmap
            }
            // No themed icon: use the colored foreground art without its background.
            val fg = drawable.foreground?.constantState?.newDrawable()?.mutate() ?: drawable.foreground
            if (fg != null) {
                drawLayer(fg, canvas, px)
                return bitmap
            }
        }

        // Old-style icon: shrink it a bit so it sits inside the glass.
        val copy = drawable.constantState?.newDrawable()?.mutate() ?: drawable
        val inset = (px * 0.18f).toInt()
        copy.setBounds(inset, inset, px - inset, px - inset)
        copy.draw(canvas)
        return bitmap
    }

    /** Adaptive layers are 108 units with the art in the middle; scale so the art fills ~60%. */
    private fun drawLayer(layer: Drawable, canvas: Canvas, px: Int) {
        val pad = px / 6
        layer.setBounds(-pad, -pad, px + pad, px + pad)
        layer.draw(canvas)
    }

    fun autoColor(bitmap: Bitmap): Int {
        val fallback = 0xFF5C8DFF.toInt()
        return runCatching {
            val p = Palette.from(bitmap).maximumColorCount(16).generate()
            p.getVibrantColor(p.getLightVibrantColor(p.getDominantColor(fallback)))
        }.getOrDefault(fallback)
    }
}
