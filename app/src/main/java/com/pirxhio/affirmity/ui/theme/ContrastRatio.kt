package com.pirxhio.affirmity.ui.theme

/**
 * Pure WCAG 2.x relative-luminance contrast ratio calculator (bug 2b), extracted so theme color
 * choices are JVM-testable without rendering Compose -- same pattern as `resolveSelectedGroupIds`
 * in `data/AffirmityAppState.kt`.
 *
 * [colorA] and [colorB] are packed `0xAARRGGBB` values (the alpha channel is ignored). Returns a
 * ratio in `[1.0, 21.0]` per the WCAG formula: `(L_lighter + 0.05) / (L_darker + 0.05)`.
 */
fun wcagContrastRatio(colorA: Long, colorB: Long): Double {
    val lighter = maxOf(relativeLuminance(colorA), relativeLuminance(colorB))
    val darker = minOf(relativeLuminance(colorA), relativeLuminance(colorB))
    return (lighter + 0.05) / (darker + 0.05)
}

private fun relativeLuminance(argb: Long): Double {
    val red = ((argb shr 16) and 0xFF).toInt()
    val green = ((argb shr 8) and 0xFF).toInt()
    val blue = (argb and 0xFF).toInt()
    return 0.2126 * linearize(red) + 0.7152 * linearize(green) + 0.0722 * linearize(blue)
}

private fun linearize(channel: Int): Double {
    val c = channel / 255.0
    return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
}
