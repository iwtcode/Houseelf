package com.example.houseelf.today

import android.content.Context
import java.time.LocalDate

/** XP, серия дней и отметки «сделано». Лежит в настройках приложения, без базы данных. */
class ProgressStore(context: Context) {
    private val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)

    val xp: Int get() = prefs.getInt("xp", 0)

    fun doneKeys(): Set<String> = HashSet(prefs.getStringSet("done", emptySet()) ?: emptySet())

    /** Серия обрывается, если вчера ничего не было сделано. */
    fun streak(today: LocalDate): Int {
        val last = prefs.getLong("lastDay", -1L)
        return if (last >= today.toEpochDay() - 1) prefs.getInt("streak", 0) else 0
    }

    /** Дела, которые пользователь уже начал (нажал «Начать»). */
    fun startedKeys(): Set<String> = HashSet(prefs.getStringSet("started", emptySet()) ?: emptySet())

    fun start(key: String) {
        prefs.edit().putStringSet("started", startedKeys() + key).apply()
    }

    /** Сколько шагов каждого дела уже пройдено. */
    fun stepsDone(): Map<String, Int> = prefs.all
        .filterKeys { it.startsWith(STEP_PREFIX) }
        .entries.associate { (name, value) -> name.removePrefix(STEP_PREFIX) to (value as? Int ?: 0) }

    fun nextStep(key: String) {
        prefs.edit().putInt(STEP_PREFIX + key, prefs.getInt(STEP_PREFIX + key, 0) + 1).apply()
    }

    fun complete(key: String, today: LocalDate) {
        if (key in doneKeys()) return
        val done = (doneKeys() + key).withoutStale(today)

        val lastDay = prefs.getLong("lastDay", -1L)
        val streak = when {
            lastDay == today.toEpochDay() -> prefs.getInt("streak", 1)
            lastDay == today.toEpochDay() - 1 -> prefs.getInt("streak", 0) + 1
            else -> 1
        }
        prefs.edit()
            .putStringSet("done", done)
            .putStringSet("started", startedKeys().withoutStale(today))
            .remove(STEP_PREFIX + key)
            .putInt("xp", xp + XP_PER_TASK)
            .putInt("streak", streak)
            .putLong("lastDay", today.toEpochDay())
            .apply()
    }

    /** Старые отметки не копим: ключ заканчивается номером дня. */
    private fun Set<String>.withoutStale(today: LocalDate): Set<String> =
        filterNot { (it.substringAfterLast(':').toLongOrNull() ?: 0L) < today.toEpochDay() - 7 }.toSet()

    companion object {
        const val XP_PER_TASK = 20
        private const val STEP_PREFIX = "step:"
    }
}
