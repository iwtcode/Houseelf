package com.example.houseelf.chat.ui

import com.example.houseelf.calendar.EventProposal

/** Что можно сделать с карточкой события в чате. */
class EventActions(
    val onConfirm: (Long) -> Unit,
    val onDismiss: (Long) -> Unit,
    /** Запасной путь: открыть форму события в приложении календаря. */
    val onOpenForm: (EventProposal) -> Unit,
    /** Открыть уже созданное событие. */
    val onView: (Long) -> Unit,
    /** Изменить вручную уже созданное событие. Если null — кнопки «Изменить» и «Удалить» не показываем. */
    val onEdit: ((Long) -> Unit)? = null,
    /** Удалить вручную уже созданное событие. */
    val onDeleteEvent: ((Long) -> Unit)? = null,
)
