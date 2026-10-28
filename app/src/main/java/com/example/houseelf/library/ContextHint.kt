package com.example.houseelf.library

/** Положения ползунка: 0 — «авто», дальше размер контекста в токенах. */
object ContextSteps {
    val values: List<Int> = listOf(0, 2048, 3072, 4096, 6144, 8192, 12288, 16384, 24576, 32768)

    fun indexOf(tokens: Int): Int {
        val exact = values.indexOf(tokens)
        if (exact >= 0) return exact
        // Сохранённое значение не из списка — берём ближайшее
        return values.indices.drop(1).minByOrNull { kotlin.math.abs(values[it] - tokens) } ?: 0
    }
}

enum class MemoryVerdict { Ok, Tight, Bad }

data class ContextHint(
    val tokens: Int,
    val verdict: MemoryVerdict,
    /** Примерный объём памяти под контекст. */
    val extraBytes: Long,
    /** Что с этим размером можно делать. */
    val usage: String,
    /** Как с таким размером обстоят дела с памятью телефона. */
    val memory: String,
    /** Самый большой размер из списка, который сейчас проходит с запасом (0 — даже минимальный впритык). */
    val comfortableMax: Int,
)

object ContextAdvisor {

    private const val MB = 1024L * 1024
    private const val COMFORT_RESERVE = 1024 * MB // столько должно остаться свободным
    private const val TIGHT_RESERVE = 300 * MB

    /** Меньше этого авто-режим не опускается: на системную инструкцию и инструменты нужно место. */
    const val AUTO_FLOOR = 3072

    /**
     * Прикидка памяти под KV-кэш: примерно 1 КБ на токен на каждые ≈ 25 МБ веса модели
     * (Gemma 4 E2B ≈ 100 КБ на токен, E4B ≈ 150 КБ). Это оценка «с запасом», а не замер.
     */
    fun contextBytes(tokens: Int, modelBytes: Long): Long = tokens.toLong() * (modelBytes / 25_000)

    /**
     * Сколько памяти реально можно отдать под контекст: свободная минус то, что ещё займёт сама модель.
     * Если модель уже в памяти, её вес в «свободно» учтён, зато учтён и её текущий контекст — он освободится.
     */
    private fun availableFor(modelBytes: Long, freeRam: Long, loadedTokens: Int?): Long =
        if (loadedTokens != null) freeRam + contextBytes(loadedTokens, modelBytes)
        else freeRam - modelBytes

    private fun verdictOf(tokens: Int, available: Long, modelBytes: Long): MemoryVerdict {
        val headroom = available - contextBytes(tokens, modelBytes)
        return when {
            headroom >= COMFORT_RESERVE -> MemoryVerdict.Ok
            headroom >= TIGHT_RESERVE -> MemoryVerdict.Tight
            else -> MemoryVerdict.Bad
        }
    }

    /**
     * Авто-размер контекста: самый большой шаг ползунка, который проходит с запасом по СВОБОДНОЙ памяти
     * (а не по общему объёму телефона). Если и минимальный шаг не проходит — [AUTO_FLOOR].
     */
    fun autoContext(modelBytes: Long, freeRam: Long, loadedTokens: Int? = null): Int {
        val available = availableFor(modelBytes, freeRam, loadedTokens)
        return ContextSteps.values.drop(1)
            .lastOrNull { it >= AUTO_FLOOR && verdictOf(it, available, modelBytes) == MemoryVerdict.Ok }
            ?: AUTO_FLOOR
    }

    /**
     * @param tokens выбранный размер (0 — авто)
     * @param modelBytes размер файла модели
     * @param freeRam свободная оперативная память телефона сейчас
     * @param loadedTokens контекст уже загруженной модели (null — модель не в памяти)
     */
    fun advise(tokens: Int, modelBytes: Long, freeRam: Long, loadedTokens: Int?): ContextHint {
        val effective = if (tokens > 0) tokens else autoContext(modelBytes, freeRam, loadedTokens)
        val available = availableFor(modelBytes, freeRam, loadedTokens)

        fun verdictFor(t: Int) = verdictOf(t, available, modelBytes)

        val verdict = verdictFor(effective)
        val comfortable = ContextSteps.values.drop(1).lastOrNull { verdictFor(it) == MemoryVerdict.Ok } ?: 0
        val extra = contextBytes(effective, modelBytes)

        val memory = when (verdict) {
            MemoryVerdict.Ok -> "Памяти хватит с запасом."
            MemoryVerdict.Tight ->
                "Памяти впритык: приложение может вылетать. Закрой другие приложения" +
                    if (comfortable > 0) " или выбери не больше $comfortable." else " или выбери размер поменьше."
            MemoryVerdict.Bad ->
                "Не хватит памяти: модель, скорее всего, не запустится" +
                    if (comfortable > 0) " — выбери не больше $comfortable." else " — освободи память или выбери размер поменьше."
        }
        return ContextHint(effective, verdict, extra, usageFor(effective), memory, comfortable)
    }

    private fun usageFor(tokens: Int): String = when {
        tokens <= 2048 -> "Короткие вопросы и ответы. Длинный разговор быстро забудется."
        tokens <= 3072 -> "Простые диалоги без фото и длинных текстов."
        tokens <= 4096 -> "Обычный чат: несколько десятков сообщений или пара фото."
        tokens <= 6144 -> "Длинная переписка и несколько фото."
        tokens <= 8192 -> "Большие тексты и долгие разговоры, фото и голос."
        tokens <= 12288 -> "Очень длинные тексты. Ответы медленнее, памяти нужно много."
        tokens <= 16384 -> "Огромные тексты и очень долгие разговоры. Нужно много свободной памяти."
        tokens <= 24576 -> "Целые документы и книги по главам. Только для телефонов с 12 ГБ и больше."
        else -> "Максимум: очень длинные документы. Нужно 12 ГБ и больше и много свободной памяти."
    }
}
