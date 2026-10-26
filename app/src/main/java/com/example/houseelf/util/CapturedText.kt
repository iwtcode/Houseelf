package com.example.houseelf.util

/** Текст, пришедший из другого приложения, уже очищенный и укороченный до допустимой длины. */
data class CapturedText(
    val text: String,
    /** Исходный текст был длиннее лимита, взято только начало. */
    val truncated: Boolean,
)

/**
 * Приводит текст к виду, с которым работает модель: единые переводы строк, без пустых краёв,
 * не длиннее [maxChars]. Возвращает null, если текста по сути нет.
 */
fun normalizeCapturedText(raw: CharSequence, maxChars: Int): CapturedText? {
    val clean = raw.toString().replace("\r\n", "\n").trim()
    if (clean.isEmpty()) return null
    if (clean.length <= maxChars) return CapturedText(clean, truncated = false)

    var cut = clean.take(maxChars)
    if (cut.last().isHighSurrogate()) cut = cut.dropLast(1) // не режем эмодзи пополам
    return CapturedText(cut.trimEnd(), truncated = true)
}
