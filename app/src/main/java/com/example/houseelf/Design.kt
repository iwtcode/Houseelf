package com.example.houseelf

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.houseelf.ui.theme.Duo

// ───────── Иконки ─────────
// Контурные иконки 24×24 из макета. Нарисованы путями, поэтому отдельная библиотека иконок не нужна.

private fun icon(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        for (d in paths) {
            addPath(
                pathData = addPathNodes(d),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.4f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

object DuoIcons {
    val Flame = icon("flame", "M12 3c1 4 5 5 5 10a5 5 0 0 1-10 0c0-2 1-3 2-4 0 2 1 3 2 3 0-3-1-6 1-9z")
    val Bolt = icon("bolt", "M13 2L5 14h6l-1 8 8-12h-6z")
    val Check = icon("check", "M5 12.5l4.5 4.5L19 7.5")
    val Close = icon("close", "M6 6l12 12M18 6L6 18")
    val Back = icon("back", "M15 5l-7 7 7 7")
    val Plus = icon("plus", "M12 5v14M5 12h14")
    val Calendar = icon(
        "calendar",
        "M7 5h10a3 3 0 0 1 3 3v9a3 3 0 0 1-3 3H7a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3z",
        "M4 10h16M9 3v4M15 3v4",
    )
    val Lock = icon(
        "lock",
        "M7 11h10a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2z",
        "M8 11V8a4 4 0 0 1 8 0v3",
    )
    val Home = icon("home", "M4 11l8-7 8 7v9H4z")
    val Chat = icon("chat", "M4 5h16v11H9l-5 4z")
    val Chip = icon(
        "chip",
        "M8 6h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z",
        "M9 2v4M15 2v4M9 18v4M15 18v4M2 9h4M2 15h4M18 9h4M18 15h4",
    )
    val Image = icon(
        "image",
        "M7 4h10a3 3 0 0 1 3 3v10a3 3 0 0 1-3 3H7a3 3 0 0 1-3-3V7a3 3 0 0 1 3-3z",
        "M7.5 9a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0-3 0",
        "M20 15l-5-5-8 8",
    )
    val Music = icon(
        "music",
        "M9 18V5l11-2v13",
        "M3 18a3 3 0 1 0 6 0a3 3 0 1 0-6 0",
        "M14 16a3 3 0 1 0 6 0a3 3 0 1 0-6 0",
    )
    val Clip = icon("clip", "M20 11l-8 8a5 5 0 0 1-7-7l9-9a3.5 3.5 0 0 1 5 5l-9 9a2 2 0 0 1-3-3l8-8")
    val Mic = icon(
        "mic",
        "M12 3a3 3 0 0 1 3 3v5a3 3 0 0 1-6 0V6a3 3 0 0 1 3-3z",
        "M5 11a7 7 0 0 0 14 0M12 18v3",
    )
    val Send = icon("send", "M4 12l16-8-6 16-3-7z")
    val Trash = icon("trash", "M5 7h14M10 7V4h4v3M7 7l1 13h8l1-13")
    val Download = icon("download", "M12 4v11M7 10l5 5 5-5M5 20h14")
    val Folder = icon("folder", "M4 7h6l2 2h8v10H4z")
    val Play = icon("play", "M8 5l11 7-11 7z")
    val Stop = icon("stop", "M7 7h10v10H7z")
    val Pin = icon(
        "pin",
        "M12 21s-6-5.5-6-11a6 6 0 0 1 12 0c0 5.5-6 11-6 11z",
        "M10 10a2 2 0 1 0 4 0a2 2 0 1 0-4 0",
    )
    val External = icon("external", "M14 4h6v6M20 4l-9 9M18 14v5H5V6h5")
}

// ───────── Маскот ─────────

enum class Mood { Happy, Thinking, Sad }

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

private fun svgPath(d: String): Path = PathParser().parsePathString(d).toPath()

// Контуры из SVG-рисунка в координатах 400×400. Разбираются один раз.
private val spinesOuter by lazy {
    svgPath("M88.5 277.7 L65.9 266.3 L86.0 250.4 L67.1 234.0 L89.1 223.2 L71.8 201.8 L97.8 197.4 L87.5 174.0 L111.4 174.3 L104.6 147.3 L129.4 155.0 L128.7 127.8 L150.9 140.5 L156.3 115.4 L174.8 131.5 L184.9 106.0 L200.0 128.5 L214.8 108.4 L225.2 131.5 L244.3 113.5 L249.1 140.5 L270.5 129.2 L270.6 155.0 L293.9 149.0 L288.6 174.3 L313.9 173.0 L302.2 197.4 L329.0 201.5 L310.9 223.2 L332.8 234.0 L314.0 250.4 L333.6 266.3 L311.5 277.7 L200 302 Z")
}
private val spinesInner by lazy {
    svgPath("M107.2 273.9 L87.5 260.9 L106.6 247.0 L89.5 228.6 L112.5 220.9 L101.8 199.1 L124.6 197.3 L120.1 173.8 L142.0 178.1 L142.3 152.0 L163.5 164.4 L171.1 143.2 L187.5 157.4 L200.0 135.5 L212.5 157.4 L229.2 142.2 L236.5 164.4 L256.1 155.1 L258.0 178.1 L279.1 174.7 L275.4 197.3 L297.2 199.6 L287.5 220.9 L310.0 228.7 L293.4 247.0 L310.7 260.9 L292.8 273.9 L200 308 Z")
}
private val faceTop by lazy {
    svgPath("M122.2 223.4 L128.3 212.5 L136.6 202.6 L146.8 194.1 L158.6 187.2 L171.7 182.2 L185.6 179.0 L200.0 178.0 L214.4 179.0 L228.3 182.2 L241.4 187.2 L253.2 194.1 L263.4 202.6 L271.7 212.5 L277.8 223.4 L267.7 226.5 L249.9 223.0 L255.2 208.4 L237.0 210.2 L236.0 195.0 L219.7 201.9 L212.5 187.9 L200.0 199.0 L187.5 187.9 L180.3 201.9 L164.0 195.0 L163.0 210.2 L144.8 208.4 L150.1 223.0 L132.3 226.5 Z")
}
private val pocketBody by lazy { svgPath("M82 306h236v44a26 26 0 0 1-26 26H108a26 26 0 0 1-26-26z") }
private val pocketStitchBody by lazy { svgPath("M96 322h208v28a16 16 0 0 1-16 16H112a16 16 0 0 1-16-16z") }
private val pocketStitchLip by lazy { svgPath("M90 298h220") }
private val toes by lazy { svgPath("M152 284v6M158 283v7M164 284v6M236 284v6M242 283v7M248 284v6") }

private val browsHappy by lazy { svgPath("M153 227q13-8 26 0M221 227q13-8 26 0") }
private val browsSad by lazy { svgPath("M153 230q13-3 26-9M247 230q-13-3-26-9") }
private val browsThinking by lazy { svgPath("M153 227q13-8 26 0M221 220q13-8 26 0") }
private val mouthHappy by lazy { svgPath("M200 265v4M200 269q-5 6-11 3M200 269q5 6 11 3") }
private val mouthSad by lazy { svgPath("M200 265v3M200 268q-5-5-11-2M200 268q5-5 11-2") }
private val mouthThinking by lazy { svgPath("M200 265v2") }

private val stickerLines1 by lazy { svgPath("M99 171h20M99 178h14M99 185h17") }
private val stickerCheck by lazy { svgPath("M284 170l6 6 12-13") }
private val stickerLines2 by lazy { svgPath("M188 120h20M188 127h14M188 134h17") }

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

// ───────── Кнопки ─────────

enum class DuoKind { Primary, Outline, Amber }

/** Большая кнопка с «объёмом»: при нажатии вдавливается на глубину своей нижней грани. */
@Composable
fun DuoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: DuoKind = DuoKind.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 48.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val depth = 4.dp
    val shape = RoundedCornerShape(16.dp)

    val colors = when {
        !enabled -> Triple(Duo.Track, Duo.Line, Duo.Muted)
        kind == DuoKind.Primary -> Triple(Duo.Teal, Duo.TealDark, Color.White)
        kind == DuoKind.Amber -> Triple(Duo.Amber, Duo.AmberDark, Duo.Ink)
        else -> Triple(Color.White, Duo.Line, Duo.Teal)
    }
    val face = colors.first
    val edge = colors.second
    val content = colors.third
    val sink by animateDpAsState(if (pressed && enabled) depth else 0.dp, label = "press")

    Box(modifier.height(height + depth)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .offset(y = depth)
                .clip(shape)
                .background(edge),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .offset(y = sink)
                .clip(shape)
                .background(face)
                .then(if (kind == DuoKind.Outline && enabled) Modifier.border(2.dp, Duo.Line, shape) else Modifier)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                )
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
                Text(
                    text.uppercase(),
                    color = content,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.6.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Квадратная кнопка с иконкой. [filled] — главная (бирюзовая), иначе белая с рамкой. */
@Composable
fun DuoIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Duo.Ink,
    filled: Boolean = false,
    enabled: Boolean = true,
    side: Dp = 44.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val depth = 3.dp
    val shape = RoundedCornerShape(14.dp)
    val face = if (filled) Duo.Teal else Color.White
    val edge = if (filled) Duo.TealDark else Duo.Line
    val iconTint = if (filled) Color.White else tint
    val sink by animateDpAsState(if (pressed && enabled) depth else 0.dp, label = "press")

    Box(
        modifier
            .size(width = side, height = side + depth)
            .alpha(if (enabled) 1f else 0.45f),
    ) {
        Box(
            Modifier
                .size(side)
                .offset(y = depth)
                .clip(shape)
                .background(edge),
        )
        Box(
            Modifier
                .size(side)
                .offset(y = sink)
                .clip(shape)
                .background(face)
                .then(if (!filled) Modifier.border(2.dp, Duo.Line, shape) else Modifier)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = description, tint = iconTint, modifier = Modifier.size(24.dp))
        }
    }
}

// ───────── Контейнеры ─────────

@Composable
fun DuoCard(
    modifier: Modifier = Modifier,
    borderColor: Color = Duo.Line,
    shape: Shape = RoundedCornerShape(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(shape)
            .background(Color.White)
            .border(2.dp, borderColor, shape),
        content = content,
    )
}

/** Круг с иконкой внутри. */
@Composable
fun IconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    side: Dp = 44.dp,
    background: Color = Duo.TealSoft,
    tint: Color = Duo.TealDark,
) {
    Box(
        modifier
            .size(side)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
    }
}

/** Рамка из штрихов — для блока «добавить ещё». */
fun Modifier.dashedBorder(color: Color, radius: Dp = 20.dp): Modifier = this.drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
    )
}

// ───────── Переключатель, прогресс, навигация ─────────

@Composable
fun DuoSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val track by animateColorAsState(if (checked) Duo.Teal else Duo.SwitchOff, label = "track")
    val thumbX by animateDpAsState(if (checked) 20.dp else 0.dp, label = "thumb")
    Box(
        modifier
            .size(width = 52.dp, height = 32.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(track)
            .toggleable(
                value = checked,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .offset(x = thumbX)
                .size(26.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/** Толстая полоса прогресса. Если [progress] == null — бегущий отрезок. */
@Composable
fun DuoProgressBar(progress: Float?, modifier: Modifier = Modifier, color: Color = Duo.Teal) {
    val sweep = if (progress == null) {
        val transition = rememberInfiniteTransition(label = "sweep")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(animation = tween(1200, easing = LinearEasing)),
            label = "sweepX",
        ).value
    } else 0f

    Canvas(
        modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(Duo.Track),
    ) {
        val radius = CornerRadius(size.height / 2f)
        if (progress != null) {
            val w = size.width * progress.coerceIn(0f, 1f)
            if (w > 0f) drawRoundRect(color, size = Size(w, size.height), cornerRadius = radius)
        } else {
            val seg = size.width * 0.35f
            drawRoundRect(
                color,
                topLeft = Offset((size.width + seg) * sweep - seg, 0f),
                size = Size(seg, size.height),
                cornerRadius = radius,
            )
        }
    }
}

/** Нижняя навигация. 0 — Сегодня, 1 — Домовой, 2 — Мозг. */
@Composable
fun DuoNavBar(selected: Int, onSelect: (Int) -> Unit) {
    val items = listOf(
        "Сегодня" to DuoIcons.Home,
        "Домовой" to DuoIcons.Chat,
        "Мозг" to DuoIcons.Chip,
    )
    Column(Modifier.background(Color.White)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(Duo.Line),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = index == selected
                val shape = RoundedCornerShape(14.dp)
                val tint = if (isSelected) Duo.TealDark else Duo.Muted
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .clip(shape)
                        .background(if (isSelected) Duo.TealSoft else Color.Transparent)
                        .border(2.dp, if (isSelected) Duo.Teal else Color.Transparent, shape)
                        .clickable(role = Role.Tab) { onSelect(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(item.second, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                    Text(item.first, color = tint, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** Шапка экрана: кнопка «назад», заголовок и необязательная подпись. */
@Composable
fun ScreenHeader(title: String, subtitle: String?, onBack: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onBack != null) DuoIconButton(DuoIcons.Back, "Назад", onBack)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Duo.Muted)
            }
        }
    }
}

/** Три точки, пока домовой печатает ответ. */
@Composable
fun TypingDots() {
    val transition = rememberInfiniteTransition(label = "dots")
    Row(
        Modifier.padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        repeat(3) { i ->
            val a by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = i * 150),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$i",
            )
            Box(
                Modifier
                    .size(8.dp)
                    .graphicsLayer { alpha = a }
                    .clip(CircleShape)
                    .background(Duo.Muted),
            )
        }
    }
}
