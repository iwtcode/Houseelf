package com.example.houseelf.today

import android.content.Context

/** Шаги (подзадачи) дел. Календарь телефона их не хранит, поэтому они лежат в настройках приложения. */
class SubtaskStore(context: Context) {
    private val prefs = context.getSharedPreferences("subtasks", Context.MODE_PRIVATE)

    fun steps(eventId: Long): List<String> =
        prefs.getString(key(eventId), null)?.split(SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()

    fun save(eventId: Long, steps: List<String>) {
        prefs.edit().putString(key(eventId), steps.joinToString(SEPARATOR)).apply()
    }

    private fun key(eventId: Long) = "steps:$eventId"

    private companion object {
        const val SEPARATOR = "\n"
    }
}

const val MAX_STEPS = 8
private const val MAX_STEP_LENGTH = 60
private val stepDelimiters = Regex("[\\n;]")
private val stepBullet = Regex("^(\\d+[.)]|[-•*])\\s*")

/** Превращает ответ модели («1. купить\n2. оплатить») в чистый список шагов: без нумерации, повторов и лишней длины. */
fun parseSteps(raw: String): List<String> = raw
    .split(stepDelimiters)
    .map { stepBullet.replace(it.trim(), "").trim() }
    .filter { it.isNotEmpty() }
    .distinct()
    .take(MAX_STEPS)
    .map { it.take(MAX_STEP_LENGTH) }
