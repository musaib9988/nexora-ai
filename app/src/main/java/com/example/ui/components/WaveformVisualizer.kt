package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.VioletSecondary
import kotlin.random.Random

@Composable
fun WaveformVisualizer(
    isActive: Boolean,
    audioLevel: Float = 0f,
    modifier: Modifier = Modifier,
    barCount: Int = 18
) {
    val infiniteTransition = rememberInfiniteTransition(label = "WaveformAnimation")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(barCount) { index ->
            val phaseOffset = (index * 150)
            val animatedHeight by infiniteTransition.animateFloat(
                initialValue = 0.15f,
                targetValue = 0.95f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 350 + (index % 5) * 80,
                        delayMillis = phaseOffset % 300,
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "BarHeight_$index"
            )

            val heightMultiplier = if (isActive) {
                (animatedHeight * 0.5f + audioLevel * 0.6f).coerceIn(0.12f, 1f)
            } else {
                0.08f
            }

            Box(
                modifier = Modifier
                    .padding(horizontal = 2.5.dp)
                    .width(4.dp)
                    .fillMaxHeight(heightMultiplier)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (index % 2 == 0) CyanPrimary else VioletSecondary
                    )
            )
        }
    }
}
