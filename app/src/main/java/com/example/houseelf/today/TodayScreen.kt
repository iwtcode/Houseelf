package com.example.houseelf.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.houseelf.ui.components.DuoButton
import com.example.houseelf.ui.components.DuoCard
import com.example.houseelf.ui.components.DuoNavBar
import com.example.houseelf.ui.components.DuoProgressBar
import com.example.houseelf.ui.hedgehog.Hedgehog
import com.example.houseelf.ui.icons.DuoIcons
import com.example.houseelf.ui.theme.Duo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

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
    var started by remember { mutableStateOf(store.startedKeys()) }
    var stepsDone by remember { mutableStateOf(store.stepsDone()) }
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
                all.isEmpty() -> "В ближайшие $TASKS_AHEAD_DAYS дней дел нет. Расскажи мне про них — внесу в календарь."
                todays.isEmpty() -> "На сегодня всё чисто. Дальше уже есть планы."
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
                "Ближайшие $TASKS_AHEAD_DAYS дней",
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
                        // Дни идут подряд, поэтому над первым делом каждого дня — заголовок с датой
                        if (index == 0 || all[index - 1].date != task.date) {
                            DayHeader(task.date, today)
                        }
                        val state = when {
                            task.key in done -> NodeState.Done
                            task.key == active?.key -> NodeState.Active
                            else -> NodeState.Locked
                        }
                        PathItem(
                            task = task,
                            state = state,
                            offset = if (index % 2 == 1) 36.dp else 0.dp,
                            started = task.key in started,
                            stepsDone = stepsDone[task.key] ?: 0,
                            actions = TaskActions(
                                onStart = {
                                    store.start(task.key)
                                    started = store.startedKeys()
                                },
                                onNextStep = {
                                    store.nextStep(task.key)
                                    stepsDone = store.stepsDone()
                                },
                                onDone = {
                                    store.complete(task.key, today)
                                    done = store.doneKeys()
                                    stepsDone = store.stepsDone()
                                    xp = store.xp
                                    streak = store.streak(today)
                                },
                                onOpen = { onOpenEvent(task.eventId) },
                            ),
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
