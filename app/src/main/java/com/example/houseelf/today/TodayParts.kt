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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import java.util.Locale

internal enum class NodeState { Done, Active, Locked }

@Composable
internal fun Stat(icon: ImageVector, value: String, color: Color, description: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = description, tint = color, modifier = Modifier.size(24.dp))
        Text(value, color = color, style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp))
    }
}

/** Что пользователь может сделать с делом на пути дня. */
internal class TaskActions(
    val onStart: () -> Unit,
    val onNextStep: () -> Unit,
    val onDone: () -> Unit,
    val onOpen: () -> Unit,
)

@Composable
internal fun PathItem(
    task: DayTask,
    state: NodeState,
    offset: Dp,
    started: Boolean,
    stepsDone: Int,
    actions: TaskActions,
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
        Column(Modifier.weight(1f)) {
            Column(Modifier.clickable(onClick = actions.onOpen)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                    color = if (state == NodeState.Locked) Duo.Muted else Duo.Ink,
                )
                val subtitle = when (state) {
                    NodeState.Done -> "Готово · +${ProgressStore.XP_PER_TASK} XP"
                    else -> whenLabel(task)
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
            }
            // Описание дела свёрнуто, пока человек сам не раскроет
            task.description?.let { TaskDescription(it) }
            val completedSteps = if (state == NodeState.Done) task.steps.size else stepsDone.coerceAtMost(task.steps.size)
            TaskDetails(task, completedSteps, current = state == NodeState.Active && started)
            if (state == NodeState.Active) {
                Spacer(Modifier.height(8.dp))
                ActiveTaskControls(task, started, stepsDone, actions)
            }
        }
    }
}

/** Кнопка текущего дела: «Начать» → (Дальше →) «Готово». */
@Composable
private fun ActiveTaskControls(task: DayTask, started: Boolean, stepsDone: Int, actions: TaskActions) {
    if (!started) {
        DuoButton("Начать", actions.onStart, Modifier.fillMaxWidth(), height = 44.dp)
        return
    }
    val steps = task.steps
    val stepIndex = stepsDone.coerceAtMost(steps.lastIndex)
    val isLastStep = steps.isEmpty() || stepIndex == steps.lastIndex
    if (steps.isNotEmpty()) {
        Text(
            "Шаг ${stepIndex + 1} из ${steps.size}: ${steps[stepIndex]}",
            Modifier.padding(bottom = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    if (isLastStep) {
        DuoButton(
            "Готово · +${ProgressStore.XP_PER_TASK} XP",
            actions.onDone,
            Modifier.fillMaxWidth(),
            icon = DuoIcons.Check,
            height = 44.dp,
        )
    } else {
        DuoButton("Дальше", actions.onNextStep, Modifier.fillMaxWidth(), height = 44.dp)
    }
}

/** Разворачиваемое описание дела: по умолчанию свёрнуто. */
@Composable
private fun TaskDescription(text: String) {
    var expanded by remember { mutableStateOf(false) }
    Text(
        "Описание ${if (expanded) "▴" else "▾"}",
        Modifier.clickable { expanded = !expanded }.padding(vertical = 6.dp),
        style = MaterialTheme.typography.labelLarge,
        color = Duo.TealDark,
    )
    if (expanded) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Duo.Muted)
    }
}

/** Разворачиваемый блок с шагами дела. Если дело не разбивали на шаги — ничего не рисуем. */
@Composable
private fun TaskDetails(task: DayTask, completedSteps: Int, current: Boolean) {
    if (task.steps.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }

    Text(
        "Шаги: $completedSteps из ${task.steps.size} ${if (expanded) "▴" else "▾"}",
        Modifier.clickable { expanded = !expanded }.padding(vertical = 6.dp),
        style = MaterialTheme.typography.labelLarge,
        color = Duo.TealDark,
    )
    if (!expanded) return

    task.steps.forEachIndexed { index, step ->
        val mark = when {
            index < completedSteps -> "✓"
            index == completedSteps && current -> "▶"
            else -> "○"
        }
        Text(
            "$mark $step",
            Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = if (index < completedSteps) Duo.Muted else Duo.Ink,
        )
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

private fun whenLabel(task: DayTask): String {
    val time = if (task.allDay) "Весь день" else "${task.start.format(timeFormat)}–${task.end.format(timeFormat)}"
    val place = if (task.location != null) " · ${task.location}" else ""
    return time + place
}

private val dayFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())
private val dayWithYearFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())

/** «Сегодня», «Завтра» или дата словами — например, «Пятница, 6 ноября». */
internal fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Сегодня"
    today.plusDays(1) -> "Завтра"
    else -> date.format(if (date.year == today.year) dayFormat else dayWithYearFormat)
        .replaceFirstChar { it.uppercase() }
}

/** Заголовок дня над его делами на пути. */
@Composable
internal fun DayHeader(date: LocalDate, today: LocalDate) {
    Text(
        dayLabel(date, today),
        Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.titleSmall,
        color = if (date == today) Duo.TealDark else Duo.Muted,
    )
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
