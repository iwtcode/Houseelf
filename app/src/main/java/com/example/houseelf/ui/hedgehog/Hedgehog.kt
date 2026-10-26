package com.example.houseelf.ui.hedgehog

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

/** Краски ёжика — как в исходном рисунке. */
private object HogColors {
    val SpineDark = Color(0xFF4B3029)
    val SpineLight = Color(0xFF7A5447)
    val Skin = Color(0xFFEBC9A2)
    val SkinShade = Color(0xFFE3BC92)
    val Muzzle = Color(0xFFF8E5CB)
    val EarInner = Color(0xFFF2A99A)
    val Cheek = Color(0xFFF4A58A)
    val Brow = Color(0xFF5A3E36)
    val Eye = Color(0xFF2B1B17)
    val Nose = Color(0xFF3A2622)
    val PocketBody = Color(0xFF2F7A8A)
    val PocketLip = Color(0xFF3F97A8)
    val Stitch = Color(0xFFCDEBF0)
    val PawLine = Color(0xFFB98F66)
}

private fun DrawScope.oval(cx: Float, cy: Float, rx: Float, ry: Float, color: Color, alpha: Float = 1f) =
    drawOval(color, topLeft = Offset(cx - rx, cy - ry), size = Size(rx * 2, ry * 2), alpha = alpha)

private fun DrawScope.sticker(x: Float, y: Float, side: Float, color: Color) =
    drawRoundRect(color, topLeft = Offset(x, y), size = Size(side, side), cornerRadius = CornerRadius(4f))

private fun DrawScope.line(path: Path, color: Color, width: Float, alpha: Float = 1f, dashed: Boolean = false) =
    drawPath(
        path, color, alpha = alpha,
        style = Stroke(
            width = width, cap = StrokeCap.Round, join = StrokeJoin.Round,
            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6f, 6f)) else null,
        ),
    )

/** Сам рисунок. Координаты — как в исходном SVG (400×400). */
private fun DrawScope.drawHedgehog(mood: Mood) {
    // Иголки: тёмная подложка и светлый слой поверх
    drawPath(spinesOuter, HogColors.SpineDark)
    drawPath(spinesOuter, HogColors.SpineDark, style = Stroke(3f, join = StrokeJoin.Round))
    drawPath(spinesInner, HogColors.SpineLight)
    drawPath(spinesInner, HogColors.SpineLight, style = Stroke(3f, join = StrokeJoin.Round))

    // Стикеры с делами на иголках
    rotate(-20f, Offset(108f, 175f)) {
        sticker(92f, 160f, 34f, Color(0xFFFFD66B))
        line(stickerLines1, Color(0xFFB98A1C), 2.4f)
    }
    rotate(16f, Offset(292f, 168f)) {
        sticker(275f, 152f, 34f, Color(0xFF8FD9B6))
        line(stickerCheck, Color(0xFF1F6B4F), 3.4f)
    }
    rotate(-5f, Offset(198f, 126f)) {
        sticker(180f, 108f, 36f, Color(0xFFF7A8B8))
        line(stickerLines2, Color(0xFFB5546B), 2.4f)
    }

    // Уши
    oval(132f, 206f, 15f, 15f, HogColors.SkinShade)
    oval(132f, 206f, 8f, 8f, HogColors.EarInner)
    oval(268f, 206f, 15f, 15f, HogColors.SkinShade)
    oval(268f, 206f, 8f, 8f, HogColors.EarInner)

    // Мордочка
    oval(200f, 247f, 72f, 60f, HogColors.Skin)
    drawPath(faceTop, HogColors.SpineLight)
    oval(200f, 266f, 29f, 21f, HogColors.Muzzle)
    oval(146f, 263f, 11f, 7f, HogColors.Cheek, alpha = 0.75f)
    oval(254f, 263f, 11f, 7f, HogColors.Cheek, alpha = 0.75f)

    // Брови
    line(
        when (mood) {
            Mood.Happy -> browsHappy
            Mood.Sad -> browsSad
            Mood.Thinking -> browsThinking
        },
        HogColors.Brow, 3.5f,
    )

    // Глаза. При «раздумьях» ёжик смотрит вверх.
    val up = if (mood == Mood.Thinking) -4f else 0f
    oval(166f, 243f + up, 8.5f, 9.5f, HogColors.Eye)
    oval(234f, 243f + up, 8.5f, 9.5f, HogColors.Eye)
    oval(169f, 239.5f + up, 3.2f, 3.2f, Color.White)
    oval(237f, 239.5f + up, 3.2f, 3.2f, Color.White)
    oval(163.5f, 247f + up, 1.5f, 1.5f, Color.White)
    oval(231.5f, 247f + up, 1.5f, 1.5f, Color.White)

    // Нос и рот
    oval(200f, 259f, 8.5f, 6f, HogColors.Nose)
    oval(198f, 257f, 2.6f, 1.5f, Color.White, alpha = 0.6f)
    when (mood) {
        Mood.Happy -> line(mouthHappy, HogColors.Nose, 2.6f)
        Mood.Sad -> line(mouthSad, HogColors.Nose, 2.6f)
        Mood.Thinking -> {
            line(mouthThinking, HogColors.Nose, 2.6f)
            drawCircle(HogColors.Nose, radius = 3.4f, center = Offset(200f, 272f), style = Stroke(2.4f))
        }
    }

    // Карман со строчкой и лапки на его краю
    drawPath(pocketBody, HogColors.PocketBody)
    line(pocketStitchBody, HogColors.Stitch, 2.4f, dashed = true)
    drawRoundRect(HogColors.PocketLip, Offset(74f, 286f), Size(252f, 24f), CornerRadius(12f))
    line(pocketStitchLip, HogColors.Stitch, 2f, alpha = 0.8f, dashed = true)
    oval(158f, 288f, 17f, 12f, HogColors.Skin)
    oval(242f, 288f, 17f, 12f, HogColors.Skin)
    line(toes, HogColors.PawLine, 2f)
}

/**
 * Ёжик-домовой выглядывает из кармана. Рисуется на Canvas по рисунку из SVG:
 * в кадр попадает всё, кроме фоновой плашки.
 */
@Composable
fun Hedgehog(
    sizeDp: Dp,
    modifier: Modifier = Modifier,
    mood: Mood = Mood.Happy,
    bob: Boolean = false,
) {
    val bobY = if (bob) {
        val transition = rememberInfiniteTransition(label = "bob")
        transition.animateFloat(
            initialValue = -3f,
            targetValue = 3f,
            animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Reverse),
            label = "bobY",
        ).value
    } else 0f

    Canvas(
        modifier
            .size(sizeDp)
            .graphicsLayer { translationY = bobY * density },
    ) {
        // Видимая часть рисунка: квадрат 300×300 с началом в точке (50, 85)
        val k = size.width / 300f
        withTransform({
            scale(k, k, Offset.Zero)
            translate(-50f, -85f)
        }) {
            drawHedgehog(mood)
        }
    }
}
