package com.pioneer.messenger.ui.components.liquidglass

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Применяет эффект размытия к фону
 */
fun Modifier.backdropBlur(
    radius: Dp = 8.dp,
    shape: Shape? = null
): Modifier {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        this.graphicsLayer {
            renderEffect = RenderEffect
                .createBlurEffect(
                    radius.toPx(),
                    radius.toPx(),
                    Shader.TileMode.CLAMP
                )
                .asComposeRenderEffect()
            if (shape != null) {
                this.shape = shape
                clip = true
            }
        }
    } else {
        this
    }
}

/**
 * Применяет эффект vibrancy (яркости) к фону
 */
fun Modifier.backdropVibrancy(
    alpha: Float = 0.8f
): Modifier {
    return this.graphicsLayer {
        this.alpha = alpha
    }
}

/**
 * Комбинированный эффект Liquid Glass
 */
fun Modifier.liquidGlassEffect(
    blurRadius: Dp = 8.dp,
    alpha: Float = 0.9f,
    shape: Shape? = null
): Modifier {
    return this
        .backdropBlur(blurRadius, shape)
        .backdropVibrancy(alpha)
}
