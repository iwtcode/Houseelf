package com.example.houseelf.chat.ui

import com.example.houseelf.chat.ChatMessage

/** Что можно сделать с сообщением в чате. */
class MessageActions(
    val onCopy: (ChatMessage) -> Unit,
    /** Удаляет сообщение вместе с парой (вопрос и ответ). */
    val onDelete: (Long) -> Unit,
    /** Повторить последний ответ. */
    val onRepeat: () -> Unit,
    /** Прервать ответ, который сейчас пишется. */
    val onStop: () -> Unit,
)
