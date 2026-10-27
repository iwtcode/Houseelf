package com.example.houseelf.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.houseelf.calendar.EventProposal
import com.example.houseelf.chat.ChatMessage
import com.example.houseelf.markdown.MarkdownText
import com.example.houseelf.media.AudioAttachment
import com.example.houseelf.media.ImageAttachment
import com.example.houseelf.media.TextAttachment
import com.example.houseelf.ui.components.QuoteCard
import com.example.houseelf.ui.components.TypingDots
import com.example.houseelf.ui.icons.DuoIcons
import com.example.houseelf.ui.theme.Duo

/**
 * Сообщение чата. [actions] — кнопки под пузырём (копировать, повторить, удалить, прервать); без них (мини-чат)
 * пузырь остаётся без кнопок. Кнопки открываются нажатием на пузырь; у последнего ответа они видны сразу.
 * [busy] — модель сейчас отвечает: пока так, сообщения не трогаем.
 *
 * Ответ модели идёт в том порядке, в каком он писался: текст до вызова инструмента, карточка вызова, текст после.
 */
@Composable
internal fun MessageItem(
    message: ChatMessage,
    eventActions: EventActions,
    typing: Boolean,
    actions: MessageActions? = null,
    isLast: Boolean = false,
    busy: Boolean = false,
) {
    val texts = message.attachments.filterIsInstance<TextAttachment>()
    val images = message.attachments.filterIsInstance<ImageAttachment>()
    val audios = message.attachments.filterIsInstance<AudioAttachment>()
    val fromUser = message.fromUser
    var actionsOpen by remember { mutableStateOf(false) }
    val toggleActions: (() -> Unit)? = if (actions != null && !busy) ({ actionsOpen = !actionsOpen }) else null

    val parts = splitParts(message)
    val firstIsText = parts.firstOrNull() is MessagePart.Text
    val hasThinking = !fromUser && message.thinking.isNotBlank()
    val headHasContent = texts.isNotEmpty() || images.isNotEmpty() || audios.isNotEmpty() || hasThinking
    // Пустой ответ без размышлений и карточек: точки, пока модель пишет, или пометка, если ответа так и не было
    val placeholder = !fromUser && message.text.isEmpty() && message.thinking.isBlank() && message.events.isEmpty()

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = if (fromUser) Alignment.End else Alignment.Start,
    ) {
        // Первый пузырь: вложения, размышления и — если ответ начинается с текста — его первая часть
        if (headHasContent || placeholder || firstIsText) {
            Bubble(fromUser, toggleActions) {
                // Выделенный текст из другого приложения — цитатой над сообщением
                texts.forEach { QuoteCard(it, onDark = fromUser) }
                if (images.isNotEmpty()) {
                    // Одно фото показываем крупно, несколько — мелкими плитками в ряд
                    val side = when (images.size) { 1 -> 200.dp; 2 -> 130.dp; 3 -> 90.dp; else -> 64.dp }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        images.forEach { ImageThumb(it, side) }
                    }
                }
                audios.forEach { AudioChip(it, onDark = fromUser) }
                if (hasThinking) {
                    ThinkingBlock(message.thinking, streaming = typing && message.text.isEmpty())
                }
                if (firstIsText) {
                    MessageText((parts.first() as MessagePart.Text).text, fromUser, streaming = typing && parts.size == 1)
                } else if (placeholder) {
                    if (typing) TypingDots()
                    else Text(
                        if (message.stopped) "Ответ прерван" else "…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Duo.Muted,
                    )
                }
            }
        }
        parts.forEachIndexed { index, part ->
            if (index == 0 && firstIsText) return@forEachIndexed
            when (part) {
                is MessagePart.Text -> Bubble(fromUser, toggleActions) {
                    MessageText(part.text, fromUser, streaming = typing && index == parts.lastIndex)
                }
                is MessagePart.Event -> EventCard(part.event, eventActions, Modifier.fillMaxWidth())
            }
        }
        // После карточки модель ещё пишет итог — показываем, что работа идёт
        if (typing && parts.lastOrNull() is MessagePart.Event) {
            Bubble(fromUser, null) { TypingDots() }
        }
        if (!fromUser && message.stopped && (message.text.isNotEmpty() || hasThinking)) {
            Text(
                "Ответ прерван",
                Modifier.padding(horizontal = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = Duo.Muted,
                fontStyle = FontStyle.Italic,
            )
        }

        if (actions != null) {
            when {
                // Ответ пишется: единственное, что можно сделать, — прервать
                typing -> ActionRow {
                    ActionButton(DuoIcons.Stop, "Прервать", actions.onStop)
                }
                busy -> Unit
                actionsOpen || (isLast && !fromUser) -> ActionRow {
                    if (message.text.isNotEmpty()) ActionButton(DuoIcons.Copy, "Копировать") { actions.onCopy(message) }
                    if (isLast && !fromUser) ActionButton(DuoIcons.Refresh, "Повторить", actions.onRepeat)
                    ActionButton(DuoIcons.Trash, "Удалить") { actions.onDelete(message.id) }
                }
            }
        }
    }
}

/** Часть ответа в порядке показа: кусок текста или карточка вызова инструмента. */
private sealed interface MessagePart {
    class Text(val text: String) : MessagePart
    class Event(val event: EventProposal) : MessagePart
}

/** Режем текст ответа по местам, где модель вызывала инструменты ([EventProposal.textPos]). */
private fun splitParts(message: ChatMessage): List<MessagePart> {
    val text = message.text
    if (message.events.isEmpty()) return if (text.isEmpty()) emptyList() else listOf(MessagePart.Text(text))
    val parts = mutableListOf<MessagePart>()
    var pos = 0
    fun addText(end: Int) {
        val piece = text.substring(pos, end).trim()
        if (piece.isNotEmpty()) parts += MessagePart.Text(piece)
        pos = end
    }
    for (ev in message.events.sortedBy { it.textPos }) {
        addText(ev.textPos.coerceIn(pos, text.length))
        parts += MessagePart.Event(ev)
    }
    addText(text.length)
    return parts
}

/** Пузырь сообщения. Хвостик — меньший радиус у угла, из которого «говорит» автор. */
@Composable
private fun Bubble(fromUser: Boolean, onClick: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    val shape = if (fromUser) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 6.dp, bottomStart = 20.dp)
    } else {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 6.dp)
    }
    val noRipple = remember { MutableInteractionSource() }
    Column(
        Modifier
            .widthIn(max = 300.dp)
            .clip(shape)
            .background(if (fromUser) Duo.Teal else Color.White)
            .then(if (fromUser) Modifier else Modifier.border(2.dp, Duo.Line, shape))
            .then(
                if (onClick != null) Modifier.clickable(interactionSource = noRipple, indication = null, onClick = onClick)
                else Modifier
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun MessageText(text: String, fromUser: Boolean, streaming: Boolean) {
    if (fromUser) {
        // Свои сообщения показываем как набрано: звёздочки и подчёркивания остаются как есть
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = Color.White)
    } else {
        // Ответы модели — markdown; пока ответ пишется, незакрытая разметка не мигает
        MarkdownText(text = text, color = Duo.Ink, streaming = streaming)
    }
}

/** Размышления модели: всегда свёрнуты, пока человек сам не раскроет. Свернуть их можно и во время ответа. */
@Composable
private fun ThinkingBlock(text: String, streaming: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    val open = expanded
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            (if (streaming) "Думаю…" else "Размышления") + (if (open) " ▴" else " ▾"),
            Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { expanded = !expanded },
            style = MaterialTheme.typography.labelLarge,
            color = Duo.TealDark,
        )
        if (open) {
            Text(
                text.trim(),
                style = MaterialTheme.typography.bodySmall,
                color = Duo.Muted,
                fontStyle = FontStyle.Italic,
            )
        }
    }
}

@Composable
private fun ActionRow(content: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 6.dp),
    ) { content() }
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Duo.Muted, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp), color = Duo.Muted)
    }
}
