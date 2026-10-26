package com.example.houseelf.media

import android.graphics.Bitmap
import com.example.houseelf.chat.ChatViewModel

/** Вложение к сообщению. Сами данные лежат файлом в кэше приложения, в памяти — только путь и превью. */
sealed interface Attachment {
    val id: Long
    val path: String
}

data class ImageAttachment(
    override val id: Long,
    override val path: String,
    /** Маленькая копия для показа в чате. */
    val thumbnail: Bitmap,
) : Attachment

data class AudioAttachment(
    override val id: Long,
    override val path: String,
    val durationMs: Long,
    /** true — записано микрофоном (голосовой ввод), false — выбранный файл. */
    val isVoice: Boolean,
) : Attachment

/** Откуда пришёл текст. */
enum class TextOrigin { Selection, Share, Clipboard }

/**
 * Текст из другого приложения — выделенный, отправленный через «Поделиться» или скопированный.
 * Файла нет: текст короткий (не длиннее [ChatViewModel.MAX_CAPTURE_CHARS]) и лежит прямо в сообщении.
 */
data class TextAttachment(
    override val id: Long,
    val text: String,
    /** Название приложения-источника («Telegram») или null, если система его не сообщила. */
    val source: String?,
    val origin: TextOrigin,
    /** Исходный текст был длиннее лимита, взято только начало. */
    val truncated: Boolean = false,
    override val path: String = "",
) : Attachment {
    /** Подпись над цитатой. */
    fun caption(): String {
        val base = when {
            origin == TextOrigin.Clipboard -> "Буфер обмена"
            source != null -> "Из «$source»"
            origin == TextOrigin.Share -> "Отправлено через «Поделиться»"
            else -> "Выделенный текст"
        }
        return if (truncated) "$base · начало текста" else base
    }
}
