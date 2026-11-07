package com.example.glasslauncher.data

import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette

/** Raw app data, loaded once per package. */
class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
    /** The app's normal icon. */
    val icon: Bitmap,
    /** Light symbol that sits inside the glass tile. */
    val glyph: Bitmap,
    /** Color picked automatically from the app's icon. */
    val autoColor: Int,
    val installTime: Long = 0L,
) {
    val key: String = "$packageName/$activityName"
}

/** What the UI draws: label + a ready-made icon bitmap. Cheap to draw, never re-rendered per frame. */
@Immutable
data class UiApp(
    val key: String,
    val app: AppInfo,
    val label: String,
    val icon: ImageBitmap,
)

data class ShortcutItem(
    val info: ShortcutInfo,
    val label: String,
    val icon: ImageBitmap?,
)

object IconFactory {

    const val PX = 192

    fun original(drawable: Drawable): Bitmap = drawable.toBitmap(PX, PX, Bitmap.Config.ARGB_8888)

    /** Turns colored logo art into light, slightly see-through shades (keeps the details). */
    private val lightFilter = ColorMatrixColorFilter(
        ColorMatrix(
            floatArrayOf(
                0.18f, 0.36f, 0.07f, 0f, 125f,
                0.18f, 0.36f, 0.07f, 0f, 125f,
                0.18f, 0.36f, 0.07f, 0f, 125f,
                0f, 0f, 0f, 0.95f, 0f,
            )
        )
    )

    /**
     * Builds the symbol that sits inside the glass tile. Tries, in order:
     *  1. the Android 13 themed-icon layer (if it's a real symbol, not a filled square)
     *  2. the foreground logo (if it sits on a transparent background)
     *  3. a light "engraving" made from the artwork's light/dark areas (photo-style icons)
     *  4. the app's first letter
     */
    fun glyph(drawable: Drawable, original: Bitmap, label: String): Bitmap {
        if (drawable is AdaptiveIconDrawable) {
            drawable.monochrome?.constantState?.newDrawable()?.mutate()?.let { mono ->
                mono.colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
                val bmp = renderLayer(mono)
                if (coverage(bmp) in 0.015f..0.5f) return bmp
            }
            drawable.foreground?.constantState?.newDrawable()?.mutate()?.let { fg ->
                fg.colorFilter = lightFilter
                val bmp = renderLayer(fg)
                if (coverage(bmp) in 0.015f..0.42f) return bmp
            }
            return fromArtwork(original) ?: letter(label)
        }

        // Old-style icon
        val cov = coverage(original)
        if (cov in 0.015f..0.42f) {
            val bmp = Bitmap.createBitmap(PX, PX, Bitmap.Config.ARGB_8888)
            val inset = PX * 0.18f
            Canvas(bmp).drawBitmap(
                original, null, RectF(inset, inset, PX - inset, PX - inset),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { colorFilter = lightFilter },
            )
            return bmp
        }
        return fromArtwork(original) ?: letter(label)
    }

    /** Adaptive layers are 108 units with the art in the middle; scale so the art fills ~60%. */
    private fun renderLayer(layer: Drawable): Bitmap {
        val bmp = Bitmap.createBitmap(PX, PX, Bitmap.Config.ARGB_8888)
        val pad = PX / 6
        layer.setBounds(-pad, -pad, PX + pad, PX + pad)
        layer.draw(Canvas(bmp))
        return bmp
    }

    /** Share of pixels that are clearly visible. A filled square is ~1.0, a logo ~0.1–0.4. */
    private fun coverage(bmp: Bitmap): Float {
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        var n = 0
        for (p in px) if ((p ushr 24) > 100) n++
        return n.toFloat() / px.size
    }

    /**
     * For full-picture icons: map brightness to opacity, so the picture becomes
     * white highlights "engraved" in the glass. Light icons are inverted (dark parts show).
     */
    private fun fromArtwork(art: Bitmap): Bitmap? {
        val w = art.width
        val h = art.height
        val px = IntArray(w * h)
        art.getPixels(px, 0, w, 0, 0, w, h)

        val hist = IntArray(256)
        var count = 0
        var sum = 0L
        for (p in px) {
            if ((p ushr 24) < 128) continue
            val l = lum(p)
            hist[l]++
            sum += l
            count++
        }
        if (count < 64) return null
        val lo = percentile(hist, count, 0.08f)
        val hi = percentile(hist, count, 0.92f)
        if (hi - lo < 28) return null
        val invert = sum / count > 150

        val out = IntArray(w * h)
        val range = (hi - lo).toFloat()
        for (i in px.indices) {
            val p = px[i]
            val a = p ushr 24
            if (a == 0) continue
            var t = ((lum(p) - lo) / range).coerceIn(0f, 1f)
            if (invert) t = 1f - t
            t = t * t * (3f - 2f * t)
            val alpha = (a * t * 0.95f).toInt().coerceIn(0, 255)
            out[i] = (alpha shl 24) or 0x00FFFFFF
        }
        val full = Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
        // Shrink into the middle of the tile
        val bmp = Bitmap.createBitmap(PX, PX, Bitmap.Config.ARGB_8888)
        val inset = PX * 0.14f
        Canvas(bmp).drawBitmap(
            full, null, RectF(inset, inset, PX - inset, PX - inset),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
        return bmp
    }

    private fun lum(p: Int): Int =
        (((p shr 16) and 0xff) * 77 + ((p shr 8) and 0xff) * 150 + (p and 0xff) * 29) shr 8

    private fun percentile(hist: IntArray, count: Int, q: Float): Int {
        val target = (count * q).toInt()
        var acc = 0
        for (i in 0..255) {
            acc += hist[i]
            if (acc >= target) return i
        }
        return 255
    }

    /** Last resort: the app's first letter. */
    private fun letter(label: String): Bitmap {
        val bmp = Bitmap.createBitmap(PX, PX, Bitmap.Config.ARGB_8888)
        val ch = label.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "•"
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = PX * 0.44f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val y = PX / 2f - (paint.descent() + paint.ascent()) / 2f
        Canvas(bmp).drawText(ch, PX / 2f, y, paint)
        return bmp
    }

    fun autoColor(bitmap: Bitmap): Int {
        val fallback = 0xFF5C8DFF.toInt()
        return runCatching {
            val p = Palette.from(bitmap).maximumColorCount(12).resizeBitmapArea(48 * 48).generate()
            p.getVibrantColor(p.getLightVibrantColor(p.getDominantColor(fallback)))
        }.getOrDefault(fallback)
    }
}

/**
 * Draws a liquid-glass icon ONCE into a bitmap: translucent tinted body, inner glow,
 * top gloss, bottom shade, specular rim, soft shadow and the symbol.
 * Drawing a bitmap is nearly free, so the home screen and drawer stay fast.
 */
object IconBaker {

    private const val OUT = IconFactory.PX

    /** The tile outline for each icon shape. */
    fun shapePath(shape: IconShape, rect: RectF): Path {
        val w = rect.width()
        return Path().apply {
            when (shape) {
                IconShape.CIRCLE -> addOval(rect, Path.Direction.CW)
                IconShape.ROUNDED -> addRoundRect(rect, w * 0.22f, w * 0.22f, Path.Direction.CW)
                IconShape.SQUARE -> addRoundRect(rect, w * 0.10f, w * 0.10f, Path.Direction.CW)
                IconShape.TEARDROP -> {
                    val r = w * 0.5f
                    val s = w * 0.14f
                    addRoundRect(rect, floatArrayOf(r, r, r, r, s, s, r, r), Path.Direction.CW)
                }
                IconShape.SQUIRCLE -> {
                    // Superellipse (n = 5): the smooth "continuous corner" shape
                    val cx = rect.centerX()
                    val cy = rect.centerY()
                    val a = w / 2f
                    val n = 5.0
                    val steps = 96
                    for (i in 0..steps) {
                        val t = 2.0 * Math.PI * i / steps
                        val c = Math.cos(t)
                        val sn = Math.sin(t)
                        val x = cx + a * Math.signum(c) * Math.pow(Math.abs(c), 2.0 / n)
                        val y = cy + a * Math.signum(sn) * Math.pow(Math.abs(sn), 2.0 / n)
                        if (i == 0) moveTo(x.toFloat(), y.toFloat()) else lineTo(x.toFloat(), y.toFloat())
                    }
                    close()
                }
            }
        }
    }

    fun bake(glyph: Bitmap, tint: Int?, shape: IconShape = IconShape.SQUIRCLE): Bitmap {
        val out = OUT
        val bmp = Bitmap.createBitmap(out, out, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val pad = out * 0.07f
        val rect = RectF(pad, pad, out - pad, out - pad)
        val w = rect.width()
        val h = rect.height()
        val path = shapePath(shape, rect)

        // 1) Soft outer shadow only (punch out the inside so it doesn't darken the glass)
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            setShadowLayer(out * 0.04f, 0f, out * 0.02f, Color.argb(90, 0, 0, 0))
        }
        c.drawPath(path, shadow)
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) })

        // 2) Translucent tinted body
        val base = tint ?: Color.WHITE
        val clear = tint == null
        val top = mix(base, Color.WHITE, 0.35f)
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, rect.top, 0f, rect.bottom,
                withAlpha(top, if (clear) 80 else 215),
                withAlpha(base, if (clear) 40 else 175),
                Shader.TileMode.CLAMP,
            )
        }
        c.drawPath(path, body)

        c.save()
        c.clipPath(path)
        // 3) Inner glow, top-left
        val gx = rect.left + w * 0.28f
        val gy = rect.top + h * 0.12f
        c.drawCircle(gx, gy, w * 0.8f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(gx, gy, w * 0.8f, Color.argb(95, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        })
        // 4) Gloss band on the top half
        c.drawRect(rect.left, rect.top, rect.right, rect.top + h * 0.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, rect.top, 0f, rect.top + h * 0.5f,
                Color.argb(70, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        })
        // 5) Slight shade at the bottom for depth
        c.drawRect(rect.left, rect.bottom - h * 0.35f, rect.right, rect.bottom, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, rect.bottom - h * 0.35f, 0f, rect.bottom,
                Color.TRANSPARENT, Color.argb(45, 0, 0, 0), Shader.TileMode.CLAMP)
        })
        c.restore()

        // 6) The symbol, with a soft shadow so it pops off the glass
        val gp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            setShadowLayer(out * 0.025f, 0f, out * 0.012f, Color.argb(90, 0, 0, 0))
        }
        c.save()
        c.clipPath(path)
        c.drawBitmap(glyph, null, Rect(0, 0, out, out), gp)
        c.restore()

        // 7) Specular rim: bright top-left and bottom-right edges
        val stroke = out * 0.013f
        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            shader = LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                intArrayOf(
                    Color.argb(235, 255, 255, 255),
                    Color.argb(40, 255, 255, 255),
                    Color.argb(25, 255, 255, 255),
                    Color.argb(150, 255, 255, 255),
                ),
                floatArrayOf(0f, 0.42f, 0.62f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        val inner = RectF(rect).apply { inset(stroke / 2, stroke / 2) }
        c.drawPath(shapePath(shape, inner), rim)

        // Move it to GPU memory: drawing it then costs nothing on the CPU.
        return bmp.copy(Bitmap.Config.HARDWARE, false) ?: bmp.also { it.prepareToDraw() }
    }

    private fun withAlpha(color: Int, alpha: Int) =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private fun mix(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
    )
}
