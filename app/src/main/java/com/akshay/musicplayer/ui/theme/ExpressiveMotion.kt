package com.akshay.musicplayer.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/**
 * Material 3 Expressive Physics-Based Spring Motions
 */
object ExpressiveMotion {

    /**
     * Expressive Fast Spring: Snappy feedback for button presses, icon taps, and micro-interactions
     */
    val FastSpring: SpringSpec<Float> = spring(
        dampingRatio = 0.8f,
        stiffness = 800f
    )

    /**
     * Expressive Smooth Spring: Natural fluid movement for sheet expansions, bottom bar transitions, and page sliding
     */
    val SmoothSpring: SpringSpec<Float> = spring(
        dampingRatio = 0.75f,
        stiffness = 380f
    )

    /**
     * Expressive Bouncy Spring: Playful bounce for pill badges, like buttons, and artwork scaling
     */
    val BouncySpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = 500f
    )

    val OffsetSpring: SpringSpec<androidx.compose.ui.unit.IntOffset> = spring(
        dampingRatio = 0.8f,
        stiffness = 500f
    )
}
