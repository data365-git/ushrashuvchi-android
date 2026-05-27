package com.example.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

object Motion {
    // Material 3 emphasized easing — accelerates fast, decelerates slowly (feels premium)
    val EmphasizedEasing: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val StandardEasing: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val DecelerateEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    // Durations in ms — Material 3 phone-scale defaults
    const val DurationShort = 150
    const val DurationMedium = 250
    const val DurationLong = 350
}

fun <T> motionShortSpec() = tween<T>(
    durationMillis = Motion.DurationShort,
    easing = Motion.StandardEasing
)

fun <T> motionMediumSpec() = tween<T>(
    durationMillis = Motion.DurationMedium,
    easing = Motion.EmphasizedEasing
)

fun <T> motionLongSpec() = tween<T>(
    durationMillis = Motion.DurationLong,
    easing = Motion.EmphasizedEasing
)

fun motionSpringSpec() = spring<IntOffset>(
    dampingRatio = 0.85f,
    stiffness = 380f
)
