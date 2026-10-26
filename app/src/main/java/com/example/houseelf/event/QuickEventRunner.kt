package com.example.houseelf.event

import android.app.Application
import com.example.houseelf.calendar.CalendarTools
import com.example.houseelf.calendar.EventDefaults
import com.example.houseelf.calendar.currentTimeForPrompt
import com.example.houseelf.calendar.hasCalendarPermission
import com.example.houseelf.chat.ChatViewModel
import com.google.ai.edge.litertlm.*
import java.time.LocalDate

/**
 * Один прогон Домового.Событие: отдаёт текст модели и возвращает итог того, что она сделала с календарём.
 *
 * Модель работает через те же инструменты, что и в чате ([CalendarTools]), но:
 *  - без подтверждений и вопросов — результат сразу в календаре;
 *  - недостающее (название, дата, время) подставляют [EventDefaults];
 *  - событий может быть много: модель вызывает инструмент столько раз, сколько их в тексте.
 * Разговор отдельный, переписка в чате пользователя не меняется.
 */
internal class QuickEventRunner(
    private val app: Application,
    private val chat: ChatViewModel,
) {
    suspend fun run(text: String): EventBatchSummary {
        check(hasCalendarPermission(app)) { NO_CALENDAR_ACCESS }

        val batch = EventBatch(app)
        val tools = CalendarTools(
            context = app,
            nextId = batch::nextId,
            ensurePermission = { hasCalendarPermission(app) },
            handler = batch::execute,
            defaults = EventDefaults(today = LocalDate.now()),
        )
        val config = ConversationConfig(
            systemInstruction = Contents.of(EventPrompts.systemInstruction(currentTimeForPrompt())),
            tools = listOf(tool(tools)),
        )
        chat.runIsolated(config) { conversation ->
            // Ответ модели нам не нужен: итог собирает batch из реальных записей в календарь
            conversation.sendMessageAsync(EventPrompts.userMessage(text)).collect { }
        }
        return batch.summary()
    }

    companion object {
        const val NO_CALENDAR_ACCESS = "Нет доступа к календарю. Разреши его в настройках телефона."
    }
}
