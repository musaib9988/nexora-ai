package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Nexora Emblem rendered natively with Jetpack Compose Canvas.
 * Accurately replicates the glowing cyan orbital ring with satellite nodes
 * and the 3D folded ribbon 'N'.
 */
@Composable
fun NexoraEmblem(
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
    animated: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "nexora_pulse")
    val rotation by if (animated) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(24000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "orbital_rotation"
        )
    } else {
        rememberUpdatedState(0f)
    }

    val glowAlpha by if (animated) {
        infiniteTransition.animateFloat(
            initialValue = 0.6f,
            targetValue = 0.95f,
            animationSpec = infiniteRepeatable(
                animation = tween(2200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "ring_glow"
        )
    } else {
        rememberUpdatedState(0.85f)
    }

    Canvas(modifier = modifier.size(size)) {
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val orbitalRadius = this.size.width * 0.38f

        // 1. Ambient Radial Glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF00E5FF).copy(alpha = 0.28f * glowAlpha),
                    Color(0xFF0077FF).copy(alpha = 0.12f * glowAlpha),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = orbitalRadius * 1.5f
            ),
            radius = orbitalRadius * 1.4f,
            center = Offset(cx, cy)
        )

        // 2. Outer Orbital Ring Soft Glow
        drawCircle(
            color = Color(0xFF00F0FF).copy(alpha = 0.25f * glowAlpha),
            radius = orbitalRadius,
            center = Offset(cx, cy),
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        )

        // 3. Crisp Inner Ring
        drawCircle(
            brush = Brush.sweepGradient(
                colors = listOf(
                    Color(0xFF00E5FF),
                    Color(0xFF0091FF),
                    Color(0xFF00E5FF),
                    Color(0xFF2979FF),
                    Color(0xFF00E5FF)
                ),
                center = Offset(cx, cy)
            ),
            radius = orbitalRadius,
            center = Offset(cx, cy),
            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
        )

        // 4. Orbital Satellites (rotates smoothly around ring)
        val radOffset = Math.toRadians(rotation.toDouble())
        // Node 1 (starts at -35 deg)
        val angle1 = Math.toRadians(-35.0) + radOffset
        val n1x = cx + orbitalRadius * cos(angle1).toFloat()
        val n1y = cy + orbitalRadius * sin(angle1).toFloat()
        drawCircle(
            color = Color(0xFF00E5FF).copy(alpha = 0.4f),
            radius = 5.dp.toPx(),
            center = Offset(n1x, n1y)
        )
        drawCircle(
            color = Color(0xFF00F0FF),
            radius = 3.dp.toPx(),
            center = Offset(n1x, n1y)
        )
        drawCircle(
            color = Color.White,
            radius = 1.4.dp.toPx(),
            center = Offset(n1x, n1y)
        )

        // Node 2 (starts at 145 deg)
        val angle2 = Math.toRadians(145.0) + radOffset
        val n2x = cx + orbitalRadius * cos(angle2).toFloat()
        val n2y = cy + orbitalRadius * sin(angle2).toFloat()
        drawCircle(
            color = Color(0xFF00E5FF).copy(alpha = 0.4f),
            radius = 4.5.dp.toPx(),
            center = Offset(n2x, n2y)
        )
        drawCircle(
            color = Color(0xFF00F0FF),
            radius = 2.6.dp.toPx(),
            center = Offset(n2x, n2y)
        )
        drawCircle(
            color = Color.White,
            radius = 1.2.dp.toPx(),
            center = Offset(n2x, n2y)
        )

        // 5. Draw 3D Folded Ribbon 'N'
        drawRibbonN(cx, cy, orbitalRadius * 1.05f)
    }
}

/**
 * Draws the stylized folded-ribbon N geometry
 */
private fun DrawScope.drawRibbonN(cx: Float, cy: Float, span: Float) {
    val scale = span / 70f

    // Coordinate space centered at (cx, cy)
    // 1. Right stem underlayer
    val rightStemPath = Path().apply {
        moveTo(cx + 8f * scale, cy - 22f * scale)
        lineTo(cx + 19f * scale, cy - 15f * scale)
        lineTo(cx + 19f * scale, cy + 18f * scale)
        cubicTo(
            cx + 19f * scale, cy + 24f * scale,
            cx + 14f * scale, cy + 26f * scale,
            cx + 9f * scale, cy + 26f * scale
        )
        cubicTo(
            cx + 5f * scale, cy + 26f * scale,
            cx + 3f * scale, cy + 24f * scale,
            cx + 1f * scale, cy + 20f * scale
        )
        lineTo(cx + 8f * scale, cy - 22f * scale)
        close()
    }
    drawPath(
        path = rightStemPath,
        brush = Brush.linearGradient(
            colors = listOf(
                Color(0xFF00E5FF),
                Color(0xFF0066FF),
                Color(0xFF0A1E66)
            ),
            start = Offset(cx + 8f * scale, cy - 22f * scale),
            end = Offset(cx + 19f * scale, cy + 26f * scale)
        )
    )

    // Beveled top tip for right stem
    val rightBevel = Path().apply {
        moveTo(cx + 8f * scale, cy - 22f * scale)
        lineTo(cx + 19f * scale, cy - 15f * scale)
        lineTo(cx + 19f * scale, cy + 2f * scale)
        lineTo(cx + 8f * scale, cy - 6f * scale)
        close()
    }
    drawPath(
        path = rightBevel,
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFF40C4FF), Color(0xFF0050D8)),
            start = Offset(cx + 8f * scale, cy - 22f * scale),
            end = Offset(cx + 19f * scale, cy + 2f * scale)
        )
    )

    // Shadow under the diagonal crossover
    val foldShadow = Path().apply {
        moveTo(cx - 2f * scale, cy + 2f * scale)
        lineTo(cx + 19f * scale, cy + 14f * scale)
        lineTo(cx + 19f * scale, cy + 22f * scale)
        lineTo(cx + 9f * scale, cy + 26f * scale)
        lineTo(cx - 5f * scale, cy + 10f * scale)
        close()
    }
    drawPath(path = foldShadow, color = Color(0xDD040A1E))

    // 2. Main Front Ribbon (Left vertical pillar + curved arch + diagonal sweep)
    val frontRibbon = Path().apply {
        moveTo(cx - 20f * scale, cy + 25f * scale)
        lineTo(cx - 20f * scale, cy - 12f * scale)
        cubicTo(
            cx - 20f * scale, cy - 23f * scale,
            cx - 12f * scale, cy - 26f * scale,
            cx - 4f * scale, cy - 25f * scale
        )
        cubicTo(
            cx + 2f * scale, cy - 24f * scale,
            cx + 8f * scale, cy - 19f * scale,
            cx + 14f * scale, cy - 12f * scale
        )
        lineTo(cx + 20f * scale, cy + 8f * scale)
        cubicTo(
            cx + 23f * scale, cy + 16f * scale,
            cx + 20f * scale, cy + 23f * scale,
            cx + 15f * scale, cy + 25f * scale
        )
        cubicTo(
            cx + 10f * scale, cy + 27f * scale,
            cx + 5f * scale, cy + 25f * scale,
            cx + 1f * scale, cy + 19f * scale
        )
        lineTo(cx - 10f * scale, cy + 1f * scale)
        lineTo(cx - 10f * scale, cy + 22f * scale)
        close()
    }
    drawPath(
        path = frontRibbon,
        brush = Brush.linearGradient(
            colors = listOf(
                Color(0xFF80F6FF),
                Color(0xFF00D2FF),
                Color(0xFF0072FF),
                Color(0xFF0040C0)
            ),
            start = Offset(cx - 20f * scale, cy - 25f * scale),
            end = Offset(cx + 20f * scale, cy + 25f * scale)
        )
    )

    // 3. Specular Sheen on the Upper Ribbon Arch
    val sheen = Path().apply {
        moveTo(cx - 20f * scale, cy - 6f * scale)
        lineTo(cx - 20f * scale, cy - 12f * scale)
        cubicTo(
            cx - 20f * scale, cy - 23f * scale,
            cx - 12f * scale, cy - 26f * scale,
            cx - 4f * scale, cy - 25f * scale
        )
        cubicTo(
            cx + 2f * scale, cy - 24f * scale,
            cx + 8f * scale, cy - 19f * scale,
            cx + 13f * scale, cy - 12f * scale
        )
        lineTo(cx + 10f * scale, cy - 10f * scale)
        cubicTo(
            cx + 5f * scale, cy - 16f * scale,
            cx - 1f * scale, cy - 21f * scale,
            cx - 7f * scale, cy - 21f * scale
        )
        cubicTo(
            cx - 13f * scale, cy - 21f * scale,
            cx - 17f * scale, cy - 17f * scale,
            cx - 17f * scale, cy - 6f * scale
        )
        close()
    }
    drawPath(
        path = sheen,
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.9f),
                Color(0xFF80F6FF).copy(alpha = 0.7f),
                Color.Transparent
            ),
            start = Offset(cx - 15f * scale, cy - 26f * scale),
            end = Offset(cx + 10f * scale, cy - 10f * scale)
        )
    )
}

/**
 * Complete Nexora brand header with the exact typography,
 * motto, and creator credit as seen in the official logo:
 *
 *   [ EMBLEM ]
 *    NEXORA
 *   SMARTER • CONNECTED • TOGETHER
 *   ———— by Musaib Hamid ————
 */
@Composable
fun NexoraBrandHeader(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    emblemSize: Dp = if (compact) 56.dp else 90.dp
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NexoraEmblem(size = emblemSize)

        Spacer(modifier = Modifier.height(if (compact) 6.dp else 12.dp))

        // Brand Name "NEXORA"
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "NEXORA",
                color = Color.White,
                fontSize = if (compact) 20.sp else 26.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp,
                fontFamily = FontFamily.SansSerif
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Tagline: "SMARTER • CONNECTED • TOGETHER"
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "SMARTER",
                color = Color(0xFF90A4AE),
                fontSize = if (compact) 9.sp else 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp
            )
            Text(
                text = "  •  ",
                color = Color(0xFF00E5FF),
                fontSize = if (compact) 10.sp else 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "CONNECTED",
                color = Color(0xFF90A4AE),
                fontSize = if (compact) 9.sp else 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp
            )
            Text(
                text = "  •  ",
                color = Color(0xFF00E5FF),
                fontSize = if (compact) 10.sp else 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "TOGETHER",
                color = Color(0xFF90A4AE),
                fontSize = if (compact) 9.sp else 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp
            )
        }

        Spacer(modifier = Modifier.height(if (compact) 4.dp else 8.dp))

        // Credit: "———— by Musaib Hamid ————"
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .width(if (compact) 24.dp else 40.dp)
                    .height(1.5.dp)
                    .graphicsLayer(alpha = 0.7f)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawLine(
                        brush = Brush.horizontalGradient(
                            listOf(Color.Transparent, Color(0xFF00E5FF))
                        ),
                        start = Offset(0f, size.height / 2),
                        end = Offset(size.width, size.height / 2),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }
            }

            Text(
                text = "  by Musaib Hamid  ",
                color = Color(0xFFCFD8DC),
                fontSize = if (compact) 10.sp else 11.5.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp
            )

            Box(
                modifier = Modifier
                    .width(if (compact) 24.dp else 40.dp)
                    .height(1.5.dp)
                    .graphicsLayer(alpha = 0.7f)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawLine(
                        brush = Brush.horizontalGradient(
                            listOf(Color(0xFF00E5FF), Color.Transparent)
                        ),
                        start = Offset(0f, size.height / 2),
                        end = Offset(size.width, size.height / 2),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }
            }
        }
    }
}
