package com.akshay.musicplayer.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onAnimationFinished: () -> Unit) {
    var startAnimation by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (startAnimation) 1.1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "Scale"
    )

    val offsetX by animateDpAsState(
        targetValue = if (startAnimation) (-40).dp else 0.dp,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "Slide"
    )

    val textAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 800, delayMillis = 400),
        label = "Alpha"
    )

    LaunchedEffect(Unit) {
        delay(500)
        startAnimation = true
        delay(2500)
        onAnimationFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F)),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.offset(x = offsetX)
        ) {
            Canvas(
                modifier = Modifier
                    .size(100.dp)
                    .graphicsLayer(scaleX = scale, scaleY = scale)
            ) {
                val w = size.width
                val h = size.height
                
                val gradient = Brush.linearGradient(
                    colors = listOf(Color(0xFF7B1FA2), Color(0xFFFF0050), Color(0xFFFF9800))
                )

                // Headphone Band (Minimalist)
                val bandPath = Path().apply {
                    moveTo(w * 0.28f, h * 0.60f)
                    cubicTo(w * 0.28f, h * 0.25f, w * 0.80f, h * 0.25f, w * 0.80f, h * 0.60f)
                }
                drawPath(bandPath, brush = gradient, style = Stroke(width = 12f, cap = StrokeCap.Round))

                // Earcups
                drawOval(
                    color = Color(0xFF1A1A1A),
                    topLeft = Offset(w * 0.18f, h * 0.58f),
                    size = Size(w * 0.12f, h * 0.24f)
                )
                drawOval(
                    color = Color(0xFF1A1A1A),
                    topLeft = Offset(w * 0.82f, h * 0.58f),
                    size = Size(w * 0.12f, h * 0.24f)
                )
            }

            if (startAnimation) {
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Music",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-3).sp,
                        fontSize = 64.sp
                    ),
                    color = Color.White,
                    modifier = Modifier.graphicsLayer(alpha = textAlpha)
                )
            }
        }
    }
}
