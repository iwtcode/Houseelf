package com.example.houseelf.today

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.houseelf.calendar.CalendarEventInfo
import com.example.houseelf.calendar.EventAction
import com.example.houseelf.calendar.EventProposal
import com.example.houseelf.calendar.applyCalendarAction
import com.example.houseelf.calendar.applyEventDelete
import com.example.houseelf.calendar.applyEventUpdate
import com.example.houseelf.calendar.loadCalendarEvent
import com.example.houseelf.ui.components.DialogAction
import com.example.houseelf.ui.components.DuoDialog
import com.example.houseelf.ui.components.DuoSwitch
import com.example.houseelf.ui.theme.Duo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Что человек хочет сделать с делом вручную. Дела живут в календаре телефона, поэтому ссылаемся на номер события. */
sealed interface TaskRequest {
    /** Новое дело. */
    data class Create(val date: LocalDate = LocalDate.now()) : TaskRequest

    data class Edit(val eventId: Long) : TaskRequest

    data class Delete(val eventId: Long) : TaskRequest
}

/** Что изменилось в календаре после ручной правки: по этому обновляются «Сегодня» и карточки в чате. */
sealed interface TaskChange {
    data class Created(val eventId: Long) : TaskChange

    data class Updated(val event: CalendarEventInfo) : TaskChange

    data class Deleted(val eventId: Long) : TaskChange
}

/**
 * Окна ручного добавления, изменения и удаления дела. Один и тот же редактор открывается и с экрана «Сегодня»,
 * и из карточки в чате. Пока [request] == null — ничего не показывает.
 */
@Composable
fun TaskEditorHost(request: TaskRequest?, onClose: () -> Unit, onChanged: (TaskChange) -> Unit) {
    if (request != null) TaskEditorContent(request, onClose, onChanged)
}

@Composable
private fun TaskEditorContent(request: TaskRequest, onClose: () -> Unit, onChanged: (TaskChange) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Для изменения и удаления сначала читаем актуальное дело из календаря: оно могло поменяться или исчезнуть
    val eventId = when (request) {
        is TaskRequest.Edit -> request.eventId
        is TaskRequest.Delete -> request.eventId
        is TaskRequest.Create -> null
    }
    var info by remember(request) { mutableStateOf<CalendarEventInfo?>(null) }
    LaunchedEffect(request) {
        if (eventId == null) return@LaunchedEffect
        val loaded = try {
            Result.success(withContext(Dispatchers.IO) { loadCalendarEvent(context, eventId) })
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Result.failure(t)
        }
        val event = loaded.getOrNull()
        when {
            loaded.isFailure -> {
                context.toast("Не удалось прочитать календарь")
                onClose()
            }
            event == null -> {
                context.toast("Дела уже нет в календаре")
                onChanged(TaskChange.Deleted(eventId))
                onClose()
            }
            !event.editable -> {
                context.toast("Этот календарь только для чтения")
                onClose()
            }
            else -> info = event
        }
    }

    when (request) {
        is TaskRequest.Create -> TaskFormDialog(
            initial = null,
            defaultDate = request.date,
            onDismiss = onClose,
            onSave = { form ->
                runAction(scope, context, onClose) {
                    val id = withContext(Dispatchers.IO) { applyCalendarAction(context, form.toProposal(null, EventAction.Add)) }
                    onChanged(TaskChange.Created(id))
                }
            },
        )
        is TaskRequest.Edit -> info?.let { current ->
            TaskFormDialog(
                initial = current,
                defaultDate = current.start.toLocalDate(),
                onDismiss = onClose,
                onSave = { form ->
                    runAction(scope, context, onClose) {
                        withContext(Dispatchers.IO) { applyEventUpdate(context, form.toProposal(current.id, EventAction.Update)) }
                        onChanged(TaskChange.Updated(form.applyTo(current)))
                    }
                },
            )
        }
        is TaskRequest.Delete -> info?.let { current ->
            val repeats = if (current.recurring) "\nБудут удалены все повторы этого дела." else ""
            DuoDialog(
                title = "Удалить дело?",
                text = "«${current.title}» исчезнет из календаря. Это нельзя отменить.$repeats",
                confirmText = "Удалить",
                dismissText = "Оставить",
                confirmColor = Duo.Red,
                onConfirm = {
                    runAction(scope, context, onClose) {
                        withContext(Dispatchers.IO) {
                            applyEventDelete(context, EventForm.of(current).toProposal(current.id, EventAction.Delete))
                        }
                        onChanged(TaskChange.Deleted(current.id))
                    }
                },
                onDismiss = onClose,
            )
        }
    }
}

/** Выполняет запись в календарь; при успехе закрывает окно, при ошибке говорит об этом. */
private fun runAction(scope: kotlinx.coroutines.CoroutineScope, context: Context, onClose: () -> Unit, block: suspend () -> Unit) {
    scope.launch {
        try {
            block()
            onClose()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            context.toast("Не получилось: ${t.message ?: t.javaClass.simpleName}")
        }
    }
}

private fun Context.toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

/** Поля формы дела — то, что человек может поправить руками. */
private data class EventForm(
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val allDay: Boolean,
    val location: String?,
    val description: String?,
) {
    fun toProposal(calendarEventId: Long?, action: EventAction) = EventProposal(
        id = 0,
        title = title,
        start = start,
        end = end,
        allDay = allDay,
        location = location,
        description = description,
        calendarEventId = calendarEventId,
        action = action,
    )

    fun applyTo(info: CalendarEventInfo) = info.copy(
        title = title,
        // Время повторяющегося дела календарь не меняет, поэтому показываем прежнее
        start = if (info.recurring) info.start else start,
        end = if (info.recurring) info.end else end,
        allDay = if (info.recurring) info.allDay else allDay,
        location = location,
        description = description,
    )

    companion object {
        fun of(info: CalendarEventInfo) = EventForm(
            info.title, info.start, info.end, info.allDay, info.location, info.description,
        )
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault())
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun TaskFormDialog(
    initial: CalendarEventInfo?,
    defaultDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (EventForm) -> Unit,
) {
    val context = LocalContext.current
    val editing = initial != null
    val recurring = initial?.recurring == true

    // Для нового дела — ближайший час и час длительности; для «весь день» время хранится про запас
    val defaultStart = remember { LocalTime.of((LocalTime.now().hour + 1).coerceAtMost(22), 0) }

    var title by remember { mutableStateOf(initial?.title ?: "") }
    var location by remember { mutableStateOf(initial?.location ?: "") }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var allDay by remember { mutableStateOf(initial?.allDay ?: false) }
    var startDate by remember { mutableStateOf(initial?.start?.toLocalDate() ?: defaultDate) }
    var startTime by remember {
        mutableStateOf(if (initial == null || initial.allDay) defaultStart else initial.start.toLocalTime())
    }
    var endDate by remember { mutableStateOf(initial?.end?.toLocalDate() ?: defaultDate) }
    var endTime by remember {
        mutableStateOf(if (initial == null || initial.allDay) defaultStart.plusHours(1) else initial.end.toLocalTime())
    }
    var error by remember { mutableStateOf<String?>(null) }

    // Сдвигая начало, переносим и конец: длительность дела не должна «ехать»
    fun moveStart(date: LocalDate, time: LocalTime) {
        val length = Duration.between(LocalDateTime.of(startDate, startTime), LocalDateTime.of(endDate, endTime))
        startDate = date
        startTime = time
        val newEnd = LocalDateTime.of(date, time).plus(length)
        endDate = newEnd.toLocalDate()
        endTime = newEnd.toLocalTime()
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Duo.Teal,
        focusedLabelColor = Duo.TealDark,
        cursorColor = Duo.Teal,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp),
        properties = DialogProperties(dismissOnClickOutside = false),
        title = {
            Text(
                if (editing) "Изменить дело" else "Новое дело",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; error = null },
                    label = { Text("Название") },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (recurring) {
                    Text(
                        "Повторяющееся дело: время не меняется, а название и заметки поменяются у всех повторов.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Duo.Muted,
                    )
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Весь день", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        DuoSwitch(allDay, { allDay = it; error = null })
                    }
                    Text("Начало", style = MaterialTheme.typography.labelLarge, color = Duo.Muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PickerField(startDate.format(dateFormat), Modifier.weight(1f)) {
                            pickDate(context, startDate) { moveStart(it, startTime); error = null }
                        }
                        if (!allDay) {
                            PickerField(startTime.format(timeFormat), Modifier.width(84.dp)) {
                                pickTime(context, startTime) { moveStart(startDate, it); error = null }
                            }
                        }
                    }
                    Text("Конец", style = MaterialTheme.typography.labelLarge, color = Duo.Muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PickerField(endDate.format(dateFormat), Modifier.weight(1f)) {
                            pickDate(context, endDate) { endDate = it; error = null }
                        }
                        if (!allDay) {
                            PickerField(endTime.format(timeFormat), Modifier.width(84.dp)) {
                                pickTime(context, endTime) { endTime = it; error = null }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Место") },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Заметки") },
                    minLines = 2,
                    maxLines = 5,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Duo.Red) }
            }
        },
        dismissButton = { DialogAction("Отмена", Duo.Muted, onDismiss) },
        confirmButton = {
            DialogAction(if (editing) "Сохранить" else "Добавить", Duo.Teal) {
                val cleanTitle = title.trim()
                val start = if (allDay) startDate.atStartOfDay() else LocalDateTime.of(startDate, startTime)
                val end = if (allDay) endDate.atStartOfDay() else LocalDateTime.of(endDate, endTime)
                error = when {
                    cleanTitle.isEmpty() -> "Введите название"
                    recurring -> null
                    allDay && endDate.isBefore(startDate) -> "Конец раньше начала"
                    !allDay && !end.isAfter(start) -> "Конец должен быть позже начала"
                    else -> null
                }
                if (error == null) {
                    onSave(
                        EventForm(
                            title = cleanTitle,
                            // У повторяющегося дела время остаётся прежним — календарь его всё равно не принимает
                            start = if (recurring) initial!!.start else start,
                            end = if (recurring) initial!!.end else end,
                            allDay = if (recurring) initial!!.allDay else allDay,
                            location = location.trim().takeIf { it.isNotEmpty() },
                            description = description.trim().takeIf { it.isNotEmpty() },
                        ),
                    )
                }
            }
        },
    )
}

/** Поле-кнопка с датой или временем: по нажатию открывается системный выбор. */
@Composable
private fun PickerField(text: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .height(44.dp)
            .clip(shape)
            .border(2.dp, Duo.Line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Duo.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun pickDate(context: Context, current: LocalDate, onPicked: (LocalDate) -> Unit) {
    DatePickerDialog(
        context,
        { _, year, month, day -> onPicked(LocalDate.of(year, month + 1, day)) },
        current.year, current.monthValue - 1, current.dayOfMonth,
    ).show()
}

private fun pickTime(context: Context, current: LocalTime, onPicked: (LocalTime) -> Unit) {
    TimePickerDialog(
        context,
        { _, hour, minute -> onPicked(LocalTime.of(hour, minute)) },
        current.hour, current.minute, true,
    ).show()
}
