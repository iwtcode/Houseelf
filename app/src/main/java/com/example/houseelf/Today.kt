package com.example.houseelf

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.houseelf.ui.theme.Duo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// ───────── Данные ─────────

/** Дело дня — событие из календаря телефона. */
data class DayTask(
    /** Стабильный ключ: событие + день (повторяющееся событие даёт разные ключи в разные дни). */
    val key: String,
    val eventId: Long,
    val title: String,
    val date: LocalDate,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val allDay: Boolean,
    val location: String?,
)

/** События на сегодня и завтра. Без разрешения на календарь возвращает пустой список. */
fun loadTasks(context: Context, today: LocalDate): List<DayTask> {
    if (!hasCalendarPermission(context)) return emptyList()
    val zone = ZoneId.systemDefault()
    val from = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val to = today.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()

    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
    ContentUris.appendId(uri, from)
    ContentUris.appendId(uri, to)

    val result = mutableListOf<DayTask>()
    try {
        context.contentResolver.query(
            uri.build(),
            arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.EVENT_LOCATION,
            ),
            null, null,
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val eventId = c.getLong(0)
                val title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "Без названия"
                val begin = c.getLong(2)
                val finish = c.getLong(3)
                val allDay = c.getInt(4) == 1
                val location = c.getString(5)?.takeIf { it.isNotBlank() }

                val start: LocalDateTime
                val end: LocalDateTime
                if (allDay) {
                    // Событие «на весь день» хранится в UTC, поэтому дату берём оттуда
                    val day = Instant.ofEpochMilli(begin).atZone(ZoneOffset.UTC).toLocalDate()
                    start = day.atStartOfDay()
                    end = day.atTime(23, 59)
                } else {
                    start = Instant.ofEpochMilli(begin).atZone(zone).toLocalDateTime()
                    end = Instant.ofEpochMilli(finish).atZone(zone).toLocalDateTime()
                }
                // Многодневное событие, начавшееся раньше, показываем как дело на сегодня
                val date = if (start.toLocalDate().isBefore(today)) today else start.toLocalDate()
                if (date.isAfter(today.plusDays(1))) continue

                result += DayTask(
                    key = "$eventId:${date.toEpochDay()}",
                    eventId = eventId,
                    title = title,
                    date = date,
                    start = start,
                    end = end,
                    allDay = allDay,
                    location = location,
                )
            }
        }
    } catch (_: SecurityException) {
        return emptyList()
    } catch (_: RuntimeException) {
        return emptyList()
    }
    return result.sortedWith(compareBy<DayTask> { it.date }.thenBy { it.start })
}

/** XP, серия дней и отметки «сделано». Лежит в настройках приложения, без базы данных. */
class ProgressStore(context: Context) {
    private val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)

    val xp: Int get() = prefs.getInt("xp", 0)

    fun doneKeys(): Set<String> = HashSet(prefs.getStringSet("done", emptySet()) ?: emptySet())

    /** Серия обрывается, если вчера ничего не было сделано. */
    fun streak(today: LocalDate): Int {
        val last = prefs.getLong("lastDay", -1L)
        return if (last >= today.toEpochDay() - 1) prefs.getInt("streak", 0) else 0
    }

    fun complete(key: String, today: LocalDate) {
        val done = doneKeys().toMutableSet()
        if (!done.add(key)) return
        // Старые отметки не копим: ключ заканчивается номером дня
        done.removeAll { (it.substringAfterLast(':').toLongOrNull() ?: 0L) < today.toEpochDay() - 7 }

        val lastDay = prefs.getLong("lastDay", -1L)
        val streak = when {
            lastDay == today.toEpochDay() -> prefs.getInt("streak", 1)
            lastDay == today.toEpochDay() - 1 -> prefs.getInt("streak", 0) + 1
            else -> 1
        }
        prefs.edit()
            .putStringSet("done", done)
            .putInt("xp", xp + XP_PER_TASK)
            .putInt("streak", streak)
            .putLong("lastDay", today.toEpochDay())
            .apply()
    }

    companion object {
        const val XP_PER_TASK = 20
    }
}

// ───────── Экран ─────────

private enum class NodeState { Done, Active, Locked }

@Composable
fun TodayScreen(
    calendarGranted: Boolean,
    refreshKey: Int,
    onRequestCalendar: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenEvent: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember { ProgressStore(context.applicationContext) }
    val today = remember { LocalDate.now() }

    var tasks by remember { mutableStateOf<List<DayTask>?>(null) }
    var done by remember { mutableStateOf(store.doneKeys()) }
    var xp by remember { mutableIntStateOf(store.xp) }
    var streak by remember { mutableIntStateOf(store.streak(today)) }

    LaunchedEffect(calendarGranted, refreshKey) {
        tasks = if (calendarGranted) withContext(Dispatchers.IO) { loadTasks(context, today) } else emptyList()
    }

    val all = tasks.orEmpty()
    val todays = all.filter { it.date == today }
    val doneToday = todays.count { it.key in done }
    val active = todays.firstOrNull { it.key !in done }
    val left = todays.size - doneToday

    Column(modifier.fillMaxSize().background(Duo.Bg)) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            // Серия и опыт
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Stat(DuoIcons.Flame, streak.toString(), Duo.AmberText, "Серия дней")
                Spacer(Modifier.width(18.dp))
                Stat(DuoIcons.Bolt, xp.toString(), Duo.Teal, "Опыт")
            }

            // Цель дня
            if (todays.isNotEmpty()) {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Цель дня", style = MaterialTheme.typography.titleSmall, color = Duo.Muted)
                        Text("$doneToday из ${todays.size} дел", style = MaterialTheme.typography.titleSmall, color = Duo.Muted)
                    }
                    DuoProgressBar(progress = doneToday.toFloat() / todays.size, color = Duo.Amber)
                }
            }

            // Домовой подсказывает, с чего начать
            val tip = when {
                tasks == null -> ""
                !calendarGranted -> "Дай доступ к календарю — покажу твои дела на сегодня."
                all.isEmpty() -> "На сегодня дел нет. Расскажи мне про них — внесу в календарь."
                todays.isEmpty() -> "На сегодня всё чисто. Завтра уже есть планы."
                active == null -> "Всё сделано. Так держать!"
                left == 1 -> "Осталось одно дело. Начнём с «${active.title}»?"
                else -> "Осталось ${dealsLeft(left)}. Начнём с «${active.title}»?"
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Hedgehog(72.dp)
                if (tip.isNotEmpty()) {
                    DuoCard(Modifier.weight(1f)) {
                        Text(tip, Modifier.padding(horizontal = 14.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            Text(
                "Путь дня",
                Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp),
                style = MaterialTheme.typography.titleLarge,
            )

            Column(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                when {
                    tasks == null -> Unit
                    !calendarGranted -> DuoButton("Разрешить доступ", onRequestCalendar, Modifier.fillMaxWidth(), icon = DuoIcons.Calendar)
                    all.isEmpty() -> DuoButton("Рассказать домовому", onOpenChat, Modifier.fillMaxWidth(), icon = DuoIcons.Chat)
                    else -> all.forEachIndexed { index, task ->
                        val state = when {
                            task.key in done -> NodeState.Done
                            task.key == active?.key -> NodeState.Active
                            else -> NodeState.Locked
                        }
                        PathItem(
                            task = task,
                            state = state,
                            today = today,
                            offset = if (index % 2 == 1) 36.dp else 0.dp,
                            onDone = {
                                store.complete(task.key, today)
                                done = store.doneKeys()
                                xp = store.xp
                                streak = store.streak(today)
                            },
                            onOpen = { onOpenEvent(task.eventId) },
                        )
                    }
                }
            }
        }

        DuoNavBar(selected = 0, onSelect = { tab ->
            when (tab) {
                1 -> onOpenChat()
                2 -> onOpenLibrary()
            }
        })
    }
}

@Composable
private fun Stat(icon: ImageVector, value: String, color: Color, description: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = description, tint = color, modifier = Modifier.size(24.dp))
        Text(value, color = color, style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp))
    }
}

@Composable
private fun PathItem(
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

private fun dealsLeft(n: Int): String {
    val m10 = n % 10
    val m100 = n % 100
    val word = when {
        m10 == 1 && m100 != 11 -> "дело"
        m10 in 2..4 && m100 !in 12..14 -> "дела"
        else -> "дел"
    }
    return "$n $word"
}
