package com.pirxhio.affirmity.ui.affirmations

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.util.lerp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** A single independent heart-burst animation anchored in card-local coordinates. */
data class LikeBurst(
    val id: Long,
    val origin: Offset,
    val durationMillis: Int,
    val particles: List<LikeBurstParticle>,
)

data class LikeBurstParticle(
    val angleRadians: Float,
    val distance: Dp,
    val size: Dp,
    val scale: Float,
    val rotation: Float,
    val delayMillis: Int,
    val durationMillis: Int,
    val useLightTint: Boolean,
)

fun createLikeBurst(origin: Offset, random: Random = Random.Default): LikeBurst {
    val durationMillis = random.nextInt(from = 540, until = 651)
    val particleCount = random.nextInt(from = 18, until = 23)
    return LikeBurst(
        id = random.nextLong(),
        origin = origin,
        durationMillis = durationMillis,
        particles = List(particleCount) { index ->
            val angle = (index.toFloat() / particleCount) * (Math.PI * 2).toFloat() +
                random.nextFloat() * 0.22f - 0.11f
            val delayMillis = random.nextInt(from = 0, until = 91)
            val targetDurationMillis = (durationMillis * (random.nextFloat() * 0.3f + 0.7f)).roundToInt()
            LikeBurstParticle(
                angleRadians = angle,
                distance = random.nextInt(from = 90, until = 181).dp,
                size = random.nextInt(from = 10, until = 25).dp,
                scale = random.nextFloat() * 0.7f + 0.55f,
                rotation = random.nextFloat() * 80f - 40f,
                delayMillis = delayMillis,
                durationMillis = minOf(targetDurationMillis, durationMillis - delayMillis),
                useLightTint = random.nextFloat() < 0.2f,
            )
        },
    )
}

@Composable
fun LikeBurstOverlay(
    bursts: List<LikeBurst>,
    onBurstFinished: (id: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        bursts.forEach { burst ->
            LikeBurstAnimation(burst = burst, onFinished = onBurstFinished)
        }
    }
}

@Composable
private fun LikeBurstAnimation(
    burst: LikeBurst,
    onFinished: (Long) -> Unit,
) {
    val progress = remember(burst.id) { Animatable(0f) }
    val density = LocalDensity.current
    val fraction = progress.value
    val primary = MaterialTheme.colorScheme.primary

    LaunchedEffect(burst.id) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = burst.durationMillis, easing = LinearEasing),
        )
        onFinished(burst.id)
    }

    val centerSize = 68.dp
    val centerSizePx = with(density) { centerSize.toPx() }
    val centerOffset = IntOffset(
        (burst.origin.x - centerSizePx / 2f).roundToInt(),
        (burst.origin.y - centerSizePx / 2f).roundToInt(),
    )
    val entrance = (fraction / 0.18f).coerceIn(0f, 1f)
    val settle = ((fraction - 0.18f) / 0.34f).coerceIn(0f, 1f)
    val exit = ((fraction - 0.82f) / 0.18f).coerceIn(0f, 1f)
    val heartScale = when {
        fraction < 0.18f -> lerp(0.15f, 1.16f, FastOutSlowInEasing.transform(entrance))
        fraction < 0.52f -> lerp(1.16f, 1f, FastOutSlowInEasing.transform(settle))
        else -> lerp(1f, 1.06f, FastOutSlowInEasing.transform(exit))
    }
    val heartRotation = when {
        fraction < 0.18f -> lerp(-16f, 8f, FastOutSlowInEasing.transform(entrance))
        fraction < 0.52f -> lerp(8f, 0f, FastOutSlowInEasing.transform(settle))
        else -> lerp(0f, 4f, exit)
    }
    val heartAlpha = minOf(entrance * 1.4f, 1f) * (1f - exit)
    val ringProgress = (fraction / 0.45f).coerceIn(0f, 1f)
    val ringEased = FastOutSlowInEasing.transform(ringProgress)

    Box(modifier = Modifier.offset { centerOffset }.size(centerSize)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = lerp(0.25f, 1f, ringEased)
                    scaleY = lerp(0.25f, 1f, ringEased)
                    alpha = (1f - ringEased) * 0.55f
                }
                .clip(CircleShape)
                .border(width = 2.dp, color = primary, shape = CircleShape),
        )
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = null,
            tint = primary,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = heartScale
                    scaleY = heartScale
                    rotationZ = heartRotation
                    alpha = heartAlpha
                },
        )
    }

    burst.particles.forEach { particle ->
        LikeBurstParticleAnimation(
            burst = burst,
            particle = particle,
            fraction = fraction,
            color = if (particle.useLightTint) Color.White else primary,
        )
    }
}

@Composable
private fun LikeBurstParticleAnimation(
    burst: LikeBurst,
    particle: LikeBurstParticle,
    fraction: Float,
    color: Color,
) {
    val elapsedMillis = fraction * burst.durationMillis
    val particleProgress = ((elapsedMillis - particle.delayMillis) / particle.durationMillis).coerceIn(0f, 1f)
    if (particleProgress == 0f && elapsedMillis < particle.delayMillis) return

    val density = LocalDensity.current
    val eased = FastOutSlowInEasing.transform(particleProgress)
    val alpha = when {
        particleProgress < 0.1f -> particleProgress / 0.1f
        else -> 1f - ((particleProgress - 0.1f) / 0.9f)
    }
    val distance = with(density) { particle.distance.toPx() } * eased
    val particleSizePx = with(density) { particle.size.toPx() }
    val offset = IntOffset(
        (burst.origin.x + cos(particle.angleRadians.toDouble()).toFloat() * distance - particleSizePx / 2f).roundToInt(),
        (burst.origin.y + sin(particle.angleRadians.toDouble()).toFloat() * distance - particleSizePx / 2f).roundToInt(),
    )

    Icon(
        imageVector = Icons.Filled.Favorite,
        contentDescription = null,
        tint = color,
        modifier = Modifier
            .offset { offset }
            .size(particle.size)
            .graphicsLayer {
                val scale = lerp(0.15f, particle.scale, eased)
                scaleX = scale
                scaleY = scale
                rotationZ = particle.rotation * eased
                this.alpha = alpha
            },
    )
}
