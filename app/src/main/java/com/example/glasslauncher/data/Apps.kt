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

    /** Turns any colored art into light, slightly see-through shades (keeps the details). */
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

    fun glyph(drawable: Drawable): Bitmap {
        val px = PX
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        if (drawable is AdaptiveIconDrawable) {
            // Android 13 themed-icon layer: a clean single-color symbol. Best case.
            val mono = drawable.monochrome?.constantState?.newDrawable()?.mutate()
            if (mono != null) {
                mono.colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
                drawLayer(mono, canvas, px)
                return bitmap
            }
            // No themed icon: take the foreground art and turn it light so it matches the rest.
            val fg = drawable.foreground?.constantState?.newDrawable()?.mutate()
            if (fg != null) {
                fg.colorFilter = lightFilter
                drawLayer(fg, canvas, px)
                return bitmap
            }
        }

        // Old-style icon: shrink it and make it light too.
        val copy = drawable.constantState?.newDrawable()?.mutate() ?: drawable.mutate()
        copy.colorFilter = lightFilter
        val inset = (px * 0.2f).toInt()
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

    fun bake(glyph: Bitmap, tint: Int?): Bitmap {
        val out = OUT
        val bmp = Bitmap.createBitmap(out, out, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val pad = out * 0.07f
        val rect = RectF(pad, pad, out - pad, out - pad)
        val w = rect.width()
        val h = rect.height()
        val radius = w * 0.30f
        val path = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }

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
        c.drawBitmap(glyph, null, Rect(0, 0, out, out), gp)

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
        c.drawRoundRect(inner, radius - stroke / 2, radius - stroke / 2, rim)

        bmp.prepareToDraw()
        return bmp
    }

    private fun withAlpha(color: Int, alpha: Int) =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private fun mix(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
    )
}
