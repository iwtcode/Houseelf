package com.example.houseelf.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.houseelf.chat.ChatMessage
import com.example.houseelf.markdown.MarkdownText
import com.example.houseelf.media.AudioAttachment
import com.example.houseelf.media.ImageAttachment
import com.example.houseelf.media.TextAttachment
import com.example.houseelf.ui.components.QuoteCard
import com.example.houseelf.ui.components.TypingDots
import com.example.houseelf.ui.theme.Duo

@Composable
internal fun MessageItem(message: ChatMessage, eventActions: EventActions, typing: Boolean) {
    val texts = message.attachments.filterIsInstance<TextAttachment>()
    val images = message.attachments.filterIsInstance<ImageAttachment>()
    val audios = message.attachments.filterIsInstance<AudioAttachment>()
    val fromUser = message.fromUser

    // Хвостик пузыря — меньший радиус у угла, из которого «говорит» автор
    val shape = if (fromUser) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 6.dp, bottomStart = 20.dp)
    } else {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 6.dp)
    }
    val hasBubble = texts.isNotEmpty() || images.isNotEmpty() || audios.isNotEmpty() || message.text.isNotEmpty() ||
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
                if (message.text.isNotEmpty()) {
                    if (fromUser) {
                        // Свои сообщения показываем как набрано: звёздочки и подчёркивания остаются как есть
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White,
                        )
                    } else {
                        // Ответы модели — markdown; пока ответ пишется (typing), незакрытая разметка не мигает
                        MarkdownText(
                            text = message.text,
                            color = Duo.Ink,
                            streaming = typing,
                        )
                    }
                } else if (!fromUser) {
                    if (typing) TypingDots() else Text("…", style = MaterialTheme.typography.bodyLarge, color = Duo.Muted)
                }
            }
        }
        message.events.forEach { EventCard(it, eventActions, Modifier.fillMaxWidth()) }
    }
}
