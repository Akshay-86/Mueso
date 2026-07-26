package com.akshay.musicplayer.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akshay.musicplayer.R
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun SplashScreen(onAnimationFinished: () -> Unit) {
    var startAnimation by remember { mutableStateOf(false) }
    var textVisible by remember { mutableStateOf(false) }

    // Pulsing aura animation
    val infiniteTransition = rememberInfiniteTransition(label = "AuraTransition")
    val auraScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AuraScale"
    )
    val auraAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AuraAlpha"
    )

    // Equalizer bar pulse animations
    val bar1Height by infiniteTransition.animateFloat(
        initialValue = 12f,
        targetValue = 32f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Bar1"
    )
    val bar2Height by infiniteTransition.animateFloat(
        initialValue = 32f,
        targetValue = 16f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, easing = FastOutLinearInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Bar2"
    )
    val bar3Height by infiniteTransition.animateFloat(
        initialValue = 18f,
        targetValue = 36f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Bar3"
    )

    // Entrance animations
    val scale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.15f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "Scale"
    )

    val rotation by animateFloatAsState(
        targetValue = if (startAnimation) 0f else -140f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "Rotation"
    )

    LaunchedEffect(Unit) {
        startAnimation = true // Logo springs up
        delay(400.milliseconds) // Wait for logo to expand
        textVisible = true // Slide out logo & reveal title and soundwave
        delay(1600.milliseconds) // Display time
        onAnimationFinished()
    }

    val accentOrange = Color(0xFFFF512F)
    val accentPink = Color(0xFFDD2476)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        // Glowing Aura Ring behind logo
        Box(
            modifier = Modifier
                .size(110.dp)
                .graphicsLayer(
                    scaleX = scale * auraScale,
                    scaleY = scale * auraScale,
                    alpha = if (startAnimation) auraAlpha else 0f
                )
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(accentOrange.copy(alpha = 0.6f), accentPink.copy(alpha = 0.2f), Color.Transparent)
                    )
                )
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_logo),
                contentDescription = "Mueso Logo",
                modifier = Modifier
                    .size(96.dp)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        rotationZ = rotation
                    )
            )

            AnimatedVisibility(
                visible = textVisible,
                enter = fadeIn(tween(600)) + expandHorizontally(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    expandFrom = Alignment.Start
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.width(20.dp))
                    Text(
                        text = "Mueso",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-1.5).sp,
                            fontSize = 54.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    // Animated Equalizer Visualizer Bars
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(5.dp)
                                .height(bar1Height.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(accentOrange)
                        )
                        Box(
                            modifier = Modifier
                                .width(5.dp)
                                .height(bar2Height.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(accentPink)
                        )
                        Box(
                            modifier = Modifier
                                .width(5.dp)
                                .height(bar3Height.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(accentOrange)
                        )
                    }
                }
            }
        }
    }
}
