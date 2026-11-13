package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.FeldbuchMotion

/**
 * Tactile spring press scale that leaves click handling untouched.
 */
fun Modifier.pressableScale(
  interactionSource: MutableInteractionSource,
  pressedScale: Float = 0.982f,
): Modifier = composed {
  val isPressed by interactionSource.collectIsPressedAsState()
  val scale by
    animateFloatAsState(
      targetValue = if (isPressed) pressedScale else 1f,
      animationSpec = FeldbuchMotion.SnappySpring,
      label = "pressScale",
    )
  graphicsLayer {
    scaleX = scale
    scaleY = scale
  }
}

/**
 * Subtle archival dot-grid and radial corner wash behind screen content,
 * giving the fieldbook aesthetic atmospheric depth without hurting legibility.
 */
@Composable
fun FieldLedgerBackdrop(
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  val accentGlow = MaterialTheme.colorScheme.secondary.copy(alpha = 0.065f)
  val secondaryGlow = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.045f)
  val dotColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.30f)

  Box(
    modifier =
      modifier
        .fillMaxSize()
        .drawBehind {
          drawRect(
            brush =
              Brush.radialGradient(
                colors = listOf(accentGlow, Color.Transparent),
                center = Offset(size.width * 0.92f, size.height * 0.06f),
                radius = size.maxDimension * 0.65f,
              )
          )
          drawRect(
            brush =
              Brush.radialGradient(
                colors = listOf(secondaryGlow, Color.Transparent),
                center = Offset(size.width * 0.08f, size.height * 0.88f),
                radius = size.maxDimension * 0.55f,
              )
          )

          val stepPx = 22.dp.toPx()
          val radiusPx = 1.dp.toPx()
          var x = stepPx * 0.5f
          while (x < size.width) {
            var y = stepPx * 0.5f
            while (y < size.height) {
              drawCircle(
                color = dotColor,
                radius = radiusPx,
                center = Offset(x, y),
              )
              y += stepPx
            }
            x += stepPx
          }
        },
  ) {
    content()
  }
}

/**
 * Animated live status beacon with concentric ripple ring when reachable or actively checking.
 */
@Composable
fun PulsingStatusBeacon(
  color: Color,
  isPulsing: Boolean,
  modifier: Modifier = Modifier,
  coreSize: Dp = 10.dp,
) {
  val animatedColor by
    animateColorAsState(
      targetValue = color,
      animationSpec = tween(durationMillis = 250),
      label = "beaconColor",
    )
  val infiniteTransition = rememberInfiniteTransition(label = "statusBeacon")
  val ringScale by
    infiniteTransition.animateFloat(
      initialValue = 1f,
      targetValue = if (isPulsing) 2.15f else 1f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = "beaconRingScale",
    )
  val ringAlpha by
    infiniteTransition.animateFloat(
      initialValue = if (isPulsing) 0.45f else 0f,
      targetValue = 0f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = "beaconRingAlpha",
    )

  Box(
    contentAlignment = Alignment.Center,
    modifier = modifier.size(coreSize * 2.2f),
  ) {
    if (isPulsing) {
      Box(
        modifier =
          Modifier.size(coreSize)
            .graphicsLayer {
              scaleX = ringScale
              scaleY = ringScale
              alpha = ringAlpha
            }
            .clip(CircleShape)
            .background(animatedColor)
      )
    }
    Box(
      modifier = Modifier.size(coreSize).clip(CircleShape).background(animatedColor)
    )
  }
}

/**
 * Smoothly rotating sync/refresh glyph when a network/worker operation is active.
 */
@Composable
fun SpinningSyncIcon(
  isSpinning: Boolean,
  imageVector: ImageVector,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  tint: Color = MaterialTheme.colorScheme.onSurface,
) {
  val infiniteTransition = rememberInfiniteTransition(label = "syncSpin")
  val spinAngle by
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = if (isSpinning) 360f else 0f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 900, easing = LinearEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = "syncAngle",
    )

  Icon(
    imageVector = imageVector,
    contentDescription = contentDescription,
    tint = tint,
    modifier =
      modifier.graphicsLayer {
        rotationZ = if (isSpinning) spinAngle else 0f
      },
  )
}

/**
 * Vertical rolling numeric transition for counters (file counts, waiting badges, match counts).
 */
@Composable
fun AnimatedCounterValue(
  count: Int,
  style: TextStyle,
  color: Color,
  modifier: Modifier = Modifier,
) {
  AnimatedContent(
    targetState = count,
    transitionSpec = {
      if (targetState >= initialState) {
        (slideInVertically(tween(180)) { it / 2 } + fadeIn(tween(180))) togetherWith
          (slideOutVertically(tween(140)) { -it / 2 } + fadeOut(tween(120)))
      } else {
        (slideInVertically(tween(180)) { -it / 2 } + fadeIn(tween(180))) togetherWith
          (slideOutVertically(tween(140)) { it / 2 } + fadeOut(tween(120)))
      }
    },
    label = "animatedCounter",
    modifier = modifier,
  ) { targetCount ->
    Text(
      text = "$targetCount",
      style = style,
      color = color,
    )
  }
}

/**
 * Three staggered bouncing ink dots for pending AI / Eule responses.
 */
@Composable
fun TypingDotsIndicator(
  modifier: Modifier = Modifier,
  dotColor: Color = MaterialTheme.colorScheme.secondary,
) {
  val transition = rememberInfiniteTransition(label = "typingDots")
  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(5.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    repeat(3) { index ->
      val offsetY by
        transition.animateFloat(
          initialValue = 0f,
          targetValue = -5f,
          animationSpec =
            infiniteRepeatable(
              animation =
                tween(
                  durationMillis = 420,
                  delayMillis = index * 110,
                  easing = FastOutSlowInEasing,
                ),
              repeatMode = RepeatMode.Reverse,
            ),
          label = "dotBounce_$index",
        )
      val dotAlpha by
        transition.animateFloat(
          initialValue = 0.4f,
          targetValue = 1f,
          animationSpec =
            infiniteRepeatable(
              animation =
                tween(
                  durationMillis = 420,
                  delayMillis = index * 110,
                  easing = FastOutSlowInEasing,
                ),
              repeatMode = RepeatMode.Reverse,
            ),
          label = "dotAlpha_$index",
        )

      Box(
        modifier =
          Modifier.size(7.dp)
            .offset(y = offsetY.dp)
            .graphicsLayer { alpha = dotAlpha }
            .clip(CircleShape)
            .background(dotColor)
      )
    }
  }
}

/**
 * Editorial empty state card with layered icon medallion and optional tip pill.
 */
@Composable
fun ArchivalEmptyStateCard(
  icon: ImageVector,
  title: String,
  body: String,
  modifier: Modifier = Modifier,
  eyebrow: String? = null,
  accentColor: Color = MaterialTheme.colorScheme.secondary,
  actions: (@Composable ColumnScope.() -> Unit)? = null,
) {
  val glowColor = accentColor.copy(alpha = 0.12f)
  Surface(
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainerLowest,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    tonalElevation = 1.dp,
    modifier = modifier.fillMaxWidth(),
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .drawBehind {
            drawCircle(
              brush = Brush.radialGradient(listOf(glowColor, Color.Transparent)),
              radius = size.maxDimension * 0.45f,
              center = Offset(size.width * 0.5f, 44.dp.toPx()),
            )
          }
          .padding(horizontal = 24.dp, vertical = 26.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Surface(
          shape = CircleShape,
          color = accentColor.copy(alpha = 0.08f),
          border = BorderStroke(1.dp, accentColor.copy(alpha = 0.22f)),
          modifier = Modifier.size(64.dp),
        ) {}
        Surface(
          shape = CircleShape,
          color = accentColor.copy(alpha = 0.14f),
          modifier = Modifier.size(46.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = icon,
              contentDescription = null,
              tint = accentColor,
              modifier = Modifier.size(24.dp),
            )
          }
        }
      }

      if (!eyebrow.isNullOrBlank()) {
        Surface(
          shape = MaterialTheme.shapes.extraSmall,
          color = MaterialTheme.colorScheme.surfaceContainer,
          border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
          Text(
            text = eyebrow,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
          )
        }
      }

      Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
      )
      Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )

      if (actions != null) {
        Spacer(modifier = Modifier.height(4.dp))
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(8.dp),
          content = actions,
        )
      }
    }
  }
}

/**
 * Editorial section header with a compact icon medallion for Setup and workflow cards.
 */
@Composable
fun EditorialCardHeader(
  icon: ImageVector,
  title: String,
  modifier: Modifier = Modifier,
  accentColor: Color = MaterialTheme.colorScheme.secondary,
  trailing: (@Composable () -> Unit)? = null,
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.weight(1f),
    ) {
      Surface(
        shape = MaterialTheme.shapes.small,
        color = accentColor.copy(alpha = 0.12f),
        border = BorderStroke(0.5.dp, accentColor.copy(alpha = 0.25f)),
        modifier = Modifier.size(32.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(18.dp),
          )
        }
      }
      Spacer(modifier = Modifier.width(10.dp))
      Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
      )
    }
    if (trailing != null) {
      trailing()
    }
  }
}
