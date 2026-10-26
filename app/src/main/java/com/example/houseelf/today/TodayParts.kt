package com.example.houseelf.today

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.houseelf.ui.components.DuoButton
import com.example.houseelf.ui.icons.DuoIcons
import com.example.houseelf.ui.theme.Duo
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal enum class NodeState { Done, Active, Locked }

@Composable
internal fun Stat(icon: ImageVector, value: String, color: Color, description: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = description, tint = color, modifier = Modifier.size(24.dp))
        Text(value, color = color, style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp))
    }
}

@Composable
internal fun PathItem(
    task: DayTask,
    state: NodeState,
    today: LocalDate,
    offset: Dp,
    onDone: () -> Unit,
    onOpen: () -> Unit,
) {
    val now = LocalDateTime.now()
    val overdue = state == NodeState.Active && !task.allDay && task.end.isBefore(now)

    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = offset)
            .alpha(if (state == NodeState.Locked) 0.85f else 1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when (state) {
            NodeState.Done -> PathNode(DuoIcons.Check, Duo.Teal, Duo.TealDark, Color.White, ring = false)
            NodeState.Active -> PathNode(DuoIcons.Calendar, Duo.Amber, Duo.AmberDark, Duo.Ink, ring = true)
            NodeState.Locked -> PathNode(DuoIcons.Lock, Duo.Track, Duo.Line, Duo.Muted, ring = false)
        }
        Column(Modifier.weight(1f).clickable(onClick = onOpen)) {
            Text(
                task.title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                color = if (state == NodeState.Locked) Duo.Muted else Duo.Ink,
            )
            val subtitle = when (state) {
                NodeState.Done -> "Готово · +${ProgressStore.XP_PER_TASK} XP"
                else -> whenLabel(task, today)
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    state == NodeState.Done -> Duo.Teal
                    overdue -> Duo.Red
                    else -> Duo.Muted
                },
            )
            if (state == NodeState.Active) {
                Spacer(Modifier.height(8.dp))
                DuoButton(
                    "Готово · +${ProgressStore.XP_PER_TASK} XP",
                    onDone,
                    Modifier.fillMaxWidth(),
                    icon = DuoIcons.Check,
                    height = 44.dp,
                )
            }
        }
    }
}

/** Круглый «узел» пути. У текущего дела вокруг мягко пульсирует кольцо. */
@Composable
private fun PathNode(icon: ImageVector, face: Color, edge: Color, tint: Color, ring: Boolean) {
    val pulse = if (ring) {
        val transition = rememberInfiniteTransition(label = "pulse")
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(animation = tween(1100), repeatMode = RepeatMode.Reverse),
            label = "pulseScale",
        ).value
    } else 1f

    Box(Modifier.size(width = 72.dp, height = 72.dp), contentAlignment = Alignment.Center) {
        if (ring) {
            Box(
                Modifier
                    .size(70.dp)
                    .graphicsLayer { scaleX = pulse; scaleY = pulse }
                    .clip(CircleShape)
                    .background(Duo.AmberSoft),
            )
        }
        Box(Modifier.size(60.dp).offset(y = 2.dp).clip(CircleShape).background(edge))
        Box(
            Modifier.size(60.dp).offset(y = (-2).dp).clip(CircleShape).background(face),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
        }
    }
}

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

private fun whenLabel(task: DayTask, today: LocalDate): String {
    val day = if (task.date == today) "" else "Завтра · "
    val time = if (task.allDay) "Весь день" else "${task.start.format(timeFormat)}–${task.end.format(timeFormat)}"
    val place = if (task.location != null) " · ${task.location}" else ""
    return day + time + place
}

internal fun dealsLeft(n: Int): String {
    val m10 = n % 10
    val m100 = n % 100
    val word = when {
        m10 == 1 && m100 != 11 -> "дело"
        m10 in 2..4 && m100 !in 12..14 -> "дела"
        else -> "дел"
    }
    return "$n $word"
}
