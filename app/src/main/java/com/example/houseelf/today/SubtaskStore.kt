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

    /** Дело удалили — шаги больше не нужны. */
    fun clear(eventId: Long) {
        prefs.edit().remove(key(eventId)).apply()
    }

    private fun key(eventId: Long) = "steps:$eventId"

    private companion object {
        const val SEPARATOR = "\n"
    }
}

const val MAX_STEPS = 8
private const val MAX_STEP_LENGTH = 60
private val stepDelimiters = Regex("[\\n;]")
private val commaDelimiter = Regex(",")
private val stepBullet = Regex("^(\\d+[.)]|[-•*])\\s*")
private val noteDelimiters = Regex("[\\n;,]|\\s+и\\s+")
private val infinitiveEnding = Regex("(ть|ться|ти|чь)$", RegexOption.IGNORE_CASE)

/**
 * Превращает ответ модели («1. купить\n2. оплатить») в чистый список шагов: без нумерации, повторов и лишней длины.
 * Модель иногда присылает список в скобках («[купить, оплатить]») или одной строкой через запятую — понимаем и это.
 */
fun parseSteps(raw: String): List<String> {
    var text = raw.trim()
    val listLike = text.startsWith("[") && text.endsWith("]")
    if (listLike) text = text.substring(1, text.length - 1).replace("\"", "")
    var parts = splitSteps(text, if (listLike) commaDelimiter else stepDelimiters)
    if (parts.size < 2 && ',' in text) parts = splitSteps(text, commaDelimiter)
    return parts
}

private fun splitSteps(text: String, delimiter: Regex): List<String> = text
    .split(delimiter)
    .map { stepBullet.replace(it.trim(), "").trim() }
    .filter { it.isNotEmpty() }
    .distinct()
    .take(MAX_STEPS)
    .map { it.take(MAX_STEP_LENGTH) }

/**
 * Запасной вариант, если модель не прислала шаги: делим описание дела на части.
 * «купить молоко, хлеб, яйца» → «Купить молоко», «Купить хлеб», «Купить яйца» (глагол первой части переносим).
 */
fun stepsFromNotes(notes: String?): List<String> {
    val pieces = notes.orEmpty().split(noteDelimiters)
        .map { it.trim().trimEnd('.', '!') }
        .filter { it.isNotEmpty() }
    if (pieces.size < 2) return emptyList()
    val firstWord = pieces.first().substringBefore(' ')
    val verb = firstWord.takeIf { ' ' in pieces.first() && isInfinitive(it) }
    return pieces
        .mapIndexed { i, piece ->
            val line = if (verb != null && i > 0 && !isInfinitive(piece.substringBefore(' '))) "$verb $piece" else piece
            line.replaceFirstChar { it.uppercase() }
        }
        .distinct()
        .take(MAX_STEPS)
        .map { it.take(MAX_STEP_LENGTH) }
}

private fun isInfinitive(word: String) = word.length > 3 && infinitiveEnding.containsMatchIn(word)
