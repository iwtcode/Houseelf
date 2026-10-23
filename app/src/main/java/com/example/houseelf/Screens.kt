package com.example.houseelf

import android.media.MediaPlayer
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.houseelf.ui.theme.Duo

// ───────── Мозг домового (библиотека моделей) ─────────

@Composable
fun LibraryScreen(
    models: List<ModelInfo>,
    activeModel: String?,
    error: String?,
    freeRamBytes: Long,
    totalRamBytes: Long,
    autoLoad: Boolean,
    confirmActions: Boolean,
    chatHasMessages: Boolean,
    onSelect: (String) -> Unit,
    onUnload: () -> Unit,
    onAutoLoadChange: (Boolean) -> Unit,
    onConfirmActionsChange: (Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onDownload: () -> Unit,
    onPickFile: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDelete by remember { mutableStateOf<ModelInfo?>(null) }
    var pendingUnload by remember { mutableStateOf(false) }
    val canGoBack = activeModel != null
    val canDownload = models.none { it.fileName == ModelStorage.MODEL_FILE_NAME }

    Column(modifier.fillMaxSize().background(Duo.Bg)) {
        ScreenHeader(
            title = "Мозг домового",
            subtitle = if (totalRamBytes > 0) {
                "Свободно ${formatSize(freeRamBytes)} из ${formatSize(totalRamBytes)} памяти"
            } else null,
            onBack = if (canGoBack) onBack else null,
        )

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (error != null) {
                item {
                    DuoCard(Modifier.fillMaxWidth(), borderColor = Duo.Red) {
                        Text(
                            error,
                            Modifier.padding(14.dp),
                            color = Duo.Red,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            item {
                DuoCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Hedgehog(64.dp)
                        Column {
                            Text(
                                if (models.isEmpty()) "Нужна модель" else "Думает на телефоне",
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                            )
                            Text(
                                if (models.isEmpty()) {
                                    "Скачай Gemma 4 E2B (около 2,6 ГБ) или загрузи свою. Дальше чат работает без интернета."
                                } else {
                                    "Переписки остаются у вас. Интернет нужен только для скачивания модели."
                                },
                                Modifier.padding(top = 2.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = Duo.Muted,
                            )
                        }
                    }
                }
            }

            items(models, key = { it.fileName }) { model ->
                ModelCard(
                    model = model,
                    isLoaded = model.fileName == activeModel,
                    onOpenOrLoad = { onSelect(model.fileName) },
                    onUnload = { if (chatHasMessages) pendingUnload = true else onUnload() },
                    onDelete = { pendingDelete = model },
                )
            }

            if (models.isNotEmpty()) {
                item {
                    DuoCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            SettingRow(
                                title = "Загружать при запуске",
                                hint = "Последняя модель сама встанет в память",
                                checked = autoLoad,
                                onChange = onAutoLoadChange,
                                divider = true,
                            )
                            SettingRow(
                                title = "Спрашивать перед действиями",
                                hint = "Показать карточку с кнопкой «Добавить» вместо записи в календарь",
                                checked = confirmActions,
                                onChange = onConfirmActionsChange,
                                divider = false,
                            )
                        }
                    }
                }
            }

            item {
                Column(
                    Modifier.fillMaxWidth().dashedBorder(Duo.Dashed).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        if (models.isEmpty()) "Как получить модель" else "Другая модель",
                        style = MaterialTheme.typography.titleSmall,
                        color = Duo.Muted,
                    )
                    if (canDownload) {
                        DuoButton(
                            "Скачать Gemma 4 E2B",
                            onDownload,
                            Modifier.fillMaxWidth(),
                            icon = DuoIcons.Download,
                        )
                        Text(
                            "Около 2,6 ГБ — лучше по Wi-Fi",
                            style = MaterialTheme.typography.bodySmall,
                            color = Duo.Muted,
                        )
                    }
                    DuoButton(
                        "Из памяти телефона",
                        onPickFile,
                        Modifier.fillMaxWidth(),
                        kind = if (canDownload) DuoKind.Outline else DuoKind.Primary,
                        icon = DuoIcons.Folder,
                    )
                    Text(
                        "Файл модели с расширением ${ModelStorage.MODEL_EXT}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Duo.Muted,
                    )
                }
            }
        }
    }

    if (pendingUnload) {
        DuoDialog(
            title = "Выгрузить модель из памяти?",
            text = "Текущий диалог закроется: вместе с моделью из памяти уйдёт и его контекст. " +
                "Файл модели останется на телефоне.",
            confirmText = "Выгрузить",
            dismissText = "Отмена",
            onConfirm = {
                pendingUnload = false
                onUnload()
            },
            onDismiss = { pendingUnload = false },
        )
    }

    pendingDelete?.let { model ->
        DuoDialog(
            title = "Удалить модель?",
            text = "«${model.displayName}» (${formatSize(model.sizeBytes)}) будет удалена с телефона. " +
                (if (model.fileName == activeModel) "Текущий чат закроется. " else "") +
                "Чтобы пользоваться ей снова, её нужно будет скачать или загрузить заново.",
            confirmText = "Удалить",
            confirmColor = Duo.Red,
            dismissText = "Отмена",
            onConfirm = {
                onDelete(model.fileName)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun SettingRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit, divider: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .drawBehind {
                if (divider) {
                    drawLine(
                        color = Duo.Track,
                        start = androidx.compose.ui.geometry.Offset(0f, size.height),
                        end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
            }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = Duo.Muted)
        }
        Spacer(Modifier.width(12.dp))
        DuoSwitch(checked, onChange)
    }
}

@Composable
private fun ModelCard(
    model: ModelInfo,
    isLoaded: Boolean,
    onOpenOrLoad: () -> Unit,
    onUnload: () -> Unit,
    onDelete: () -> Unit,
) {
    DuoCard(Modifier.fillMaxWidth(), borderColor = if (isLoaded) Duo.Teal else Duo.Line) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconBadge(
                    DuoIcons.Chip,
                    background = if (isLoaded) Duo.TealSoft else Duo.Track,
                    tint = if (isLoaded) Duo.TealDark else Duo.Muted,
                )
                Column(Modifier.weight(1f)) {
                    Text(model.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        (if (isLoaded) "● Загружена в память" else "Не в памяти") + " · " + formatSize(model.sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isLoaded) Duo.TealDark else Duo.Muted,
                    )
                }
                DuoIconButton(DuoIcons.Trash, "Удалить модель", onDelete, tint = Duo.Red)
            }
            Spacer(Modifier.height(14.dp))
            if (isLoaded) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DuoButton("Открыть чат", onOpenOrLoad, Modifier.weight(1f))
                    DuoButton("Выгрузить", onUnload, Modifier.weight(1f), kind = DuoKind.Outline)
                }
            } else {
                DuoButton("Загрузить в память", onOpenOrLoad, Modifier.fillMaxWidth())
            }
        }
    }
}

// ───────── Диалоги ─────────

@Composable
private fun DuoDialog(
    title: String,
    text: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmColor: Color = Duo.Teal,
    onDismissRequest: () -> Unit = onDismiss,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp),
        title = { Text(title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp)) },
        text = { Text(text, style = MaterialTheme.typography.bodyMedium, color = Duo.Muted) },
        dismissButton = { DialogAction(dismissText, Duo.Muted, onDismiss) },
        confirmButton = { DialogAction(confirmText, confirmColor, onConfirm) },
    )
}

@Composable
private fun DialogAction(text: String, color: Color, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(text.uppercase(), color = color, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp)
    }
}

/** Скачивание начинается не по Wi-Fi: пользователь решает, ждать Wi-Fi или качать по мобильной сети. */
@Composable
fun NetworkChoiceDialog(
    onWaitWifi: () -> Unit,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    DuoDialog(
        title = "Нет подключения к Wi-Fi",
        text = "Модель весит около 2,6 ГБ, а скачивание пойдёт через мобильный интернет — " +
            "это может израсходовать трафик. Подождать Wi-Fi или продолжить?",
        confirmText = "Продолжить",
        dismissText = "Ждать Wi-Fi",
        onConfirm = onContinue,
        onDismiss = onWaitWifi,
        onDismissRequest = onDismiss,
    )
}

// ───────── Загрузка, прогресс, ошибка ─────────

@Composable
fun ProgressScreen(
    title: String,
    progress: Float?,
    text: String,
    hint: String?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    extraActionText: String? = null,
    onExtraAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxSize().background(Duo.Bg).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Hedgehog(96.dp, mood = Mood.Thinking, bob = true)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        DuoProgressBar(progress)
        Spacer(Modifier.height(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        if (hint != null) {
            Spacer(Modifier.height(6.dp))
            Text(hint, style = MaterialTheme.typography.bodySmall, color = Duo.Muted, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(28.dp))
        if (extraActionText != null && onExtraAction != null) {
            DuoButton(extraActionText, onExtraAction, Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
        }
        DuoButton("Отмена", onCancel, Modifier.fillMaxWidth(), kind = DuoKind.Outline)
    }
}

@Composable
fun LoadingScreen(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().background(Duo.Bg).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Hedgehog(96.dp, mood = Mood.Thinking, bob = true)
        if (text.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge, color = Duo.Muted, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun ErrorScreen(
    error: String,
    onRetry: () -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().background(Duo.Bg).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Hedgehog(96.dp, mood = Mood.Sad)
        Spacer(Modifier.height(16.dp))
        Text("Что-то пошло не так", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        DuoCard(Modifier.fillMaxWidth(), borderColor = Duo.Red) {
            Text(
                error,
                Modifier.padding(14.dp),
                color = Duo.Red,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(28.dp))
        DuoButton("Повторить", onRetry, Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        DuoButton("Выбрать другую модель", onOpenLibrary, Modifier.fillMaxWidth(), kind = DuoKind.Outline)
    }
}

// ───────── Чат ─────────

/** Что можно сделать с карточкой события в чате. */
class EventActions(
    val onConfirm: (Long) -> Unit,
    val onDismiss: (Long) -> Unit,
    /** Запасной путь: открыть форму события в приложении календаря. */
    val onOpenForm: (EventProposal) -> Unit,
    /** Открыть уже созданное событие. */
    val onView: (Long) -> Unit,
)

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
                        if (state.mediaSupported) "Напиши домовому, пришли фото\nили запиши голосовое" else "Напиши что-нибудь домовому",
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
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val lastId = state.messages.lastOrNull()?.id
                items(state.messages.asReversed(), key = { it.id }) { message ->
                    MessageItem(message, eventActions, typing = state.generating && message.id == lastId)
                }
            }
        }

        // Быстрые подсказки: после ответа домового или в пустом чате
        val showChips = input.isEmpty() && !state.generating && !state.recording &&
            (state.messages.isEmpty() || state.messages.last().fromUser.not())
        if (showChips) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SuggestionChip("Что на сегодня?") { submit("Что на сегодня?") }
                SuggestionChip("Разбить дело на шаги") { submit("Разбей моё дело на шаги") }
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
                                DropdownMenuItem(
                                    text = { Text("Изображение", style = MaterialTheme.typography.labelLarge) },
                                    leadingIcon = { Icon(DuoIcons.Image, null, tint = Duo.Teal) },
                                    onClick = { menuOpen = false; onPickImages() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Аудиофайл", style = MaterialTheme.typography.labelLarge) },
                                    leadingIcon = { Icon(DuoIcons.Music, null, tint = Duo.Teal) },
                                    onClick = { menuOpen = false; onPickAudio() },
                                )
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
                    if (state.mediaSupported) {
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

@Composable
private fun SuggestionChip(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(Color.White)
            .border(2.dp, Duo.Line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp), color = Duo.TealDark)
    }
}

/** Панель на время записи голоса. */
@Composable
private fun RecordingBar(elapsedMs: Long, onStop: () -> Unit, onCancel: () -> Unit) {
    val maxMs = Media.MAX_AUDIO_SECONDS * 1000L
    val pulse = rememberInfiniteTransition(label = "rec")
    val dotAlpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(animation = tween(700), repeatMode = RepeatMode.Reverse),
        label = "recDot",
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DuoProgressBar(progress = (elapsedMs.toFloat() / maxMs).coerceIn(0f, 1f), color = Duo.Red)
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(12.dp)
                    .graphicsLayer { alpha = dotAlpha }
                    .clip(CircleShape)
                    .background(Duo.Red),
            )
            Text(
                "Запись ${formatDuration(elapsedMs)} / ${formatDuration(maxMs)}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            DuoIconButton(DuoIcons.Close, "Отменить запись", onCancel)
            DuoButton("Готово", onStop, Modifier.width(120.dp), icon = DuoIcons.Check, height = 44.dp)
        }
    }
}

@Composable
private fun ImageThumb(att: ImageAttachment, side: Dp, onRemove: (() -> Unit)? = null) {
    val bitmap = remember(att.id) { att.thumbnail.asImageBitmap() }
    Box(Modifier.size(side)) {
        Image(
            bitmap = bitmap,
            contentDescription = "Изображение",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
        )
        if (onRemove != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(DuoIcons.Close, contentDescription = "Убрать", tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** Аудио-вложение: кнопка воспроизведения, подпись и (в черновике) крестик. */
@Composable
private fun AudioChip(att: AudioAttachment, onRemove: (() -> Unit)? = null, onDark: Boolean = false) {
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }

    fun stop() {
        runCatching { player?.release() }
        player = null
        playing = false
    }

    fun toggle() {
        if (playing) {
            stop()
            return
        }
        val mp = MediaPlayer()
        try {
            mp.setDataSource(att.path)
            mp.setOnCompletionListener { stop() }
            mp.prepare()
            mp.start()
            player = mp
            playing = true
        } catch (_: Throwable) {
            runCatching { mp.release() }
        }
    }

    DisposableEffect(Unit) { onDispose { stop() } }

    val shape = RoundedCornerShape(24.dp)
    val textColor = if (onDark) Color.White else Duo.Ink
    Row(
        Modifier
            .clip(shape)
            .background(if (onDark) Color.White.copy(alpha = 0.18f) else Duo.Bg)
            .then(if (onDark) Modifier else Modifier.border(2.dp, Duo.Line, shape))
            .padding(start = 6.dp, end = if (onRemove != null) 4.dp else 14.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (onDark) Color.White else Duo.Teal)
                .clickable { toggle() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (playing) DuoIcons.Stop else DuoIcons.Play,
                contentDescription = if (playing) "Остановить" else "Слушать",
                tint = if (onDark) Duo.Teal else Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            "${if (att.isVoice) "Голосовое" else "Аудио"} · ${formatDuration(att.durationMs)}",
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
        )
        if (onRemove != null) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(DuoIcons.Close, contentDescription = "Убрать", tint = Duo.Muted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun MessageItem(message: ChatMessage, eventActions: EventActions, typing: Boolean) {
    val images = message.attachments.filterIsInstance<ImageAttachment>()
    val audios = message.attachments.filterIsInstance<AudioAttachment>()
    val fromUser = message.fromUser

    // Хвостик пузыря — меньший радиус у угла, из которого «говорит» автор
    val shape = if (fromUser) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 6.dp, bottomStart = 20.dp)
    } else {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 6.dp)
    }
    val hasBubble = images.isNotEmpty() || audios.isNotEmpty() || message.text.isNotEmpty() ||
        (!fromUser && message.events.isEmpty())

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = if (fromUser) Alignment.End else Alignment.Start,
    ) {
        if (hasBubble) {
            Column(
                Modifier
                    .widthIn(max = 300.dp)
                    .clip(shape)
                    .background(if (fromUser) Duo.Teal else Color.White)
                    .then(if (fromUser) Modifier else Modifier.border(2.dp, Duo.Line, shape))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (images.isNotEmpty()) {
                    // Одно фото показываем крупно, несколько — мелкими плитками в ряд
                    val side = when (images.size) { 1 -> 200.dp; 2 -> 130.dp; 3 -> 90.dp; else -> 64.dp }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        images.forEach { ImageThumb(it, side) }
                    }
                }
                audios.forEach { AudioChip(it, onDark = fromUser) }
                if (message.text.isNotEmpty()) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (fromUser) Color.White else Duo.Ink,
                    )
                } else if (!fromUser) {
                    if (typing) TypingDots() else Text("…", style = MaterialTheme.typography.bodyLarge, color = Duo.Muted)
                }
            }
        }
        message.events.forEach { EventCard(it, eventActions, Modifier.fillMaxWidth()) }
    }
}

/** Карточка события, которое модель вносит в календарь. */
@Composable
private fun EventCard(ev: EventProposal, actions: EventActions, modifier: Modifier = Modifier) {
    val border = when (ev.status) {
        EventStatus.Failed -> Duo.Red
        EventStatus.Dismissed -> Duo.Line
        else -> Duo.Teal
    }
    DuoCard(modifier, borderColor = border) {
        Column(Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconBadge(
                    DuoIcons.Calendar,
                    background = if (ev.status == EventStatus.Dismissed) Duo.Track else Duo.TealSoft,
                    tint = if (ev.status == EventStatus.Dismissed) Duo.Muted else Duo.TealDark,
                )
                Column(Modifier.weight(1f)) {
                    Text(ev.title, style = MaterialTheme.typography.titleMedium)
                    Text(formatEventWhen(ev), style = MaterialTheme.typography.bodySmall, color = Duo.Muted)
                }
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
                Text(
                    ev.description,
                    Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Duo.Muted,
                )
            }

            Spacer(Modifier.height(12.dp))
            when (ev.status) {
                EventStatus.Pending -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DuoButton("Добавить", { actions.onConfirm(ev.id) }, Modifier.weight(1f))
                    DuoButton("Не то", { actions.onDismiss(ev.id) }, Modifier.weight(1f), kind = DuoKind.Outline)
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
                        "Добавлено в календарь",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                        color = Duo.TealDark,
                    )
                    if (ev.calendarEventId != null) {
                        DuoButton(
                            "Открыть",
                            { actions.onView(ev.calendarEventId) },
                            Modifier.width(112.dp),
                            kind = DuoKind.Outline,
                            height = 40.dp,
                        )
                    }
                }
                EventStatus.Failed -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Не удалось добавить: ${ev.error ?: "неизвестная ошибка"}",
                        color = Duo.Red,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DuoButton("Повторить", { actions.onConfirm(ev.id) }, Modifier.weight(1f))
                        DuoButton("В календаре", { actions.onOpenForm(ev) }, Modifier.weight(1f), kind = DuoKind.Outline)
                    }
                }
                EventStatus.Dismissed ->
                    Text("Отклонено", style = MaterialTheme.typography.bodySmall, color = Duo.Muted)
            }
        }
    }
}
