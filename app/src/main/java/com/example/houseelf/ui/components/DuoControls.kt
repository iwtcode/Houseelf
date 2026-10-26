package com.example.houseelf.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.houseelf.ui.theme.Duo

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
