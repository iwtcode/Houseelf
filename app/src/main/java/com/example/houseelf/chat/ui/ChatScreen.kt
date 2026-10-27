package com.example.houseelf.chat.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.houseelf.chat.UiState
import com.example.houseelf.media.AudioAttachment
import com.example.houseelf.media.ImageAttachment
import com.example.houseelf.media.TextAttachment
import com.example.houseelf.model.modelDisplayName
import com.example.houseelf.ui.components.DuoIconButton
import com.example.houseelf.ui.components.DuoProgressBar
import com.example.houseelf.ui.components.QuoteCard
import com.example.houseelf.ui.components.SuggestionChip
import com.example.houseelf.ui.hedgehog.Hedgehog
import com.example.houseelf.ui.hedgehog.Mood
import com.example.houseelf.ui.icons.DuoIcons
import com.example.houseelf.ui.theme.Duo

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ChatScreen(
    state: UiState,
    onSend: (String) -> Unit,
    onNewChat: () -> Unit,
    onOpenLibrary: () -> Unit,
    onBack: () -> Unit,
    onPickImages: () -> Unit,
    onPickAudio: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onRemoveAttachment: (Long) -> Unit,
    eventActions: EventActions,
    modifier: Modifier = Modifier,
) {
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    // Отправка всегда убирает клавиатуру: снимаем фокус с поля и прячем её
    val submit: (String) -> Unit = { text ->
        onSend(text)
        input = ""
        focusManager.clearFocus()
        keyboard?.hide()
    }

    // Список перевёрнут (reverseLayout): индекс 0 — самое нижнее сообщение,
    // поэтому растущий ответ модели остаётся прижатым к низу экрана.
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(0)
    }

    Column(modifier.fillMaxSize().background(Duo.Bg)) {
        // Шапка
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DuoIconButton(DuoIcons.Back, "Назад", onBack)
            Hedgehog(44.dp, mood = if (state.generating) Mood.Thinking else Mood.Happy)
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = !state.generating, onClick = onOpenLibrary),
            ) {
                Text("Домовой", style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp))
                Text(
                    if (state.generating) "Думает…" else "● ${modelDisplayName(state.activeModel)} · ${state.backend}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Duo.TealDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DuoIconButton(DuoIcons.Plus, "Новый чат", onNewChat, enabled = !state.generating)
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(Duo.Line))

        // Быстрые подсказки: после ответа домового или в пустом чате. Остаются, пока человек печатает.
        val showChips = !state.generating && !state.recording &&
            (state.messages.isEmpty() || state.messages.last().fromUser.not())

        // Сообщения
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.messages.isEmpty()) {
                Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Hedgehog(96.dp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        when {
                            state.audioSupported -> "Напиши домовому, пришли фото\nили запиши голосовое"
                            state.imagesSupported -> "Напиши домовому\nили пришли фото"
                            else -> "Напиши что-нибудь домовому"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = Duo.Muted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier.fillMaxSize(),
                // Снизу оставляем место под подсказки, чтобы они не закрывали последнее сообщение
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 18.dp,
                    bottom = if (showChips) CHIPS_OVERLAY_HEIGHT + 12.dp else 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val lastId = state.messages.lastOrNull()?.id
                items(state.messages.asReversed(), key = { it.id }) { message ->
                    MessageItem(message, eventActions, typing = state.generating && message.id == lastId)
                }
            }

            // Без своего фона: сообщения видны сквозь полосу подсказок
            if (showChips) {
                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Набранный текст не трогаем: подсказка отправляется отдельно
                    val sendChip: (String) -> Unit = { text ->
                        onSend(text)
                        focusManager.clearFocus()
                        keyboard?.hide()
                    }
                    SuggestionChip("Что на сегодня?") { sendChip("Что на сегодня?") }
                    SuggestionChip("Разбить дело на шаги") { sendChip("Разбей моё ближайшее дело на шаги. Найди его в календаре на неделю.") }
                }
            }
        }

        // Нижняя панель
        Box(Modifier.fillMaxWidth().height(2.dp).background(Duo.Line))
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
        ) {
            if (state.notice != null) {
                Text(
                    state.notice,
                    color = Duo.Red,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                )
            }

            // Прикреплённое, но ещё не отправленное
            if (state.draft.isNotEmpty() || state.attaching) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    state.draft.forEach { att ->
                        when (att) {
                            is ImageAttachment -> ImageThumb(att, 72.dp, onRemove = { onRemoveAttachment(att.id) })
                            is AudioAttachment -> AudioChip(att, onRemove = { onRemoveAttachment(att.id) })
                            is TextAttachment -> QuoteCard(att, Modifier.widthIn(max = 260.dp), onRemove = { onRemoveAttachment(att.id) })
                        }
                    }
                    if (state.attaching) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            DuoProgressBar(null, Modifier.width(40.dp))
                        }
                    }
                }
            }

            if (state.recording) {
                RecordingBar(
                    elapsedMs = state.recordingMs,
                    onStop = onStopRecording,
                    onCancel = onCancelRecording,
                )
            } else {
                var menuOpen by remember { mutableStateOf(false) }
                val hasContent = input.isNotBlank() || state.draft.isNotEmpty()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.mediaSupported) {
                        Box {
                            DuoIconButton(DuoIcons.Clip, "Прикрепить", { menuOpen = true }, enabled = !state.attaching)
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false },
                                containerColor = Color.White,
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                if (state.imagesSupported) {
                                    DropdownMenuItem(
                                        text = { Text("Изображение", style = MaterialTheme.typography.labelLarge) },
                                        leadingIcon = { Icon(DuoIcons.Image, null, tint = Duo.Teal) },
                                        onClick = { menuOpen = false; onPickImages() },
                                    )
                                }
                                if (state.audioSupported) {
                                    DropdownMenuItem(
                                        text = { Text("Аудиофайл", style = MaterialTheme.typography.labelLarge) },
                                        leadingIcon = { Icon(DuoIcons.Music, null, tint = Duo.Teal) },
                                        onClick = { menuOpen = false; onPickAudio() },
                                    )
                                }
                            }
                        }
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .padding(bottom = 3.dp)
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(Duo.Bg)
                            .border(2.dp, Duo.Line, RoundedCornerShape(22.dp))
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (input.isEmpty()) {
                            Text("Сообщение", style = MaterialTheme.typography.bodyLarge, color = Duo.Muted)
                        }
                        BasicTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Duo.Ink),
                            cursorBrush = SolidColor(Duo.Teal),
                            maxLines = 5,
                        )
                    }
                    // Микрофон и «Отправить» всегда рядом: ни одна кнопка не прячется и не подменяет другую
                    if (state.audioSupported) {
                        DuoIconButton(DuoIcons.Mic, "Голосовое", onStartRecording, enabled = !state.attaching)
                    }
                    DuoIconButton(
                        DuoIcons.Send,
                        "Отправить",
                        { submit(input) },
                        filled = true,
                        enabled = hasContent && !state.generating && !state.attaching,
                    )
                }
            }
        }
    }
}

/** Высота полосы подсказок над полем ввода: сама подсказка (40) и отступ под ней (10). */
private val CHIPS_OVERLAY_HEIGHT = 50.dp
