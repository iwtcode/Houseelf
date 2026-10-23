package com.example.houseelf

import android.media.MediaPlayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ───────── Библиотека моделей ─────────

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

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = if (canGoBack) 4.dp else 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (canGoBack) {
                TextButton(onClick = onBack) { Text("← К чату") }
                Spacer(Modifier.width(4.dp))
            }
            Column {
                Text("Модели", style = MaterialTheme.typography.titleLarge)
                if (totalRamBytes > 0) {
                    Text(
                        "Память телефона: свободно ${formatSize(freeRamBytes)} из ${formatSize(totalRamBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        HorizontalDivider()

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (error != null) {
                item { Text(error, color = MaterialTheme.colorScheme.error) }
            }

            if (models.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Карманный домовой", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Для чата нужна модель, например Gemma 4 E2B (около 2,6 ГБ). " +
                                "Она хранится на телефоне, а сам чат работает без интернета.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                item {
                    Column {
                        Text("Установлены на телефоне", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Загрузите модель в память, чтобы общаться с ней. " +
                                "Выгрузите — чтобы освободить оперативную память.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                items(models, key = { it.fileName }) { model ->
                    val loaded = model.fileName == activeModel
                    ModelCard(
                        model = model,
                        isLoaded = loaded,
                        onOpenOrLoad = { onSelect(model.fileName) },
                        onUnload = { if (chatHasMessages) pendingUnload = true else onUnload() },
                        onDelete = { pendingDelete = model },
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Загружать при запуске", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Последняя модель сама встанет в память. " +
                                    "Если выключить — приложение откроется со списком моделей.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(checked = autoLoad, onCheckedChange = onAutoLoadChange)
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Подтверждать действия модели", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Выключено: модель сама вносит события в календарь. " +
                                    "Включено: сначала покажет карточку с кнопками «Добавить» и «Отклонить».",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(checked = confirmActions, onCheckedChange = onConfirmActionsChange)
                    }
                }
            }

            item {
                Text(
                    if (models.isEmpty()) "Как получить модель" else "Добавить ещё модель",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (canDownload) {
                item {
                    Column(Modifier.fillMaxWidth()) {
                        Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                            Text("Скачать Gemma 4 E2B с Hugging Face")
                        }
                        Text(
                            "Около 2,6 ГБ, лучше по Wi-Fi",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onPickFile, modifier = Modifier.fillMaxWidth()) {
                        Text("Загрузить из памяти телефона")
                    }
                    Text(
                        "Выберите файл модели с расширением ${ModelStorage.MODEL_EXT}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }

    if (pendingUnload) {
        AlertDialog(
            onDismissRequest = { pendingUnload = false },
            title = { Text("Выгрузить модель из памяти?") },
            text = {
                Text(
                    "Текущий диалог закроется: вместе с моделью из памяти уйдёт и его контекст. " +
                        "Файл модели останется на телефоне."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingUnload = false
                    onUnload()
                }) { Text("Выгрузить") }
            },
            dismissButton = {
                TextButton(onClick = { pendingUnload = false }) { Text("Отмена") }
            },
        )
    }

    pendingDelete?.let { model ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Удалить модель?") },
            text = {
                Text(
                    "«${model.displayName}» (${formatSize(model.sizeBytes)}) будет удалена с телефона. " +
                        (if (model.fileName == activeModel) "Текущий чат закроется. " else "") +
                        "Чтобы пользоваться ей снова, её нужно будет скачать или загрузить заново."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(model.fileName)
                    pendingDelete = null
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Отмена") }
            },
        )
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
    Card(
        onClick = onOpenOrLoad,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isLoaded) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(model.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
                formatSize(model.sizeBytes) + if (isLoaded) " · в оперативной памяти" else " · не загружена",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (isLoaded) {
                    TextButton(onClick = onOpenOrLoad) { Text("Открыть чат") }
                    TextButton(onClick = onUnload) { Text("Выгрузить") }
                } else {
                    TextButton(onClick = onOpenOrLoad) { Text("Загрузить в память") }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDelete) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

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
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(24.dp))
        if (progress != null) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
        if (hint != null) {
            Spacer(Modifier.height(8.dp))
            Text(hint, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(24.dp))
        if (extraActionText != null && onExtraAction != null) {
            Button(onClick = onExtraAction) { Text(extraActionText) }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(onClick = onCancel) { Text("Отмена") }
    }
}

/** Скачивание начинается не по Wi-Fi: пользователь решает, ждать Wi-Fi или качать по мобильной сети. */
@Composable
fun NetworkChoiceDialog(
    onWaitWifi: () -> Unit,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Нет подключения к Wi-Fi") },
        text = {
            Text(
                "Модель весит около 2,6 ГБ, а скачивание пойдёт через мобильный интернет — " +
                    "это может израсходовать трафик. Подождать Wi-Fi или продолжить?"
            )
        },
        dismissButton = { TextButton(onClick = onWaitWifi) { Text("Ждать Wi-Fi") } },
        confirmButton = { TextButton(onClick = onContinue) { Text("Продолжить") } },
    )
}

@Composable
fun LoadingScreen(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        if (text.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(text, textAlign = TextAlign.Center)
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
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Повторить") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onOpenLibrary, modifier = Modifier.fillMaxWidth()) {
            Text("Выбрать другую модель")
        }
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

@Composable
fun ChatScreen(
    state: UiState,
    onSend: (String) -> Unit,
    onNewChat: () -> Unit,
    onOpenLibrary: () -> Unit,
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

    // Список перевёрнут (reverseLayout): индекс 0 — самое нижнее сообщение,
    // поэтому растущий ответ модели остаётся прижатым к низу экрана.
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(0)
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Домовой", style = MaterialTheme.typography.titleLarge)
                Text("${modelDisplayName(state.activeModel)} · ${state.backend}", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onNewChat, enabled = !state.generating) { Text("Новый чат") }
            TextButton(onClick = onOpenLibrary, enabled = !state.generating) { Text("Модели") }
        }
        HorizontalDivider()

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.messages.isEmpty()) {
                Text(
                    if (state.mediaSupported) "Напишите домовому, пришлите фото\nили запишите голосовое" else "Напишите что-нибудь домовому",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.messages.asReversed(), key = { it.id }) { MessageBubble(it, eventActions) }
            }
        }

        Column(Modifier.fillMaxWidth().padding(8.dp)) {
            if (state.notice != null) {
                Text(
                    state.notice,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                )
            }

            // Прикреплённое, но ещё не отправленное
            if (state.draft.isNotEmpty() || state.attaching) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
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
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
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
                        Box(Modifier.padding(bottom = 4.dp)) {
                            IconButton(onClick = { menuOpen = true }, enabled = !state.attaching) {
                                Text("📎", style = MaterialTheme.typography.titleLarge)
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Изображение") },
                                    onClick = { menuOpen = false; onPickImages() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Аудиофайл") },
                                    onClick = { menuOpen = false; onPickAudio() },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Сообщение") },
                        maxLines = 5,
                    )
                    if (!hasContent && state.mediaSupported) {
                        // Пустое поле и нет вложений — вместо «Отправить» показываем микрофон
                        IconButton(
                            onClick = onStartRecording,
                            enabled = !state.attaching,
                            modifier = Modifier.padding(bottom = 4.dp),
                        ) { Text("🎤", style = MaterialTheme.typography.titleLarge) }
                    } else {
                        Button(
                            onClick = {
                                onSend(input)
                                input = ""
                            },
                            enabled = hasContent && !state.generating && !state.attaching,
                        ) { Text("Отпр.") }
                    }
                }
            }
        }
    }
}

/** Панель на время записи голоса. */
@Composable
private fun RecordingBar(elapsedMs: Long, onStop: () -> Unit, onCancel: () -> Unit) {
    val maxMs = Media.MAX_AUDIO_SECONDS * 1000L
    Column(Modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { (elapsedMs.toFloat() / maxMs).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("●", color = MaterialTheme.colorScheme.error)
            Text(
                "Запись ${formatDuration(elapsedMs)} / ${formatDuration(maxMs)}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onCancel) { Text("Отмена") }
            Button(onClick = onStop) { Text("Готово") }
        }
    }
}

@Composable
private fun ImageThumb(att: ImageAttachment, size: Dp, onRemove: (() -> Unit)? = null) {
    val bitmap = remember(att.id) { att.thumbnail.asImageBitmap() }
    Box(Modifier.size(size)) {
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
            ) { Text("✕", color = Color.White, style = MaterialTheme.typography.labelSmall) }
        }
    }
}

/** Аудио-вложение: кнопка воспроизведения, подпись и (в черновике) крестик. */
@Composable
private fun AudioChip(att: AudioAttachment, onRemove: (() -> Unit)? = null) {
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

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = if (onRemove != null) 0.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { toggle() }) { Text(if (playing) "⏹" else "▶") }
            Text(
                "${if (att.isVoice) "Голосовое" else "Аудио"} · ${formatDuration(att.durationMs)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (onRemove != null) {
                IconButton(onClick = onRemove) { Text("✕", style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, eventActions: EventActions) {
    val images = message.attachments.filterIsInstance<ImageAttachment>()
    val audios = message.attachments.filterIsInstance<AudioAttachment>()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (message.fromUser) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (images.isNotEmpty()) {
                    // Одно фото показываем крупно, несколько — мелкими плитками в ряд
                    val side = when (images.size) { 1 -> 200.dp; 2 -> 130.dp; 3 -> 90.dp; else -> 64.dp }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        images.forEach { ImageThumb(it, side) }
                    }
                }
                audios.forEach { AudioChip(it) }
                if (message.text.isNotEmpty() || !message.fromUser) {
                    Text(
                        text = message.text.ifEmpty { "…" },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                message.events.forEach { EventCard(it, eventActions) }
            }
        }
    }
}

/** Карточка события, которое модель вносит в календарь. */
@Composable
private fun EventCard(ev: EventProposal, actions: EventActions) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("📅 ${ev.title}", style = MaterialTheme.typography.titleSmall)
            Text(formatEventWhen(ev), style = MaterialTheme.typography.bodyMedium)
            if (ev.location != null) Text("📍 ${ev.location}", style = MaterialTheme.typography.bodySmall)
            if (ev.description != null) Text(ev.description, style = MaterialTheme.typography.bodySmall)

            when (ev.status) {
                EventStatus.Pending -> Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { actions.onConfirm(ev.id) }) { Text("Добавить") }
                    OutlinedButton(onClick = { actions.onDismiss(ev.id) }) { Text("Отклонить") }
                }
                EventStatus.NeedsPermission ->
                    Text("Жду разрешение на доступ к календарю…", style = MaterialTheme.typography.bodySmall)
                EventStatus.Added -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✓ Добавлено в календарь", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    if (ev.calendarEventId != null) {
                        TextButton(onClick = { actions.onView(ev.calendarEventId) }) { Text("Открыть") }
                    }
                }
                EventStatus.Failed -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Не удалось добавить: ${ev.error ?: "неизвестная ошибка"}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { actions.onConfirm(ev.id) }) { Text("Повторить") }
                        TextButton(onClick = { actions.onOpenForm(ev) }) { Text("Открыть в календаре") }
                    }
                }
                EventStatus.Dismissed ->
                    Text("Отклонено", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
