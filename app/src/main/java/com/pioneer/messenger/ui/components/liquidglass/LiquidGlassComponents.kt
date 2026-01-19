package com.pioneer.messenger.ui.components.liquidglass

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pioneer.messenger.ui.theme.MKRColors
import kotlinx.coroutines.launch

/**
 * Liquid Glass Button с эффектами blur и анимацией
 */
@Composable
fun LiquidGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    surfaceColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
    blurRadius: Dp = 8.dp,
    content: @Composable RowScope.() -> Unit
) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    Row(
        modifier = modifier
            .then(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Modifier.blur(blurRadius)
                } else Modifier
            )
            .scale(scale.value)
            .clip(RoundedCornerShape(24.dp))
            .background(surfaceColor)
            .border(
                width = 1.dp,
                color = MKRColors.GlassBorder,
                shape = RoundedCornerShape(24.dp)
            )
            .clickable(onClick = onClick)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        scope.launch {
                            scale.animateTo(0.95f, spring(stiffness = 500f))
                            tryAwaitRelease()
                            scale.animateTo(1f, spring(stiffness = 500f))
                        }
                    }
                )
            }
            .height(48.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * Liquid Glass Card с эффектом размытия
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    surfaceColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
    blurRadius: Dp = 12.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .then(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Modifier.blur(blurRadius)
                } else Modifier
            )
            .clip(RoundedCornerShape(16.dp))
            .background(surfaceColor)
            .border(
                width = 1.dp,
                color = MKRColors.GlassBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        content()
    }
}

/**
 * Liquid Glass Surface - базовый контейнер с эффектом стекла
 */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    blurRadius: Dp = 8.dp,
    surfaceColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
    shape: Shape = RoundedCornerShape(12.dp),
    content: @Composable BoxScope.() -> Unit = {}
) {
    Box(
        modifier = modifier
            .then(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Modifier.blur(blurRadius)
                } else Modifier
            )
            .clip(shape)
            .background(surfaceColor)
            .border(
                width = 1.dp,
                color = MKRColors.GlassBorder,
                shape = shape
            ),
        content = content
    )
}

/**
 * Liquid Glass Container для списков и больших областей
 */
@Composable
fun LiquidGlassContainer(
    modifier: Modifier = Modifier,
    surfaceColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
    blurRadius: Dp = 16.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .then(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Modifier.blur(blurRadius)
                } else Modifier
            )
            .clip(RoundedCornerShape(20.dp))
            .background(surfaceColor)
            .border(
                width = 1.dp,
                color = MKRColors.GlassBorder,
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        content()
    }
}

/**
 * Градиентная Liquid Glass кнопка - БЕЗ BLUR НА САМОЙ КНОПКЕ!
 */
@Composable
fun LiquidGlassGradientButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    gradient: Brush = Brush.horizontalGradient(
        colors = listOf(
            MKRColors.GradientStart,
            MKRColors.GradientMiddle,
            MKRColors.GradientEnd
        )
    ),
    blurRadius: Dp = 12.dp,
    content: @Composable RowScope.() -> Unit
) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    
    val actualGradient = if (enabled) gradient else Brush.horizontalGradient(
        colors = listOf(
            Color.Gray.copy(alpha = 0.5f),
            Color.Gray.copy(alpha = 0.3f)
        )
    )

    Row(
        modifier = modifier
            .scale(scale.value)
            .clip(RoundedCornerShape(28.dp))
            .background(actualGradient)
            .border(
                width = 2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (enabled) 0.5f else 0.2f),
                        Color.White.copy(alpha = 0.1f)
                    )
                ),
                shape = RoundedCornerShape(28.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .height(56.dp)
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}
