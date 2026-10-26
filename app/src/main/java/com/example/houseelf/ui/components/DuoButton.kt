package com.example.houseelf.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.houseelf.ui.theme.Duo

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
