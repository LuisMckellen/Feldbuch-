package com.example.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * Shared spring-physics and timing presets for Feldbuch motion graphics.
 * Keeps transitions crisp (< 280ms perceptual settle) and physically grounded.
 */
object FeldbuchMotion {
  const val FAST_ENTER_MS = 220
  const val FAST_EXIT_MS = 150

  val SnappySpring =
    spring<Float>(
      dampingRatio = 0.82f,
      stiffness = Spring.StiffnessMedium,
    )

  val BouncySpring =
    spring<Float>(
      dampingRatio = 0.68f,
      stiffness = Spring.StiffnessMediumLow,
    )

  val ExpandSizeSpring =
    spring<IntSize>(
      dampingRatio = 0.86f,
      stiffness = Spring.StiffnessMediumLow,
      visibilityThreshold = IntSize.VisibilityThreshold,
    )

  val OffsetSpring =
    spring<IntOffset>(
      dampingRatio = 0.84f,
      stiffness = Spring.StiffnessMedium,
      visibilityThreshold = IntOffset.VisibilityThreshold,
    )

  fun expandFadeIn(): EnterTransition =
    expandVertically(animationSpec = ExpandSizeSpring) +
      fadeIn(animationSpec = tween(durationMillis = FAST_ENTER_MS))

  fun shrinkFadeOut(): ExitTransition =
    shrinkVertically(animationSpec = ExpandSizeSpring) +
      fadeOut(animationSpec = tween(durationMillis = FAST_EXIT_MS))

  fun pillSlideIn(): EnterTransition =
    slideInVertically(animationSpec = OffsetSpring) { fullHeight -> fullHeight / 2 } +
      fadeIn(animationSpec = tween(durationMillis = FAST_ENTER_MS)) +
      scaleIn(initialScale = 0.94f, animationSpec = SnappySpring)

  fun pillSlideOut(): ExitTransition =
    slideOutVertically(animationSpec = OffsetSpring) { fullHeight -> fullHeight / 2 } +
      fadeOut(animationSpec = tween(durationMillis = FAST_EXIT_MS)) +
      scaleOut(targetScale = 0.94f, animationSpec = tween(durationMillis = FAST_EXIT_MS))

  fun tabEnter(): EnterTransition =
    fadeIn(animationSpec = tween(durationMillis = FAST_ENTER_MS)) +
      slideInVertically(animationSpec = tween(durationMillis = FAST_ENTER_MS)) { height ->
        (height / 36).coerceAtMost(28)
      }

  fun tabExit(): ExitTransition =
    fadeOut(animationSpec = tween(durationMillis = FAST_EXIT_MS))
}
