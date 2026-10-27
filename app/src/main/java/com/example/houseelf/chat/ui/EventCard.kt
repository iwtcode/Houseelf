package com.example.houseelf.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.houseelf.calendar.EventAction
import com.example.houseelf.calendar.EventProposal
import com.example.houseelf.calendar.EventStatus
import com.example.houseelf.calendar.formatEventWhen
import com.example.houseelf.ui.components.DuoButton
import com.example.houseelf.ui.components.DuoCard
import com.example.houseelf.ui.components.DuoIconButton
import com.example.houseelf.ui.components.DuoKind
import com.example.houseelf.ui.components.IconBadge
import com.example.houseelf.ui.icons.DuoIcons
import com.example.houseelf.ui.theme.Duo

/** Карточка события, которое модель вносит в календарь, меняет или удаляет. */
@Composable
internal fun EventCard(ev: EventProposal, actions: EventActions, modifier: Modifier = Modifier) {
    val delete = ev.action == EventAction.Delete
    val border = when {
        ev.status == EventStatus.Failed -> Duo.Red
        ev.status == EventStatus.Dismissed -> Duo.Line
        delete -> Duo.Red
        else -> Duo.Teal
    }
    DuoCard(modifier, borderColor = border) {
        Column(Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconBadge(
                    if (delete) DuoIcons.Trash else DuoIcons.Calendar,
                    background = when {
                        ev.status == EventStatus.Dismissed -> Duo.Track
                        delete -> Duo.RedSoft
                        else -> Duo.TealSoft
                    },
                    tint = when {
                        ev.status == EventStatus.Dismissed -> Duo.Muted
                        delete -> Duo.Red
                        else -> Duo.TealDark
                    },
                )
                Column(Modifier.weight(1f)) {
                    when (ev.action) {
                        EventAction.Update ->
                            Text("Изменение события", style = MaterialTheme.typography.labelSmall, color = Duo.Muted)
                        EventAction.Delete ->
                            Text("Удаление события", style = MaterialTheme.typography.labelSmall, color = Duo.Red)
                        EventAction.Add -> {}
                    }
                    Text(ev.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (ev.recurring) "Повторяющееся событие" else formatEventWhen(ev),
                        style = MaterialTheme.typography.bodySmall,
                        color = Duo.Muted,
                    )
                }
            }
            if (ev.before != null) {
                Text(
                    "Было: ${ev.before}",
                    Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Duo.Muted,
                )
            }
            if (ev.location != null) {
                Row(
                    Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(DuoIcons.Pin, contentDescription = null, tint = Duo.Muted, modifier = Modifier.size(16.dp))
                    Text(ev.location, style = MaterialTheme.typography.bodySmall, color = Duo.Muted)
                }
            }
            if (ev.description != null) {
                // Описание дела свёрнуто, пока человек сам не раскроет
                var descriptionOpen by remember { mutableStateOf(false) }
                Text(
                    "Описание ${if (descriptionOpen) "▴" else "▾"}",
                    Modifier
                        .padding(top = 6.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { descriptionOpen = !descriptionOpen },
                    style = MaterialTheme.typography.labelLarge,
                    color = Duo.TealDark,
                )
                if (descriptionOpen) {
                    Text(
                        ev.description,
                        Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Duo.Muted,
                    )
                }
            }
            if (delete && ev.recurring && ev.status == EventStatus.Pending) {
                Text(
                    "Будут удалены все повторы этого события",
                    Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Duo.Red,
                )
            }

            Spacer(Modifier.height(12.dp))
            when (ev.status) {
                EventStatus.Pending -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val confirmText = when (ev.action) {
                        EventAction.Add -> "Добавить"
                        EventAction.Update -> "Изменить"
                        EventAction.Delete -> "Удалить"
                    }
                    val dismissText = if (delete) "Оставить" else "Не то"
                    // Удаление нельзя отменить, поэтому главной делаем безопасную кнопку
                    DuoButton(
                        confirmText, { actions.onConfirm(ev.id) }, Modifier.weight(1f),
                        kind = if (delete) DuoKind.Outline else DuoKind.Primary,
                    )
                    DuoButton(
                        dismissText, { actions.onDismiss(ev.id) }, Modifier.weight(1f),
                        kind = if (delete) DuoKind.Primary else DuoKind.Outline,
                    )
                }
                EventStatus.NeedsPermission ->
                    Text("Жду разрешение на доступ к календарю…", style = MaterialTheme.typography.bodySmall, color = Duo.Muted)
                EventStatus.Added -> Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(DuoIcons.Check, contentDescription = null, tint = Duo.Teal, modifier = Modifier.size(22.dp))
                    Text(
                        when (ev.action) {
                            EventAction.Add -> "Добавлено в календарь"
                            EventAction.Update -> "Изменено в календаре"
                            EventAction.Delete -> "Удалено из календаря"
                        },
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                        color = Duo.TealDark,
                    )
                    // Удалённое событие открывать, менять и удалять уже нечем
                    val id = ev.calendarEventId
                    if (id != null && !delete) {
                        DuoIconButton(DuoIcons.External, "Открыть в календаре", { actions.onView(id) }, side = 40.dp)
                        // Человек может сам поправить или убрать дело, не прося об этом модель
                        actions.onEdit?.let { onEdit ->
                            DuoIconButton(DuoIcons.Pencil, "Изменить", { onEdit(id) }, side = 40.dp)
                        }
                        actions.onDeleteEvent?.let { onDeleteEvent ->
                            DuoIconButton(DuoIcons.Trash, "Удалить", { onDeleteEvent(id) }, tint = Duo.Red, side = 40.dp)
                        }
                    }
                }
                EventStatus.Failed -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val verb = when (ev.action) {
                        EventAction.Add -> "добавить"
                        EventAction.Update -> "изменить"
                        EventAction.Delete -> "удалить"
                    }
                    Text(
                        "Не удалось $verb: ${ev.error ?: "неизвестная ошибка"}",
                        color = Duo.Red,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DuoButton("Повторить", { actions.onConfirm(ev.id) }, Modifier.weight(1f))
                        if (ev.action == EventAction.Add) {
                            DuoButton("В календаре", { actions.onOpenForm(ev) }, Modifier.weight(1f), kind = DuoKind.Outline)
                        } else if (ev.calendarEventId != null) {
                            DuoButton("Открыть", { actions.onView(ev.calendarEventId) }, Modifier.weight(1f), kind = DuoKind.Outline)
                        }
                    }
                }
                EventStatus.Dismissed ->
                    Text("Отклонено", style = MaterialTheme.typography.bodySmall, color = Duo.Muted)
            }
        }
    }
}
