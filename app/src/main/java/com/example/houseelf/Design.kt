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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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

/** Домовой в янтарном колпаке. Рисуется на Canvas в координатах 64×64, как в макете. */
@Composable
fun Elf(
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
        scale(scale = this.size.width / 64f, pivot = Offset.Zero) {
            drawCircle(Duo.Mint, radius = 20f, center = Offset(32f, 40f))

            val hat = Path().apply {
                moveTo(15f, 29f)
                lineTo(32f, 4f)
                lineTo(49f, 29f)
                close()
            }
            drawPath(hat, Duo.Amber)
            drawCircle(Duo.Cherry, radius = 3.5f, center = Offset(32f, 5f))

            // При «раздумьях» домовой смотрит вверх
            val lookUp = if (mood == Mood.Thinking) -1.4f else 0f
            drawCircle(Duo.Ink, radius = 3.6f, center = Offset(25f, 40f))
            drawCircle(Duo.Ink, radius = 3.6f, center = Offset(39f, 40f))
            drawCircle(Color.White, radius = 1.3f, center = Offset(26.2f, 38.8f + lookUp))
            drawCircle(Color.White, radius = 1.3f, center = Offset(40.2f, 38.8f + lookUp))

            val mouth = Stroke(width = 2.5f, cap = StrokeCap.Round)
            when (mood) {
                Mood.Happy -> drawArc(
                    color = Duo.Ink, startAngle = 40f, sweepAngle = 100f, useCenter = false,
                    topLeft = Offset(25f, 35f), size = Size(14f, 14f), style = mouth,
                )
                Mood.Sad -> drawArc(
                    color = Duo.Ink, startAngle = 220f, sweepAngle = 100f, useCenter = false,
                    topLeft = Offset(25f, 49f), size = Size(14f, 14f), style = mouth,
                )
                Mood.Thinking -> drawCircle(Duo.Ink, radius = 2.4f, center = Offset(32f, 49f), style = Stroke(width = 2f))
            }
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
