package com.desk.moodboard.ui.home.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.desk.moodboard.ui.theme.AccentOrange

@Composable
fun SwimmingFish(
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "fish_animation")
    
    // Smooth horizontal swimming motion
    val swimOffset by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "swim_x"
    )
    
    // Tail wagging - speeds up significantly when recording
    val tailRotation by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isRecording) 400 else 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tail_wag"
    )

    // Subtle body "pulse" during recording
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.1f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Bubble animation logic
    val bubbleTransition = rememberInfiniteTransition(label = "bubbles")
    val bubble1Y by bubbleTransition.animateFloat(
        initialValue = 0f,
        targetValue = -25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "b1_y"
    )
    val bubble2Y by bubbleTransition.animateFloat(
        initialValue = 0f,
        targetValue = -18f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing, delayMillis = 400),
            repeatMode = RepeatMode.Restart
        ),
        label = "b2_y"
    )
    val bubbleAlpha by bubbleTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bubble_alpha"
    )

    Canvas(modifier = modifier.size(32.dp)) {
        val centerX = size.width / 2
        val centerY = size.height / 2

        // Pre-calculate pixel values
        val swimPx = swimOffset.dp.toPx()
        val bodyWidth = 10.dp.toPx()
        val bodyHeight = 6.dp.toPx()
        val tailPivot = 8.dp.toPx()
        val tailLength = 18.dp.toPx()
        val tailHeight = 5.dp.toPx()
        val eyeRadius = 1.dp.toPx()
        val eyeOffsetX = 6.dp.toPx()
        val eyeOffsetY = 1.5.dp.toPx()

        if (isRecording) {
            // Draw subtle bubbles floating up from the fish mouth area
            drawCircle(
                color = AccentOrange.copy(alpha = bubbleAlpha),
                radius = 1.5.dp.toPx(),
                center = Offset(centerX + 12.dp.toPx() + swimPx, centerY + bubble1Y.dp.toPx()),
                style = Stroke(width = 0.5.dp.toPx())
            )
            drawCircle(
                color = AccentOrange.copy(alpha = bubbleAlpha * 0.7f),
                radius = 1.dp.toPx(),
                center = Offset(centerX + 14.dp.toPx() + swimPx, centerY - 2.dp.toPx() + bubble2Y.dp.toPx()),
                style = Stroke(width = 0.5.dp.toPx())
            )
        }

        withTransform({
            translate(swimPx, 0f)
            scale(pulseScale, pulseScale, Offset(centerX, centerY))
        }) {
            // Body
            val bodyPath = Path().apply {
                moveTo(centerX + bodyWidth, centerY)
                quadraticBezierTo(centerX, centerY - bodyHeight, centerX - bodyWidth, centerY)
                quadraticBezierTo(centerX, centerY + bodyHeight, centerX + bodyWidth, centerY)
                close()
            }
            drawPath(bodyPath, color = AccentOrange)

            // Tail
            withTransform({
                rotate(tailRotation, Offset(centerX - tailPivot, centerY))
            }) {
                val tailPath = Path().apply {
                    moveTo(centerX - bodyWidth, centerY)
                    lineTo(centerX - tailLength, centerY - tailHeight)
                    lineTo(centerX - tailLength, centerY + tailHeight)
                    close()
                }
                drawPath(tailPath, color = AccentOrange)
            }

            // Eye
            drawCircle(
                color = Color.White,
                radius = eyeRadius,
                center = Offset(centerX + eyeOffsetX, centerY - eyeOffsetY)
            )
        }
    }
}
