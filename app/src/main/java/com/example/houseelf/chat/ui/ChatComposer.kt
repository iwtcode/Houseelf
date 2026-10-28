package com.example.houseelf.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.example.houseelf.chat.UiState
import com.example.houseelf.media.AudioAttachment
import com.example.houseelf.media.ImageAttachment
import com.example.houseelf.media.TextAttachment
import com.example.houseelf.ui.components.DuoIconButton
import com.example.houseelf.ui.components.DuoProgressBar
import com.example.houseelf.ui.components.QuoteCard
import com.example.houseelf.ui.icons.DuoIcons
import com.example.houseelf.ui.theme.Duo

/** Отправка, после которой клавиатура убирается: снимаем фокус с поля и прячем её. */
@Composable
fun rememberSendAndHideKeyboard(onSend: (String) -> Unit): (String) -> Unit {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    return { text ->
        onSend(text)
        focusManager.clearFocus()
        keyboard?.hide()
    }
}

/**
 * Нижняя панель чата — одна на главный чат и мини-чат: сообщение об ошибке, вложения, поле ввода,
 * скрепка, микрофон, «Отправить»/«Прервать».
 *
 * [aboveInput] — то, что нужно только хозяину панели (например, подсказки мини-чата).
 * С [expandableQuotes] выделенный текст показывается на всю ширину и раскрывается по нажатию.
 */
@Composable
fun ChatComposer(
    state: UiState,
    actions: ChatActions,
    messageActions: MessageActions,
    modifier: Modifier = Modifier,
    placeholder: String = "Сообщение",
    notice: String? = state.notice,
    expandableQuotes: Boolean = false,
    aboveInput: @Composable () -> Unit = {},
) {
    var input by rememberSaveable { mutableStateOf("") }
    val send = rememberSendAndHideKeyboard(actions.onSend)

    Column(modifier) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(Duo.Line))
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
        ) {
            if (notice != null) {
                Text(
                    notice,
                    color = Duo.Red,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                )
            }

            DraftAttachments(state, actions.onRemoveAttachment, expandableQuotes)
            aboveInput()

            if (state.recording) {
                RecordingBar(
                    elapsedMs = state.recordingMs,
                    onStop = actions.onStopRecording,
                    onCancel = actions.onCancelRecording,
                )
            } else {
                InputRow(
                    state = state,
                    actions = actions,
                    messageActions = messageActions,
                    input = input,
                    onInputChange = { input = it },
                    placeholder = placeholder,
                    onSubmit = {
                        send(input)
                        input = ""
                    },
                )
            }
        }
    }
}

/** Прикреплённое, но ещё не отправленное. */
@Composable
private fun DraftAttachments(state: UiState, onRemove: (Long) -> Unit, expandableQuotes: Boolean) {
    if (state.draft.isEmpty() && !state.attaching) return

    val quotes = if (expandableQuotes) state.draft.filterIsInstance<TextAttachment>() else emptyList()
    val inline = if (expandableQuotes) state.draft.filterNot { it is TextAttachment } else state.draft

    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        quotes.forEach { quote ->
            QuoteCard(quote, Modifier.fillMaxWidth(), expandable = true, onRemove = { onRemove(quote.id) })
        }
        if (inline.isNotEmpty() || state.attaching) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                inline.forEach { att ->
                    when (att) {
                        is ImageAttachment -> ImageThumb(att, 72.dp, onRemove = { onRemove(att.id) })
                        is AudioAttachment -> AudioChip(att, onRemove = { onRemove(att.id) })
                        is TextAttachment -> QuoteCard(att, Modifier.widthIn(max = 260.dp), onRemove = { onRemove(att.id) })
                    }
                }
                if (state.attaching) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        DuoProgressBar(null, Modifier.width(40.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun InputRow(
    state: UiState,
    actions: ChatActions,
    messageActions: MessageActions,
    input: String,
    onInputChange: (String) -> Unit,
    placeholder: String,
    onSubmit: () -> Unit,
) {
    val hasContent = input.isNotBlank() || state.draft.isNotEmpty()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.mediaSupported) AttachMenu(state, actions)
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
                Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = Duo.Muted)
            }
            BasicTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Duo.Ink),
                cursorBrush = SolidColor(Duo.Teal),
                maxLines = 5,
            )
        }
        // Микрофон и «Отправить» всегда рядом: ни одна кнопка не прячется и не подменяет другую
        if (state.audioSupported) {
            DuoIconButton(DuoIcons.Mic, "Голосовое", actions.onStartRecording, enabled = !state.attaching)
        }
        if (state.generating) {
            // Пока модель отвечает, вместо «Отправить» — «Прервать»
            DuoIconButton(DuoIcons.Stop, "Прервать", messageActions.onStop, filled = true)
        } else {
            DuoIconButton(
                DuoIcons.Send,
                "Отправить",
                onSubmit,
                filled = true,
                enabled = hasContent && !state.attaching,
            )
        }
    }
}

@Composable
private fun AttachMenu(state: UiState, actions: ChatActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        DuoIconButton(DuoIcons.Clip, "Прикрепить", { open = true }, enabled = !state.attaching)
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp),
        ) {
            if (state.imagesSupported) {
                DropdownMenuItem(
                    text = { Text("Изображение", style = MaterialTheme.typography.labelLarge) },
                    leadingIcon = { Icon(DuoIcons.Image, null, tint = Duo.Teal) },
                    onClick = { open = false; actions.onPickImages() },
                )
            }
            if (state.audioSupported) {
                DropdownMenuItem(
                    text = { Text("Аудиофайл", style = MaterialTheme.typography.labelLarge) },
                    leadingIcon = { Icon(DuoIcons.Music, null, tint = Duo.Teal) },
                    onClick = { open = false; actions.onPickAudio() },
                )
            }
        }
    }
}
