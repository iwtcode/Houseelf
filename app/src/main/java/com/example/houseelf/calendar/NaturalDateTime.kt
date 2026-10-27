package com.example.houseelf.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Что удалось понять из слов пользователя.
 * [dateProblem] / [timeProblem] — фраза была, но разобрать её не получилось (чтобы сказать об этом модели).
 */
internal data class ParsedWhen(
    val date: LocalDate?,
    val time: LocalTime?,
    val dateProblem: Boolean = false,
    val timeProblem: Boolean = false,
)

/**
 * Разбор дат и времени «как говорит человек»: «завтра», «послезавтра», «в субботу», «в следующий вторник»,
 * «через неделю», «через 3 дня», «через 2 часа», «25 октября», «25.10», «в 12 часов», «в 6 вечера»,
 * «половина седьмого».
 *
 * Маленькая модель плохо считает даты в уме (путает дни недели, прибавляет не те дни), поэтому она передаёт
 * слова пользователя как есть, а дату и время считает этот код. Он не зависит ни от модели, ни от Android:
 * «сейчас» подаётся параметром, поэтому его просто проверить тестами. Понимает русский, английский и ISO.
 */
internal object NaturalDateTime {

    // ───────── Общий вход ─────────

    /**
     * Разбирает аргументы инструмента: [date] — «завтра», «в субботу», «25 октября»; [time] — «18:00», «6 вечера».
     * Если модель сложила всё в одну строку («завтра в 12 часов») или прислала ISO — разберём и это.
     * Аргумент [time] важнее времени, найденного в [date].
     */
    fun resolve(date: String?, time: String?, now: LocalDateTime): ParsedWhen {
        val d = date?.trim()?.takeIf { it.isNotEmpty() }
        val t = time?.trim()?.takeIf { it.isNotEmpty() }
        val today = now.toLocalDate()

        var day: LocalDate? = null
        var clock: LocalTime? = null

        // «через 2 часа», «через полчаса» — от текущего момента
        val offset = listOfNotNull(d, t).firstNotNullOfOrNull { parseOffset(it, now) }
        if (offset != null) return ParsedWhen(offset.toLocalDate(), offset.toLocalTime())

        if (d != null) {
            val iso = parseIso(d)
            if (iso != null) {
                day = iso.date
                clock = iso.time
            } else {
                day = parseDate(d, today)
                clock = parseTime(d, standalone = false)
            }
        }
        var timeProblem = false
        if (t != null) {
            val iso = parseIso(t)
            val parsed = iso?.time ?: parseTime(t, standalone = true)
            if (parsed != null) clock = parsed else if (clock == null) timeProblem = true
            // Дату, спрятанную в аргументе времени («25.10»), берём, только если в date её не нашлось
            if (day == null) day = iso?.date ?: if (parsed == null) parseDate(t, today) else null
        }
        val dateProblem = d != null && day == null && clock == null
        return ParsedWhen(day, clock, dateProblem = dateProblem, timeProblem = timeProblem)
    }

    // ───────── ISO ─────────

    private val isoRegex = Regex("""^\d{4}-\d{2}-\d{2}(?:[T ]\d{2}:\d{2}(?::\d{2})?(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})?)?$""")

    /** «2026-10-25» или «2026-10-25T15:00» (с поясом или без). */
    fun parseIso(raw: String): ParsedWhen? {
        val s = raw.trim()
        if (!isoRegex.matches(s)) return null
        val iso = s.replace(' ', 'T')
        try { return LocalDateTime.parse(iso).let { ParsedWhen(it.toLocalDate(), it.toLocalTime()) } } catch (_: Exception) {}
        try { return OffsetDateTime.parse(iso).toLocalDateTime().let { ParsedWhen(it.toLocalDate(), it.toLocalTime()) } } catch (_: Exception) {}
        try { return ParsedWhen(LocalDate.parse(iso), null) } catch (_: Exception) {}
        return null
    }

    // ───────── Слова ─────────

    private fun norm(raw: String): String = raw.lowercase(Locale.ROOT).replace('ё', 'е').trim()

    private val wordRegex = Regex("""[\p{L}\p{N}]+""")

    private fun words(s: String): List<String> = wordRegex.findAll(s).map { it.value }.toList()

    /** Дни недели: слово целиком (формы падежей) — чтобы «среди» не стало средой. */
    private val weekdayWords: Map<String, DayOfWeek> = buildMap {
        fun add(day: DayOfWeek, vararg forms: String) = forms.forEach { put(it, day) }
        add(DayOfWeek.MONDAY, "понедельник", "понедельника", "понедельнику", "понедельником", "понедельнике", "пн", "monday", "mon")
        add(DayOfWeek.TUESDAY, "вторник", "вторника", "вторнику", "вторником", "вторнике", "вт", "tuesday", "tue", "tues")
        add(DayOfWeek.WEDNESDAY, "среда", "среду", "среды", "среде", "средой", "ср", "wednesday", "wed")
        add(DayOfWeek.THURSDAY, "четверг", "четверга", "четвергу", "четвергом", "четверге", "чт", "thursday", "thu", "thur", "thurs")
        add(DayOfWeek.FRIDAY, "пятница", "пятницу", "пятницы", "пятнице", "пятницей", "пт", "friday", "fri")
        add(DayOfWeek.SATURDAY, "суббота", "субботу", "субботы", "субботе", "субботой", "сб", "saturday", "sat")
        add(DayOfWeek.SUNDAY, "воскресенье", "воскресенья", "воскресенью", "воскресеньем", "воскресеньи", "вс", "sunday", "sun")
    }

    private val monthPrefixes = listOf(
        1 to listOf("январ"), 2 to listOf("феврал"), 3 to listOf("март"), 4 to listOf("апрел"),
        6 to listOf("июн"), 7 to listOf("июл"), 8 to listOf("август"), 9 to listOf("сентябр"),
        10 to listOf("октябр"), 11 to listOf("ноябр"), 12 to listOf("декабр"),
    )

    private val monthExact: Map<String, Int> = buildMap {
        fun add(month: Int, vararg forms: String) = forms.forEach { put(it, month) }
        add(1, "янв", "january", "jan")
        add(2, "фев", "февр", "february", "feb")
        add(3, "мар", "march")
        add(4, "апр", "april", "apr")
        add(5, "мая", "май", "may")
        add(6, "июн", "june", "jun")
        add(7, "июл", "july", "jul")
        add(8, "авг", "august", "aug")
        add(9, "сен", "сент", "september", "sep", "sept")
        add(10, "окт", "october", "oct")
        add(11, "ноя", "нояб", "november", "nov")
        add(12, "дек", "december", "dec")
    }

    private fun monthOf(word: String): Int? =
        monthExact[word] ?: monthPrefixes.firstOrNull { (_, prefixes) -> prefixes.any { word.startsWith(it) } }?.first

    private val countWords = mapOf(
        "один" to 1, "одну" to 1, "одного" to 1, "два" to 2, "две" to 2, "пару" to 2, "пара" to 2, "три" to 3,
        "четыре" to 4, "пять" to 5, "шесть" to 6, "семь" to 7, "восемь" to 8, "девять" to 9, "десять" to 10,
        "одиннадцать" to 11, "двенадцать" to 12, "пятнадцать" to 15, "двадцать" to 20, "тридцать" to 30,
        "несколько" to 3, "a" to 1, "an" to 1, "one" to 1, "couple" to 2, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "ten" to 10,
    )

    // ───────── Дата ─────────

    /**
     * Дата из фразы; null — если даты в ней нет. Правила:
     *  - «сегодня», «завтра», «послезавтра», «вчера», «позавчера»;
     *  - «через N дней / недель / месяцев», «через неделю», «через пару дней»;
     *  - «в субботу» — ближайшая суббота после сегодня (если сегодня суббота — через неделю);
     *  - «в эту субботу» — суббота текущей недели (если она сегодня или ещё впереди, иначе следующая);
     *  - «в следующую субботу», «на следующей неделе в субботу» — суббота следующей недели (неделя с понедельника);
     *  - «на следующей неделе» без дня — понедельник следующей недели; «в следующем месяце» — то же число месяцем позже;
     *  - «25 октября», «25 окт», «октября 25», «25.10», «25.10.2026» — если в этом году дата уже прошла, берём следующий год;
     *  - «15 числа» — ближайшее 15-е число (включая сегодня).
     */
    fun parseDate(raw: String, today: LocalDate): LocalDate? {
        val text = norm(raw)
        if (text.isEmpty()) return null
        parseIso(text)?.date?.let { return it }

        // ISO внутри длинной фразы: «2026-10-25 в 12:00»
        Regex("""(\d{4})-(\d{2})-(\d{2})""").find(text)?.let { m ->
            try { return LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) } catch (_: Exception) {}
        }

        // Числовая дата: 25.10.2026, 25.10.26, 25.10, 25/10
        Regex("""(?<![\d.:])(\d{1,2})[./](\d{1,2})(?:[./](\d{2,4}))?(?![\d:])""").find(text)?.let { m ->
            val day = m.groupValues[1].toInt()
            val month = m.groupValues[2].toInt()
            val year = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()?.let { if (it < 100) 2000 + it else it }
            dateOrNull(day, month, year, today)?.let { return it }
        }

        val tokens = words(text)
        if (tokens.isEmpty()) return null

        // «25 октября [2027]», «октября 25»
        for (i in tokens.indices) {
            val n = tokens[i].toIntOrNull()
            val m = monthOf(tokens[i])
            if (n != null && n in 1..31) {
                val month = tokens.getOrNull(i + 1)?.let { monthOf(it) } ?: continue
                val year = tokens.getOrNull(i + 2)?.toIntOrNull()?.takeIf { it in 1900..2200 }
                dateOrNull(n, month, year, today)?.let { return it }
            } else if (m != null) {
                val day = tokens.getOrNull(i + 1)?.toIntOrNull()?.takeIf { it in 1..31 } ?: continue
                dateOrNull(day, m, null, today)?.let { return it }
            }
        }

        // «15 числа»
        for (i in tokens.indices) {
            val n = tokens[i].toIntOrNull() ?: continue
            if (n in 1..31 && tokens.getOrNull(i + 1)?.startsWith("числ") == true) {
                nextDayOfMonth(n, today)?.let { return it }
            }
        }

        val hasNext = tokens.any { it.startsWith("следующ") || it == "next" }
        val hasThis = tokens.any { it in THIS_WORDS || it.startsWith("ближайш") }
        val hasWeek = tokens.any { it.startsWith("недел") || it == "week" }
        val hasMonth = tokens.any { it.startsWith("месяц") || it == "month" }

        // Дни недели
        val weekday = tokens.firstNotNullOfOrNull { weekdayWords[it] }
        if (weekday != null) {
            val monday = today.with(DayOfWeek.MONDAY)
            return when {
                hasNext -> monday.plusWeeks(1).with(weekday)
                hasThis -> monday.with(weekday).let { if (it.isBefore(today)) it.plusWeeks(1) else it }
                else -> today.with(TemporalAdjusters.next(weekday))
            }
        }

        // «через N …»
        val inIdx = tokens.indexOfFirst { it == "через" || it == "in" }
        if (inIdx >= 0) return dateAfter(tokens.drop(inIdx + 1), today)

        // «после завтра», «day after tomorrow» — раньше одиночного «завтра»
        if ("после" in tokens && "завтра" in tokens) return today.plusDays(2)
        if ("after" in tokens && "tomorrow" in tokens) return today.plusDays(2)

        // Слова-якоря
        for (t in tokens) {
            when (t) {
                "сегодня", "today" -> return today
                "завтра", "tomorrow" -> return today.plusDays(1)
                "послезавтра" -> return today.plusDays(2)
                "послепослезавтра" -> return today.plusDays(3)
                "вчера", "yesterday" -> return today.minusDays(1)
                "позавчера" -> return today.minusDays(2)
            }
        }
        if (hasNext && hasWeek) return today.with(DayOfWeek.MONDAY).plusWeeks(1)
        if (hasNext && hasMonth) return today.plusMonths(1)
        return null
    }

    private val THIS_WORDS = setOf("эту", "этот", "этой", "этом", "this")

    /** Слова после «через»: «3 дня», «неделю», «пару недель», «полторы недели». */
    private fun dateAfter(rest: List<String>, today: LocalDate): LocalDate? {
        val first = rest.firstOrNull() ?: return null
        val unitOf = { w: String? ->
            val s = w.orEmpty()
            when {
                s.startsWith("ден") || s.startsWith("дн") || s.startsWith("day") -> 'd'
                s.startsWith("недел") || s.startsWith("week") -> 'w'
                s.startsWith("месяц") || s.startsWith("month") -> 'm'
                s.startsWith("год") || s.startsWith("лет") || s.startsWith("year") -> 'y'
                else -> ' '
            }
        }
        if (first == "полторы" || first == "полтора") {
            return when (unitOf(rest.getOrNull(1))) {
                'w' -> today.plusDays(10)
                'm' -> today.plusDays(45)
                'd' -> today.plusDays(1)
                else -> null
            }
        }
        val count = first.toIntOrNull() ?: countWords[first]
        val amount = (count ?: 1).toLong()
        val unit = unitOf(rest.getOrNull(if (count != null) 1 else 0))
        return when (unit) {
            'd' -> today.plusDays(amount)
            'w' -> today.plusWeeks(amount)
            'm' -> today.plusMonths(amount)
            'y' -> today.plusYears(amount)
            else -> null
        }
    }

    private fun dateOrNull(day: Int, month: Int, year: Int?, today: LocalDate): LocalDate? = try {
        if (year != null) {
            LocalDate.of(year, month, day)
        } else {
            val thisYear = LocalDate.of(today.year, month, day)
            // Дата без года, которая в этом году уже прошла, — это следующий год
            if (thisYear.isBefore(today)) LocalDate.of(today.year + 1, month, day) else thisYear
        }
    } catch (_: Exception) {
        null
    }

    /** Ближайшее число месяца, включая сегодня: «15 числа». */
    private fun nextDayOfMonth(day: Int, today: LocalDate): LocalDate? {
        var month = today.withDayOfMonth(1)
        repeat(13) {
            if (day <= month.lengthOfMonth()) {
                val candidate = month.withDayOfMonth(day)
                if (!candidate.isBefore(today)) return candidate
            }
            month = month.plusMonths(1)
        }
        return null
    }

    // ───────── «Через N часов / минут» ─────────

    private val offsetRegexes = listOf(
        Regex("""через\s+полтора\s+часа"""),
        Regex("""через\s+полчаса"""),
        Regex("""(?:через|in)\s+(?:(\d+|[\p{L}]+)\s+)?(час\p{L}*|минут\p{L}*|hours?|hrs?|minutes?|mins?)"""),
    )

    /** «через 2 часа», «через полчаса», «через 15 минут», «in 2 hours» — от момента [now], до минуты. */
    fun parseOffset(raw: String, now: LocalDateTime): LocalDateTime? {
        val text = norm(raw)
        val base = now.withSecond(0).withNano(0)
        if (offsetRegexes[0].containsMatchIn(text)) return base.plusMinutes(90)
        if (offsetRegexes[1].containsMatchIn(text)) return base.plusMinutes(30)
        for (m in offsetRegexes[2].findAll(text)) {
            val countRaw = m.groupValues[1]
            val count = if (countRaw.isEmpty()) 1 else countRaw.toIntOrNull() ?: countWords[countRaw] ?: continue
            val unit = m.groupValues[2]
            val minutes = if (unit.startsWith("мин") || unit.startsWith("min")) count.toLong() else count * 60L
            return base.plusMinutes(minutes)
        }
        return null
    }

    // ───────── Время ─────────

    private val ordinalHours = mapOf(
        "первого" to 1, "второго" to 2, "третьего" to 3, "четвертого" to 4, "пятого" to 5, "шестого" to 6,
        "седьмого" to 7, "восьмого" to 8, "девятого" to 9, "десятого" to 10, "одиннадцатого" to 11,
        "двенадцатого" to 12,
    )

    private val cardinalHours = mapOf(
        "час" to 1, "один" to 1, "два" to 2, "три" to 3, "четыре" to 4, "пять" to 5, "шесть" to 6, "семь" to 7,
        "восемь" to 8, "девять" to 9, "десять" to 10, "одиннадцать" to 11, "двенадцать" to 12,
    )

    private enum class Period { MORNING, DAY, EVENING, NIGHT }

    private fun periodOf(word: String): Period? = when (word) {
        "утра", "утром", "am" -> Period.MORNING
        "дня", "днем", "pm" -> Period.DAY
        "вечера", "вечером" -> Period.EVENING
        "ночи", "ночью" -> Period.NIGHT
        else -> null
    }

    /**
     * Время из фразы; null — если времени нет. [standalone] — фраза целиком про время (аргумент time):
     * только тогда «18.30» и голое «12» считаем временем (иначе спутаем с датой и числом месяца).
     *  - «18:00», «в 18:00»;
     *  - «12 часов», «в 5 часов 30 минут», «в 6 вечера», «в 7 утра», «в 3 дня», «в 2 ночи», «в 12 ночи», «в шесть»;
     *  - «половина седьмого» = 6:30, «четверть седьмого» = 6:15, «без пятнадцати семь» = 6:45;
     *  - «в полдень», «в полночь»;
     *  - «утром» = 9:00, «в обед» = 13:00, «днём» = 14:00, «вечером» = 19:00 — только если точного часа нет.
     */
    fun parseTime(raw: String, standalone: Boolean = true): LocalTime? {
        var text = norm(raw)
        offsetRegexes.forEach { text = it.replace(text, " ") } // «через 2 часа» — не «2 часа ночи»
        text = text.trim()
        if (text.isEmpty()) return null

        // Часы:минуты (подходит и хвост ISO — «T15:00»)
        Regex("""(?<![\d:])(\d{1,2}):(\d{2})(?![\d:])""").find(text)?.let { m ->
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toInt()
            if (h in 0..23 && min in 0..59) return withPeriod(h, min, text, m.range.last + 1)
        }

        // «18.30» — только когда подано чистое время
        if (standalone) {
            Regex("""^(?:в\s+)?(\d{1,2})[.\-](\d{2})$""").find(text)?.let { m ->
                val h = m.groupValues[1].toInt()
                val min = m.groupValues[2].toInt()
                if (h in 0..23 && min in 0..59) return LocalTime.of(h, min)
            }
        }

        // «половина седьмого» = 6:30, «четверть седьмого» = 6:15
        Regex("""половин\p{L}*\s+(\p{L}+)""").find(text)?.let { m ->
            ordinalHours[m.groupValues[1]]?.let { return withPeriod(before(it), 30, text, m.range.last + 1) }
        }
        Regex("""четверт\p{L}*\s+(\p{L}+)""").find(text)?.let { m ->
            ordinalHours[m.groupValues[1]]?.let { return withPeriod(before(it), 15, text, m.range.last + 1) }
        }
        // «без пятнадцати семь» = 6:45, «без десяти восемь» = 7:50
        Regex("""без\s+(\p{L}+|\d+)\s+(\p{L}+|\d+)""").find(text)?.let { m ->
            val minus = minutesBefore(m.groupValues[1])
            val hour = m.groupValues[2].toIntOrNull() ?: cardinalHours[m.groupValues[2]]
            if (minus != null && hour != null && hour in 1..12) {
                return withPeriod(before(hour), 60 - minus, text, m.range.last + 1)
            }
        }

        val tokens = words(text)
        if ("полдень" in tokens || "полдня" in tokens || "noon" in tokens) return LocalTime.NOON
        if ("полночь" in tokens || "midnight" in tokens) return LocalTime.MIDNIGHT

        // «в 5 часов 30 минут», «12 часов», «5 ч»
        Regex("""(\d{1,2})\s*(?:час\p{L}*|ч(?!\p{L})|h(?!\p{L}))(?:\s*(\d{1,2})\s*(?:мин\p{L}*|м(?!\p{L})))?""").find(text)?.let { m ->
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].takeIf { it.isNotEmpty() }?.toInt() ?: 0
            if (h in 0..24 && min in 0..59) return withPeriod(h % 24, min, text, m.range.last + 1)
        }
        // «6pm», «6 pm», «6 p.m.»
        Regex("""(\d{1,2})\s*(am|pm|a\.m\.|p\.m\.)""").find(text)?.let { m ->
            val h = m.groupValues[1].toInt()
            if (h in 1..12) return LocalTime.of(if (m.groupValues[2].startsWith("p")) h % 12 + 12 else h % 12, 0)
        }
        // «в 6», «в 6 вечера» — но не «в 5 октября» и не «в 15 числа»
        for (m in Regex("""(?:^|\s)в\s+(\d{1,2})(?![\d:.\-/])""").findAll(text)) {
            val h = m.groupValues[1].toInt()
            val next = words(text.substring(m.range.last + 1)).firstOrNull()
            if (next != null && (monthOf(next) != null || next.startsWith("числ"))) continue
            if (h in 0..23) return withPeriod(h, 0, text, m.range.last + 1)
        }
        // «6 вечера» без «в» («3 дня» — только в чистом времени: в дате это «через 3 дня»)
        val bareRegex = if (standalone) Regex("""(?:^|\s)(\d{1,2})\s+(?=утра|дня|вечера|ночи)""")
        else Regex("""(?:^|\s)(\d{1,2})\s+(?=утра|вечера|ночи)""")
        bareRegex.find(text)?.let { m ->
            val h = m.groupValues[1].toInt()
            if (h in 0..12) return withPeriod(h, 0, text, m.range.last + 1)
        }
        // «в шесть вечера», «в два часа дня», «в час»
        for (m in Regex("""(?:^|\s)в\s+(\p{L}+)""").findAll(text)) {
            val h = cardinalHours[m.groupValues[1]] ?: continue
            return withPeriod(h, 0, text, m.range.last + 1)
        }

        // Голое число «12» — час (только когда подано одно время)
        if (standalone) {
            Regex("""^\d{1,2}$""").find(text)?.let { m ->
                val h = m.value.toInt()
                if (h in 0..23) return LocalTime.of(h, 0)
            }
        }

        // Части дня без точного часа
        for (t in tokens) {
            when (t) {
                "утром", "morning" -> return LocalTime.of(9, 0)
                "обед", "обеду", "обедом", "lunch" -> return LocalTime.of(13, 0)
                "днем", "afternoon" -> return LocalTime.of(14, 0)
                "вечером", "evening" -> return LocalTime.of(19, 0)
            }
        }
        return null
    }

    private fun minutesBefore(word: String): Int? = word.toIntOrNull() ?: when (word) {
        "пяти", "пять" -> 5
        "десяти", "десять" -> 10
        "пятнадцати", "пятнадцать" -> 15
        "двадцати", "двадцать" -> 20
        else -> null
    }

    /** «Половина седьмого» — это 6:30: час перед названным; перед первым — двенадцатый. */
    private fun before(hour: Int): Int = if (hour <= 1) 12 else hour - 1

    /**
     * Поправка на «утра / дня / вечера / ночи». Смотрим слово сразу после времени (его конец — [end]), пропуская «часов»,
     * а если там слова нет — слова «утром / днём / вечером / ночью» где угодно во фразе («сегодня вечером в 6»).
     * 6 вечера → 18:00, 3 дня → 15:00, 12 дня → 12:00, 12 ночи → 00:00, 2 ночи → 02:00, 11 ночи → 23:00.
     * Время, уже записанное по 24-часовым часам (13:00, 18:30), не меняем.
     */
    private fun withPeriod(h: Int, min: Int, text: String, end: Int): LocalTime {
        val after = words(text.substring(minOf(end, text.length))).dropWhile { it.startsWith("час") }
        val period = after.firstOrNull()?.let { periodOf(it) }
            ?: words(text).firstNotNullOfOrNull { w -> periodOf(w)?.takeIf { w in INSTRUMENTAL } }
        var hour = h
        if (period != null && h <= 12) {
            hour = when (period) {
                Period.MORNING -> if (h == 12) 0 else h
                Period.DAY -> if (h < 12) h + 12 else h
                Period.EVENING -> if (h < 12) h + 12 else h
                Period.NIGHT -> when {
                    h == 12 -> 0
                    h >= 10 -> h + 12
                    else -> h
                }
            }
        }
        return LocalTime.of(hour % 24, min)
    }

    /** Слова, которые относятся ко всей фразе, а не к соседнему числу («дня» из «через 3 дня» сюда не входит). */
    private val INSTRUMENTAL = setOf("утром", "днем", "вечером", "ночью")
}
