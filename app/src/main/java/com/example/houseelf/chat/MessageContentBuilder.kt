package com.example.houseelf.chat

import com.example.houseelf.media.Attachment
import com.example.houseelf.media.AudioAttachment
import com.example.houseelf.media.ImageAttachment
import com.example.houseelf.media.TextAttachment
import com.example.houseelf.media.TextOrigin
import com.google.ai.edge.litertlm.Content
import java.io.File

/** Собирает содержимое сообщения для модели из текста пользователя и его вложений. */
internal object MessageContentBuilder {

    /** Картинки и звук — первыми, текст — последним: так модель лучше понимает, что от неё хотят. */
    fun build(prompt: String, attachments: List<Attachment>): List<Content> {
        val parts = mutableListOf<Content>()
        for (a in attachments) if (a is ImageAttachment) parts += Content.ImageBytes(File(a.path).readBytes())
        for (a in attachments) if (a is AudioAttachment) parts += Content.AudioBytes(File(a.path).readBytes())
        val quotes = attachments.filterIsInstance<TextAttachment>()
        val typed = when {
            prompt.isNotEmpty() -> prompt
            // Голосовое сообщение без текста — просим ответить на то, что сказано
            attachments.any { it is AudioAttachment && it.isVoice } -> Prompts.VOICE_HINT
            // Только выделенный текст — просим разобрать его, ничего сразу не внося в календарь
            quotes.isNotEmpty() -> Prompts.QUOTE_HINT
            else -> ""
        }
        // Выделенный текст идёт перед просьбой и отделён кавычками, чтобы модель не приняла его за команду
        val text = buildString {
            for (q in quotes) append(quoteBlock(q)).append("\n\n")
            append(typed)
        }.trim()
        if (text.isNotEmpty()) parts += Content.Text(text)
        return parts
    }

    private fun quoteBlock(q: TextAttachment): String {
        val intro = when {
            q.origin == TextOrigin.Clipboard -> "Пользователь скопировал текст:"
            q.source != null -> "Пользователь передал текст из приложения «${q.source}»:"
            else -> "Пользователь передал текст:"
        }
        return "$intro\n\"\"\"\n${q.text}\n\"\"\""
    }
}
