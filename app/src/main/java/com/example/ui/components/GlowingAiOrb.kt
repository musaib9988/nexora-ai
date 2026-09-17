package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.VioletSecondary
import kotlin.math.cos
import kotlin.math.sin

enum class OrbState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}

@Composable
fun GlowingAiOrb(
    state: OrbState,
    audioLevel: Float = 0f,
    size: Dp = 220.dp,
    onClick: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "OrbTransitions")

    // Rotation angle
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (state == OrbState.THINKING) 2000 else 8000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "Rotation"
    )

    // Breathing pulse
    val breathingPulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    OrbState.THINKING -> 800
                    OrbState.LISTENING -> 600
                    OrbState.SPEAKING -> 700
                    else -> 2400
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Breathing"
    )

    // Outer glow expander
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowAlpha"
    )

    val reactiveScale = if (state == OrbState.LISTENING) {
        (breathingPulse + (audioLevel * 0.4f)).coerceIn(0.85f, 1.45f)
    } else {
        breathingPulse
    }

    Box(
        modifier = Modifier
            .size(size)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val baseRadius = (this.size.minDimension / 2f) * 0.75f
            val currentRadius = baseRadius * reactiveScale

            // Outer Soft Halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        CyanPrimary.copy(alpha = if (state == OrbState.LISTENING) 0.5f else glowAlpha * 0.35f),
                        VioletSecondary.copy(alpha = 0.15f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = currentRadius * 1.55f
                ),
                radius = currentRadius * 1.55f,
                center = center
            )

            // Outer Orbit Ring with glowing dashes
            val ringRadius = currentRadius * 1.25f
            drawCircle(
                color = when (state) {
                    OrbState.LISTENING -> CyanPrimary.copy(alpha = 0.85f)
                    OrbState.THINKING -> VioletSecondary.copy(alpha = 0.8f)
                    OrbState.SPEAKING -> ElectricBlue.copy(alpha = 0.75f)
                    OrbState.IDLE -> CyanPrimary.copy(alpha = 0.35f)
                },
                radius = ringRadius,
                center = center,
                style = Stroke(width = 2.5.dp.toPx())
            )

            // Orbiting planetary particles
            val particleCount = if (state == OrbState.THINKING) 8 else 4
            for (i in 0 until particleCount) {
                val angleRad = Math.toRadians((rotation + (i * (360f / particleCount))).toDouble())
                val particleX = center.x + (ringRadius * cos(angleRad)).toFloat()
                val particleY = center.y + (ringRadius * sin(angleRad)).toFloat()

                drawCircle(
                    color = if (i % 2 == 0) CyanGlow else VioletSecondary,
                    radius = 4.5.dp.toPx(),
                    center = Offset(particleX, particleY)
                )
            }

            // Core AI Orb with vibrant Neon Gradient
            drawCircle(
                brush = Brush.radialGradient(
                    colors = when (state) {
                        OrbState.LISTENING -> listOf(
                            Color.White,
                            CyanGlow,
                            CyanPrimary,
                            VioletSecondary
                        )
                        OrbState.THINKING -> listOf(
                            Color.White,
                            VioletSecondary,
                            CyanPrimary,
                            Color(0xFF311B92)
                        )
                        OrbState.SPEAKING -> listOf(
                            Color.White,
                            ElectricBlue,
                            CyanPrimary,
                            VioletSecondary
                        )
                        OrbState.IDLE -> listOf(
                            CyanGlow.copy(alpha = 0.9f),
                            CyanPrimary,
                            VioletSecondary,
                            Color(0xFF0F172A)
                        )
                    },
                    center = center,
                    radius = currentRadius
                ),
                radius = currentRadius,
                center = center
            )

            // Inner Holographic Shimmer / Highlights
            drawCircle(
                color = Color.White.copy(alpha = 0.35f),
                radius = currentRadius * 0.45f,
                center = Offset(center.x - currentRadius * 0.2f, center.y - currentRadius * 0.2f)
            )
        }
    }
}
