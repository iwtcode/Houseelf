package com.example.houseelf.calendar

import java.time.LocalDateTime

enum class EventStatus {
    /** Включено подтверждение действий: ждём, пока пользователь нажмёт «Добавить». */
    Pending,
    /** Ждём разрешение на доступ к календарю. */
    NeedsPermission,
    /** Действие выполнено (событие добавлено, изменено или удалено — смотри [EventProposal.action]). */
    Added,
    Failed,
    Dismissed,
}

/** Что модель делает с событием в календаре. */
enum class EventAction { Add, Update, Delete }

/** Событие, которое модель вносит в календарь, меняет или удаляет. */
data class EventProposal(
    val id: Long,
    val title: String,
    val start: LocalDateTime,
    /** Для событий «на весь день» — последний день включительно (время игнорируется). */
    val end: LocalDateTime,
    val allDay: Boolean,
    val location: String?,
    val description: String?,
    val status: EventStatus = EventStatus.Pending,
    val error: String? = null,
    /**
     * Номер события в календаре телефона: у нового — после записи,
     * у изменяемого и удаляемого — известен сразу.
     */
    val calendarEventId: Long? = null,
    val action: EventAction = EventAction.Add,
    /** Повторяющееся событие: изменение текста и удаление касаются всех повторов. */
    val recurring: Boolean = false,
    /** Для изменения: как событие выглядело раньше (название и время), если это поменялось. */
    val before: String? = null,
    /**
     * Сколько символов ответа модели уже было написано, когда она вызвала инструмент. По этому месту карточка
     * встаёт в чате: текст до вызова — над ней, текст после — под ней.
     */
    val textPos: Int = 0,
)

/** Событие, прочитанное из календаря телефона. Для «весь день» [end] — последний день включительно. */
data class CalendarEventInfo(
    val id: Long,
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val allDay: Boolean,
    val location: String?,
    val description: String?,
    val recurring: Boolean,
    /** Календарь позволяет менять и удалять это событие. */
    val editable: Boolean,
)
