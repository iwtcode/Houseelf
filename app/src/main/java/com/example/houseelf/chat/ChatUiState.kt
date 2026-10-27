package com.example.houseelf.chat

import com.example.houseelf.calendar.EventProposal
import com.example.houseelf.media.Attachment
import com.example.houseelf.model.ModelInfo
import com.google.ai.edge.litertlm.*

enum class Phase { Checking, Library, Downloading, Importing, Loading, Chat, Error }

data class ChatMessage(
    val id: Long,
    val fromUser: Boolean,
    val text: String,
    val attachments: List<Attachment> = emptyList(),
    /** События, которые модель вносила в календарь в этом ответе. */
    val events: List<EventProposal> = emptyList(),
)

data class UiState(
    val phase: Phase = Phase.Checking,
    /** 0..1, либо null — когда прогресс неизвестен. */
    val progress: Float? = null,
    val progressText: String = "",
    val error: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val generating: Boolean = false,
    val backend: String = "",
    /** Все модели, лежащие на телефоне. */
    val models: List<ModelInfo> = emptyList(),
    /** Файл модели, которая сейчас загружена в движок (null — никакая). */
    val activeModel: String? = null,
    /** Файл модели, которая загружается прямо сейчас. */
    val loadingModel: String? = null,
    /** Показать окно «ждать Wi-Fi или продолжить по мобильной сети». */
    val askNetworkChoice: Boolean = false,
    /** Размер скачиваемой модели для текста этого окна. */
    val networkChoiceSize: String = "",
    /** Скачивание сейчас ждёт Wi-Fi. */
    val waitingWifi: Boolean = false,
    /** Оперативная память телефона: свободно / всего (обновляется при открытии списка моделей). */
    val freeRamBytes: Long = 0,
    val totalRamBytes: Long = 0,
    /** Загружать последнюю модель в память при запуске приложения. */
    val autoLoad: Boolean = true,
    /** Спрашивать подтверждение, прежде чем модель что-то сделает (по умолчанию — нет). */
    val confirmActions: Boolean = false,
    /** Нужно показать системный запрос разрешения на календарь. */
    val needCalendarPermission: Boolean = false,
    /** Движок загружен с поддержкой изображений. */
    val imagesSupported: Boolean = false,
    /** Движок загружен с поддержкой звука (голосовые сообщения и аудиофайлы). */
    val audioSupported: Boolean = false,
    /** Сколько изображений модель принимает в одном сообщении. */
    val maxImages: Int = 0,
    /** Модель умеет вносить события в календарь. */
    val toolsSupported: Boolean = false,
    /** Вложения, прикреплённые к ещё не отправленному сообщению. */
    val draft: List<Attachment> = emptyList(),
    /** Файлы из выбора сейчас обрабатываются. */
    val attaching: Boolean = false,
    /** Идёт запись с микрофона. */
    val recording: Boolean = false,
    val recordingMs: Long = 0,
    /** Короткое сообщение пользователю над полем ввода (ошибка вложения и т. п.). */
    val notice: String? = null,
) {
    /** Есть ли что прикреплять к сообщению. */
    val mediaSupported: Boolean get() = imagesSupported || audioSupported
}
