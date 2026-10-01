package com.pirxhio.affirmity.ui.collections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pirxhio.affirmity.ui.theme.GroupCoverGround
import com.pirxhio.affirmity.ui.theme.GroupCoverOnGround
import com.pirxhio.affirmity.ui.theme.GroupHighlight

/**
 * Square group cover: a dark card with the hand-drawn highlight blob (two overlapping filled
 * shapes at 0.42 / 0.26 alpha, as in design 4a) behind the group name. The design roughens the edge
 * with an SVG turbulence filter; the slightly irregular Béziers below stand in for it.
 *
 * [name] may be null for a cover that only shows the highlight (the detail sheet header).
 */
@Composable
internal fun GroupCover(
    name: String?,
    highlight: GroupHighlight,
    size: Dp,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    nameSize: TextUnit = 18.sp,
    border: BorderStroke? = null,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            // Width is fixed and height is a minimum, so a long name at a large font scale grows the
            // tile instead of clipping mid-line; at default scale it stays square.
            .width(size)
            .heightIn(min = size)
            .clip(shape)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .drawBehind { drawHighlightBlobs(highlight.color) },
        contentAlignment = Alignment.BottomStart,
    ) {
        if (!name.isNullOrEmpty()) {
            Text(
                text = name,
                color = GroupCoverOnGround,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = nameSize,
                lineHeight = nameSize * 1.1f,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

private const val BlobPrimaryAlpha = 0.42f
private const val BlobSecondaryAlpha = 0.26f
private const val BlobViewBox = 100f

// Paths from design 7a, in its 100x100 viewBox.
private val UpperBlob: Path.() -> Unit = {
    moveTo(3f, 9f)
    cubicTo(22f, 4f, 60f, 7f, 97f, 5f)
    cubicTo(99f, 30f, 95f, 62f, 98f, 93f)
    cubicTo(70f, 97f, 30f, 94f, 4f, 96f)
    cubicTo(1f, 70f, 5f, 38f, 3f, 9f)
}
private val LowerBlob: Path.() -> Unit = {
    moveTo(6f, 56f)
    cubicTo(34f, 50f, 68f, 58f, 95f, 52f)
    cubicTo(97f, 66f, 94f, 80f, 96f, 91f)
    cubicTo(66f, 95f, 34f, 90f, 5f, 93f)
    cubicTo(7f, 80f, 4f, 68f, 6f, 56f)
}

private fun DrawScope.drawHighlightBlobs(color: Color) {
    drawRect(GroupCoverGround)
    drawPath(blobPath(size, UpperBlob), color.copy(alpha = BlobPrimaryAlpha))
    drawPath(blobPath(size, LowerBlob), color.copy(alpha = BlobSecondaryAlpha))
}

/** Builds a path in 0..100 design units and scales it to [size]. */
private fun blobPath(size: Size, build: Path.() -> Unit): Path =
    Path().apply {
        build()
        close()
        transform(Matrix().apply { scale(size.width / BlobViewBox, size.height / BlobViewBox) })
    }
