package com.example.glasslauncher.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Big sheets (drawer, settings) use a frosted copy of the wallpaper that is blurred ONCE,
 * instead of a live blur that would run on every frame. Same look, almost zero cost.
 */
object Frost {

    /** Default background: soft color blobs on a deep gradient. */
    fun aurora(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(max(w, 8), max(h, 8), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bw = bmp.width.toFloat()
        val bh = bmp.height.toFloat()
        c.drawRect(0f, 0f, bw, bh, Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, bh,
                intArrayOf(0xFF0B0B2E.toInt(), 0xFF1A0F3D.toInt(), 0xFF07141F.toInt()),
                null, Shader.TileMode.CLAMP,
            )
        })
        val blobs = listOf(
            Triple(0xFFE85DFF.toInt(), 0.22f, 0.20f),
            Triple(0xFF3FD5FF.toInt(), 0.85f, 0.38f),
            Triple(0xFFFF8A3D.toInt(), 0.30f, 0.70f),
            Triple(0xFF5BFFB0.toInt(), 0.85f, 0.88f),
        )
        val radius = minOf(bw, bh) * 0.6f
        blobs.forEach { (color, bx, by) ->
            val cx = bw * bx
            val cy = bh * by
            val inner = (color and 0x00FFFFFF) or (0xBF shl 24)
            c.drawCircle(cx, cy, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(cx, cy, radius, inner, 0x00000000, Shader.TileMode.CLAMP)
            })
        }
        return bmp
    }

    /** Small, blurred, extra-saturated copy of [src] as it appears on a screenW × screenH screen. */
    fun make(src: Bitmap, screenW: Int, screenH: Int): Bitmap {
        val sw = max(8, screenW / 12)
        val sh = max(8, screenH / 12)
        val small = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
        val scale = max(sw / src.width.toFloat(), sh / src.height.toFloat())
        val dw = src.width * scale
        val dh = src.height * scale
        val left = (sw - dw) / 2f
        val top = (sh - dh) / 2f
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.5f) })
        }
        val source = if (src.config == Bitmap.Config.HARDWARE) src.copy(Bitmap.Config.ARGB_8888, false) else src
        Canvas(small).drawBitmap(source, null, RectF(left, top, left + dw, top + dh), paint)
        boxBlur(small, radius = 2, passes = 3)
        return small
    }

    private fun boxBlur(bmp: Bitmap, radius: Int, passes: Int) {
        val w = bmp.width
        val h = bmp.height
        var src = IntArray(w * h)
        bmp.getPixels(src, 0, w, 0, 0, w, h)
        var dst = IntArray(w * h)
        repeat(passes) {
            blurPass(src, dst, w, h, radius, horizontal = true)
            var t = src; src = dst; dst = t
            blurPass(src, dst, w, h, radius, horizontal = false)
            t = src; src = dst; dst = t
        }
        bmp.setPixels(src, 0, w, 0, 0, w, h)
    }

    private fun blurPass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        for (line in 0 until lines) {
            for (i in 0 until len) {
                var a = 0; var red = 0; var g = 0; var b = 0
                for (k in -r..r) {
                    val j = (i + k).coerceIn(0, len - 1)
                    val p = src[if (horizontal) line * w + j else j * w + line]
                    a += p ushr 24
                    red += (p shr 16) and 0xff
                    g += (p shr 8) and 0xff
                    b += p and 0xff
                }
                val n = 2 * r + 1
                dst[if (horizontal) line * w + i else i * w + line] =
                    ((a / n) shl 24) or ((red / n) shl 16) or ((g / n) shl 8) or (b / n)
            }
        }
    }
}

/** Draws the pre-blurred wallpaper (aligned to the window) plus a dark wash. */
fun Modifier.frosted(frost: ImageBitmap?, dim: Float): Modifier = drawBehind {
    if (frost != null) {
        drawImage(
            frost,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            filterQuality = FilterQuality.Low,
        )
        drawRect(Color.Black.copy(alpha = dim))
    } else {
        drawRect(Color.Black.copy(alpha = 0.75f))
    }
}

/** The light-catching edge of a sheet that slides over the frosted background. */
fun Modifier.sheetPanel(): Modifier {
    val shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp)
    return this
        .background(Color.White.copy(alpha = 0.06f), shape)
        .border(
            1.2.dp,
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.05f), Color.Transparent)),
            shape,
        )
}

/** Cheap frosted control for use inside sheets (no live blur). */
fun Modifier.frostedControl(shape: androidx.compose.ui.graphics.Shape): Modifier = this
    .background(Color.White.copy(alpha = 0.14f), shape)
    .border(
        1.dp,
        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.3f))),
        shape,
    )
