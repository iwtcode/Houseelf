package com.example.houseelf.event

import android.app.Application
import com.example.houseelf.calendar.CalendarTools
import com.example.houseelf.calendar.EventDefaults
import com.example.houseelf.calendar.formatEventWhen
import com.example.houseelf.calendar.currentTimeForPrompt
import com.example.houseelf.calendar.hasCalendarPermission
import com.example.houseelf.chat.ChatViewModel
import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.CancellationException

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

        val batch = EventBatch(app, text)
        val tools = CalendarTools(
            context = app,
            nextId = batch::nextId,
            ensurePermission = { hasCalendarPermission(app) },
            handler = batch::execute,
            defaults = EventDefaults(),
        )
        val config = ConversationConfig(
            systemInstruction = Contents.of(EventPrompts.systemInstruction(currentTimeForPrompt())),
            // Размышления здесь не нужны: диалога нет, а на телефоне они сильно замедляют ответ
            extraContext = mapOf("enable_thinking" to false),
            tools = listOf(tool(tools)),
        )
        // Маленькая модель иногда портит запись вызова инструмента (лишние служебные символы), и движок
        // не может её разобрать. Это случайный сбой: новый разговор обычно проходит. Уже записанное не повторяем.
        var failure: Throwable? = null
        var nudge = false
        for (attempt in 1..MAX_ATTEMPTS) {
            try {
                val done = batch.handled().map { "${it.title} — ${formatEventWhen(it)}" }
                chat.runIsolated(config, needsTools = true) { conversation ->
                    // Ответ модели нам не нужен: итог собирает batch из реальных записей в календарь
                    conversation.sendMessageAsync(EventPrompts.userMessage(text, done, nudge)).collect { }
                }
                failure = null
                // Модель ничего не вызвала: один раз подталкиваем её, потом сдаёмся («ничего не нашлось»)
                if (batch.summary().isEmpty && !nudge) {
                    nudge = true
                    continue
                }
                break
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (!isToolCallParseFailure(t)) throw t
                failure = t
            }
        }
        // Все попытки сорвались, и ничего не записано — сообщаем понятно, а не сырой ответ модели
        if (failure != null && batch.summary().isEmpty) throw IllegalStateException(MODEL_GLITCH)
        return batch.summary()
    }

    companion object {
        const val NO_CALENDAR_ACCESS = "Нет доступа к календарю. Разреши его в настройках телефона."
        const val MODEL_GLITCH = "Модель не смогла разобрать текст. Попробуй ещё раз или скажи чуть проще."

        private const val MAX_ATTEMPTS = 3

        /** Движок не смог прочитать вызов инструмента, который выдала модель (в тексте ошибки — её сырой ответ). */
        fun isToolCallParseFailure(t: Throwable): Boolean =
            generateSequence(t) { it.cause }.any { it.message?.contains("Failed to parse tool call", ignoreCase = true) == true }
    }
}
