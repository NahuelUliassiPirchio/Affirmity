package com.pirxhio.affirmity.ui.affirmations

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import androidx.core.content.FileProvider
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.data.Affirmation
import com.pirxhio.affirmity.data.AffirmationBackground
import com.pirxhio.affirmity.data.AffirmationTemplateParser
import com.pirxhio.affirmity.data.TemplateField
import com.pirxhio.affirmity.data.backgroundColor
import java.io.File
import java.io.FileOutputStream

/** Dark gradient over the card background; single source of truth for the card and the share image. */
val AffirmationScrimColors: List<Color> = listOf(
    Color.Black.copy(alpha = 0.6f),
    Color.Black.copy(alpha = 0.2f),
    Color.Black.copy(alpha = 0.8f),
)

/** Title/subtitle with token overrides resolved, as the card shows them. */
fun renderAffirmationText(affirmation: Affirmation): Pair<String, String> {
    val title = AffirmationTemplateParser.parse(TemplateField.TITLE, affirmation.title)
        .render(affirmation.overrides)
    val subtitle = AffirmationTemplateParser.parse(TemplateField.SUBTITLE, affirmation.subtitle)
        .render(affirmation.overrides)
    return title to subtitle
}

private const val WIDTH = 1080
private const val HEIGHT = 1920
private const val SIDE_PADDING = 96
private const val MAX_TITLE_HEIGHT = 820
private const val MAX_SUBTITLE_HEIGHT = 420

/**
 * Renders the affirmation to a fixed 1080x1920 PNG. Deliberately drawn with android.graphics
 * (not a captured Compose tree): it needs no window/composition, so it is deterministic, runs
 * off the main thread and cannot capture a half-loaded frame. The visual spec (background,
 * shared scrim, icon, serif title, divider, subtitle) mirrors the on-screen card. Blocking; call from IO.
 */
fun renderAffirmationShareImage(
    context: Context,
    affirmation: Affirmation,
    icon: ImageVector,
    iconTint: Int,
    appName: String,
    target: File,
) {
    val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
    try {
        val canvas = Canvas(bitmap)
        drawBackground(canvas, affirmation)
        val scrim = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, HEIGHT.toFloat(),
                AffirmationScrimColors.map { it.toArgb() }.toIntArray(), null, Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), scrim)
        drawContent(canvas, affirmation, icon, iconTint)
        drawFooter(canvas, context, appName)
        FileOutputStream(target).use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "PNG compression failed" }
        }
    } finally {
        bitmap.recycle()
    }
}

private fun drawBackground(canvas: Canvas, affirmation: Affirmation) {
    canvas.drawColor(affirmation.backgroundColor().toArgb())
    val bg = affirmation.background as? AffirmationBackground.Image ?: return
    val image = decodeSampled(bg.localPath) ?: return // decode failure keeps the color fallback
    try {
        val scale = maxOf(WIDTH / image.width.toFloat(), HEIGHT / image.height.toFloat())
        val srcW = (WIDTH / scale).toInt().coerceAtMost(image.width)
        val srcH = (HEIGHT / scale).toInt().coerceAtMost(image.height)
        val src = Rect(
            (image.width - srcW) / 2, (image.height - srcH) / 2,
            (image.width + srcW) / 2, (image.height + srcH) / 2,
        )
        canvas.drawBitmap(image, src, Rect(0, 0, WIDTH, HEIGHT), Paint(Paint.FILTER_BITMAP_FLAG))
    } finally {
        image.recycle()
    }
}

private fun decodeSampled(path: String): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= WIDTH && bounds.outHeight / (sample * 2) >= HEIGHT) sample *= 2
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}.getOrNull()

private fun textLayout(text: String, paint: TextPaint): StaticLayout =
    StaticLayout.Builder.obtain(text, 0, text.length, paint, WIDTH - 2 * SIDE_PADDING)
        .setAlignment(Layout.Alignment.ALIGN_CENTER)
        .setLineSpacing(0f, 1.1f)
        .build()

/** Largest size whose layout fits [maxHeight]; the smallest size is used as-is (clipped by nothing, just tiny). */
private fun fitLayout(text: String, paint: TextPaint, sizes: List<Float>, maxHeight: Int): StaticLayout {
    val size = fitTextSize(sizes) { s ->
        paint.textSize = s
        textLayout(text, paint).height <= maxHeight
    }
    paint.textSize = size
    return textLayout(text, paint)
}

private fun drawContent(canvas: Canvas, affirmation: Affirmation, icon: ImageVector, iconTint: Int) {
    val (title, subtitle) = renderAffirmationText(affirmation)
    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }
    val titleLayout = fitLayout(title, titlePaint, shareTextSizeCandidates(96f, 44f, 4f), MAX_TITLE_HEIGHT)
    val subtitleLayout = if (subtitle.isNotBlank()) {
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFCCCCCC.toInt()
            typeface = Typeface.SANS_SERIF
        }
        fitLayout(subtitle, p, shareTextSizeCandidates(52f, 28f, 2f), MAX_SUBTITLE_HEIGHT)
    } else null

    val iconSize = 96
    val gap = 40
    val dividerBlock = if (subtitleLayout != null) 48 + 4 + 48 else 0
    val total = iconSize + gap + titleLayout.height + dividerBlock + (subtitleLayout?.height ?: 0)
    var y = (HEIGHT - 200 - total) / 2f // footer reserves the bottom 200px

    drawIcon(canvas, icon, iconTint, (WIDTH - iconSize) / 2f, y, iconSize.toFloat())
    y += iconSize + gap
    canvas.save(); canvas.translate(SIDE_PADDING.toFloat(), y); titleLayout.draw(canvas); canvas.restore()
    y += titleLayout.height
    if (subtitleLayout != null) {
        y += 48
        val divider = Paint().apply { color = (iconTint and 0x00FFFFFF) or (0x80 shl 24) }
        canvas.drawRect((WIDTH - 96) / 2f, y, (WIDTH + 96) / 2f, y + 4f, divider)
        y += 4 + 48
        canvas.save(); canvas.translate(SIDE_PADDING.toFloat(), y); subtitleLayout.draw(canvas); canvas.restore()
    }
}

/** Fills the (flat, single-color) Material icon paths scaled from their viewport into [size]. */
private fun drawIcon(canvas: Canvas, icon: ImageVector, tint: Int, x: Float, y: Float, size: Float) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint }
    canvas.save()
    canvas.translate(x, y)
    canvas.scale(size / icon.viewportWidth, size / icon.viewportHeight)
    fun walk(group: VectorGroup) {
        group.forEach { node ->
            when (node) {
                is VectorPath -> canvas.drawPath(node.pathData.toPath().asAndroidPath(), paint)
                is VectorGroup -> walk(node)
            }
        }
    }
    walk(icon.root)
    canvas.restore()
}

private fun drawFooter(canvas: Canvas, context: Context, appName: String) {
    val logoSize = 72
    val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        alpha = 179
        textSize = 40f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        letterSpacing = 0.08f
    }
    val nameWidth = namePaint.measureText(appName)
    val gap = 20f
    val startX = (WIDTH - (logoSize + gap + nameWidth)) / 2f
    val top = HEIGHT - 140f
    // Adaptive-icon foreground is 108dp with only the central 72dp guaranteed visible: crop the
    // safe zone (inset 1/6 per side) so the logo is not surrounded by transparent padding.
    val logo = runCatching {
        BitmapFactory.decodeResource(context.resources, R.drawable.ic_launcher_foreground)
    }.getOrNull()
    if (logo != null) {
        val inset = logo.width / 6
        val src = Rect(inset, inset, logo.width - inset, logo.height - inset)
        val dst = Rect(startX.toInt(), top.toInt(), startX.toInt() + logoSize, top.toInt() + logoSize)
        canvas.drawBitmap(logo, src, dst, Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 200 })
        logo.recycle()
    }
    val baseline = top + logoSize / 2f - (namePaint.ascent() + namePaint.descent()) / 2f
    canvas.drawText(appName, startX + logoSize + gap, baseline, namePaint)
}

/** Chooser intent for a PNG written under [ShareImageCache.SUBDIR]. */
fun buildShareImageIntent(context: Context, file: File): Intent {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return Intent.createChooser(send, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
