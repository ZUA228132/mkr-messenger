package com.pioneer.messenger.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Кастомные статус-иконки для Pioneer
 */
enum class StatusIcon(val id: String, val displayName: String) {
    STAR("star", "Звезда"),
    HEART("heart", "Сердце"),
    MOON("moon", "Луна"),
    SUN("sun", "Солнце"),
    LIGHTNING("lightning", "Молния"),
    FLAME("flame", "Огонь"),
    LEAF("leaf", "Листок"),
    DIAMOND("diamond", "Алмаз"),
    CROWN("crown", "Корона"),
    MUSIC("music", "Музыка"),
    COFFEE("coffee", "Кофе"),
    ROCKET("rocket", "Ракета"),
    SNOWFLAKE("snowflake", "Снежинка"),
    FLOWER("flower", "Цветок"),
    WAVE("wave", "Волна"),
    CIRCLE("circle", "Круг")
}

/**
 * Цвета для статусов - разные для светлой и тёмной темы
 */
object StatusColors {
    val lightTheme = listOf(
        Color(0xFFE53935), // Красный
        Color(0xFFFF6F00), // Оранжевый
        Color(0xFFFDD835), // Жёлтый
        Color(0xFF43A047), // Зелёный
        Color(0xFF1E88E5), // Синий
        Color(0xFF8E24AA), // Фиолетовый
        Color(0xFFEC407A), // Розовый
        Color(0xFF00ACC1)  // Бирюзовый
    )
    
    val darkTheme = listOf(
        Color(0xFFEF5350), // Красный светлее
        Color(0xFFFFB74D), // Оранжевый светлее
        Color(0xFFFFEE58), // Жёлтый светлее
        Color(0xFF66BB6A), // Зелёный светлее
        Color(0xFF42A5F5), // Синий светлее
        Color(0xFFAB47BC), // Фиолетовый светлее
        Color(0xFFF06292), // Розовый светлее
        Color(0xFF26C6DA)  // Бирюзовый светлее
    )
    
    fun getColor(userId: String, isDarkTheme: Boolean): Color {
        val colors = if (isDarkTheme) darkTheme else lightTheme
        val hash = userId.hashCode()
        return colors[kotlin.math.abs(hash) % colors.size]
    }
}

/**
 * Компонент статус-иконки
 */
@Composable
fun StatusIconView(
    icon: StatusIcon,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    backgroundColor: Color = Color.White
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.7f)) {
            drawStatusIcon(icon, color)
        }
    }
}

/**
 * Паттерн фона из статус-иконок
 */
@Composable
fun StatusPatternBackground(
    icon: StatusIcon,
    color: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = 20.dp,
    spacing: Dp = 40.dp,
    alpha: Float = 0.15f
) {
    Canvas(modifier = modifier) {
        val iconSizePx = iconSize.toPx()
        val spacingPx = spacing.toPx()
        
        var y = -iconSizePx
        var rowIndex = 0
        
        while (y < size.height + iconSizePx) {
            var x = if (rowIndex % 2 == 0) -iconSizePx else spacingPx / 2 - iconSizePx
            
            while (x < size.width + iconSizePx) {
                drawContext.canvas.save()
                drawContext.canvas.translate(x, y)
                
                // Рисуем иконку с прозрачностью
                drawStatusIconSmall(icon, color.copy(alpha = alpha), iconSizePx)
                
                drawContext.canvas.restore()
                x += spacingPx
            }
            
            y += spacingPx * 0.866f // Гексагональная сетка
            rowIndex++
        }
    }
}

private fun DrawScope.drawStatusIcon(icon: StatusIcon, color: Color) {
    val centerX = size.width / 2
    val centerY = size.height / 2
    val radius = minOf(size.width, size.height) / 2
    
    when (icon) {
        StatusIcon.STAR -> drawStar(centerX, centerY, radius, color)
        StatusIcon.HEART -> drawHeart(centerX, centerY, radius, color)
        StatusIcon.MOON -> drawMoon(centerX, centerY, radius, color)
        StatusIcon.SUN -> drawSun(centerX, centerY, radius, color)
        StatusIcon.LIGHTNING -> drawLightning(centerX, centerY, radius, color)
        StatusIcon.FLAME -> drawFlame(centerX, centerY, radius, color)
        StatusIcon.LEAF -> drawLeaf(centerX, centerY, radius, color)
        StatusIcon.DIAMOND -> drawDiamond(centerX, centerY, radius, color)
        StatusIcon.CROWN -> drawCrown(centerX, centerY, radius, color)
        StatusIcon.MUSIC -> drawMusic(centerX, centerY, radius, color)
        StatusIcon.COFFEE -> drawCoffee(centerX, centerY, radius, color)
        StatusIcon.ROCKET -> drawRocket(centerX, centerY, radius, color)
        StatusIcon.SNOWFLAKE -> drawSnowflake(centerX, centerY, radius, color)
        StatusIcon.FLOWER -> drawFlower(centerX, centerY, radius, color)
        StatusIcon.WAVE -> drawWave(centerX, centerY, radius, color)
        StatusIcon.CIRCLE -> drawCircle(color, radius, Offset(centerX, centerY))
    }
}

private fun DrawScope.drawStatusIconSmall(icon: StatusIcon, color: Color, sizePx: Float) {
    val centerX = sizePx / 2
    val centerY = sizePx / 2
    val radius = sizePx / 2
    
    when (icon) {
        StatusIcon.STAR -> drawStar(centerX, centerY, radius, color)
        StatusIcon.HEART -> drawHeart(centerX, centerY, radius, color)
        StatusIcon.MOON -> drawMoon(centerX, centerY, radius, color)
        StatusIcon.SUN -> drawSun(centerX, centerY, radius, color)
        StatusIcon.LIGHTNING -> drawLightning(centerX, centerY, radius, color)
        StatusIcon.FLAME -> drawFlame(centerX, centerY, radius, color)
        StatusIcon.LEAF -> drawLeaf(centerX, centerY, radius, color)
        StatusIcon.DIAMOND -> drawDiamond(centerX, centerY, radius, color)
        StatusIcon.CROWN -> drawCrown(centerX, centerY, radius, color)
        StatusIcon.MUSIC -> drawMusic(centerX, centerY, radius, color)
        StatusIcon.COFFEE -> drawCoffee(centerX, centerY, radius, color)
        StatusIcon.ROCKET -> drawRocket(centerX, centerY, radius, color)
        StatusIcon.SNOWFLAKE -> drawSnowflake(centerX, centerY, radius, color)
        StatusIcon.FLOWER -> drawFlower(centerX, centerY, radius, color)
        StatusIcon.WAVE -> drawWave(centerX, centerY, radius, color)
        StatusIcon.CIRCLE -> drawCircle(color, radius * 0.8f, Offset(centerX, centerY))
    }
}

private fun DrawScope.drawStar(cx: Float, cy: Float, r: Float, color: Color) {
    val path = Path()
    val points = 5
    val innerRadius = r * 0.4f
    
    for (i in 0 until points * 2) {
        val radius = if (i % 2 == 0) r else innerRadius
        val angle = Math.PI / 2 + i * Math.PI / points
        val x = cx + (radius * cos(angle)).toFloat()
        val y = cy - (radius * sin(angle)).toFloat()
        
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}

private fun DrawScope.drawHeart(cx: Float, cy: Float, r: Float, color: Color) {
    val path = Path()
    val width = r * 2
    val height = r * 1.8f
    
    path.moveTo(cx, cy + height * 0.35f)
    path.cubicTo(
        cx - width * 0.5f, cy - height * 0.1f,
        cx - width * 0.5f, cy - height * 0.5f,
        cx, cy - height * 0.2f
    )
    path.cubicTo(
        cx + width * 0.5f, cy - height * 0.5f,
        cx + width * 0.5f, cy - height * 0.1f,
        cx, cy + height * 0.35f
    )
    drawPath(path, color)
}

private fun DrawScope.drawMoon(cx: Float, cy: Float, r: Float, color: Color) {
    drawCircle(color, r, Offset(cx, cy))
    drawCircle(Color.Transparent, r * 0.7f, Offset(cx + r * 0.4f, cy - r * 0.2f), blendMode = androidx.compose.ui.graphics.BlendMode.Clear)
}

private fun DrawScope.drawSun(cx: Float, cy: Float, r: Float, color: Color) {
    drawCircle(color, r * 0.5f, Offset(cx, cy))
    
    for (i in 0 until 8) {
        val angle = i * Math.PI / 4
        val x1 = cx + (r * 0.6f * cos(angle)).toFloat()
        val y1 = cy + (r * 0.6f * sin(angle)).toFloat()
        val x2 = cx + (r * cos(angle)).toFloat()
        val y2 = cy + (r * sin(angle)).toFloat()
        drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = r * 0.15f)
    }
}

private fun DrawScope.drawLightning(cx: Float, cy: Float, r: Float, color: Color) {
    val path = Path()
    path.moveTo(cx + r * 0.2f, cy - r)
    path.lineTo(cx - r * 0.3f, cy)
    path.lineTo(cx + r * 0.1f, cy)
    path.lineTo(cx - r * 0.2f, cy + r)
    path.lineTo(cx + r * 0.3f, cy - r * 0.1f)
    path.lineTo(cx - r * 0.1f, cy - r * 0.1f)
    path.close()
    drawPath(path, color)
}

private fun DrawScope.drawFlame(cx: Float, cy: Float, r: Float, color: Color) {
    val path = Path()
    path.moveTo(cx, cy + r)
    path.quadraticBezierTo(cx - r * 0.8f, cy, cx - r * 0.3f, cy - r * 0.5f)
    path.quadraticBezierTo(cx - r * 0.5f, cy - r, cx, cy - r)
    path.quadraticBezierTo(cx + r * 0.5f, cy - r, cx + r * 0.3f, cy - r * 0.5f)
    path.quadraticBezierTo(cx + r * 0.8f, cy, cx, cy + r)
    drawPath(path, color)
}

private fun DrawScope.drawLeaf(cx: Float, cy: Float, r: Float, color: Color) {
    val path = Path()
    path.moveTo(cx, cy - r)
    path.quadraticBezierTo(cx + r, cy - r * 0.5f, cx + r * 0.8f, cy + r * 0.3f)
    path.quadraticBezierTo(cx, cy + r, cx, cy + r)
    path.quadraticBezierTo(cx, cy + r, cx - r * 0.8f, cy + r * 0.3f)
    path.quadraticBezierTo(cx - r, cy - r * 0.5f, cx, cy - r)
    drawPath(path, color)
    drawLine(color, Offset(cx, cy - r * 0.5f), Offset(cx, cy + r * 0.8f), strokeWidth = r * 0.1f)
}

private fun DrawScope.drawDiamond(cx: Float, cy: Float, r: Float, color: Color) {
    val path = Path()
    path.moveTo(cx, cy - r)
    path.lineTo(cx + r * 0.7f, cy)
    path.lineTo(cx, cy + r)
    path.lineTo(cx - r * 0.7f, cy)
    path.close()
    drawPath(path, color)
}

private fun DrawScope.drawCrown(cx: Float, cy: Float, r: Float, color: Color) {
    val path = Path()
    path.moveTo(cx - r, cy + r * 0.5f)
    path.lineTo(cx - r, cy - r * 0.3f)
    path.lineTo(cx - r * 0.5f, cy)
    path.lineTo(cx, cy - r)
    path.lineTo(cx + r * 0.5f, cy)
    path.lineTo(cx + r, cy - r * 0.3f)
    path.lineTo(cx + r, cy + r * 0.5f)
    path.close()
    drawPath(path, color)
}

private fun DrawScope.drawMusic(cx: Float, cy: Float, r: Float, color: Color) {
    drawCircle(color, r * 0.35f, Offset(cx - r * 0.4f, cy + r * 0.5f))
    drawCircle(color, r * 0.35f, Offset(cx + r * 0.4f, cy + r * 0.3f))
    drawLine(color, Offset(cx - r * 0.1f, cy + r * 0.5f), Offset(cx - r * 0.1f, cy - r * 0.8f), strokeWidth = r * 0.15f)
    drawLine(color, Offset(cx + r * 0.7f, cy + r * 0.3f), Offset(cx + r * 0.7f, cy - r), strokeWidth = r * 0.15f)
    drawLine(color, Offset(cx - r * 0.1f, cy - r * 0.8f), Offset(cx + r * 0.7f, cy - r), strokeWidth = r * 0.15f)
}

private fun DrawScope.drawCoffee(cx: Float, cy: Float, r: Float, color: Color) {
    drawRoundRect(
        color,
        Offset(cx - r * 0.6f, cy - r * 0.3f),
        Size(r * 1.2f, r * 1.3f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r * 0.2f)
    )
    drawArc(
        color,
        startAngle = -90f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(cx + r * 0.5f, cy - r * 0.1f),
        size = Size(r * 0.5f, r * 0.8f),
        style = Stroke(width = r * 0.15f)
    )
}

private fun DrawScope.drawRocket(cx: Float, cy: Float, r: Float, color: Color) {
    val path = Path()
    path.moveTo(cx, cy - r)
    path.quadraticBezierTo(cx + r * 0.5f, cy - r * 0.5f, cx + r * 0.4f, cy + r * 0.3f)
    path.lineTo(cx + r * 0.7f, cy + r)
    path.lineTo(cx, cy + r * 0.5f)
    path.lineTo(cx - r * 0.7f, cy + r)
    path.lineTo(cx - r * 0.4f, cy + r * 0.3f)
    path.quadraticBezierTo(cx - r * 0.5f, cy - r * 0.5f, cx, cy - r)
    drawPath(path, color)
}

private fun DrawScope.drawSnowflake(cx: Float, cy: Float, r: Float, color: Color) {
    for (i in 0 until 6) {
        rotate(i * 60f, Offset(cx, cy)) {
            drawLine(color, Offset(cx, cy - r), Offset(cx, cy + r), strokeWidth = r * 0.12f)
            drawLine(color, Offset(cx - r * 0.3f, cy - r * 0.5f), Offset(cx, cy - r * 0.3f), strokeWidth = r * 0.1f)
            drawLine(color, Offset(cx + r * 0.3f, cy - r * 0.5f), Offset(cx, cy - r * 0.3f), strokeWidth = r * 0.1f)
        }
    }
}

private fun DrawScope.drawFlower(cx: Float, cy: Float, r: Float, color: Color) {
    for (i in 0 until 5) {
        val angle = i * 72.0 * Math.PI / 180
        val petalX = cx + (r * 0.6f * cos(angle)).toFloat()
        val petalY = cy + (r * 0.6f * sin(angle)).toFloat()
        drawCircle(color, r * 0.4f, Offset(petalX, petalY))
    }
    drawCircle(color.copy(alpha = 0.8f), r * 0.3f, Offset(cx, cy))
}

private fun DrawScope.drawWave(cx: Float, cy: Float, r: Float, color: Color) {
    val path = Path()
    path.moveTo(cx - r, cy)
    path.quadraticBezierTo(cx - r * 0.5f, cy - r * 0.8f, cx, cy)
    path.quadraticBezierTo(cx + r * 0.5f, cy + r * 0.8f, cx + r, cy)
    drawPath(path, color, style = Stroke(width = r * 0.25f))
}

/**
 * Получить статус-иконку по userId (детерминированно)
 */
fun getStatusIconForUser(userId: String): StatusIcon {
    val icons = StatusIcon.values()
    val hash = userId.hashCode()
    return icons[kotlin.math.abs(hash) % icons.size]
}
