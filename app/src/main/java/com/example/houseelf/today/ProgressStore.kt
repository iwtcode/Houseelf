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

    fun complete(key: String, today: LocalDate) {
        val done = doneKeys().toMutableSet()
        if (!done.add(key)) return
        // Старые отметки не копим: ключ заканчивается номером дня
        done.removeAll { (it.substringAfterLast(':').toLongOrNull() ?: 0L) < today.toEpochDay() - 7 }

        val lastDay = prefs.getLong("lastDay", -1L)
        val streak = when {
            lastDay == today.toEpochDay() -> prefs.getInt("streak", 1)
            lastDay == today.toEpochDay() - 1 -> prefs.getInt("streak", 0) + 1
            else -> 1
        }
        prefs.edit()
            .putStringSet("done", done)
            .putInt("xp", xp + XP_PER_TASK)
            .putInt("streak", streak)
            .putLong("lastDay", today.toEpochDay())
            .apply()
    }

    companion object {
        const val XP_PER_TASK = 20
    }
}
