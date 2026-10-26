package com.example.houseelf.markdown

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

private val LINE_BREAK_TAG = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)

/** Символы, которые можно экранировать обратной косой чертой: \* \_ \` и т. д. */
private const val ESCAPABLE = "\\`*_{}[]()#+-.!|~<>\$"

internal fun buildInline(text: String, linkColor: Color, codeBackground: Color, lenient: Boolean): AnnotatedString =
    buildAnnotatedString {
        appendInline(
            s = text.replace(LINE_BREAK_TAG, "\n"),
            linkColor = linkColor,
            codeBackground = codeBackground,
            lenient = lenient,
            inLink = false,
        )
    }

private fun emphasisStyle(strength: Int): SpanStyle = when (strength) {
    1 -> SpanStyle(fontStyle = FontStyle.Italic)
    // Основной шрифт приложения уже Bold, поэтому «жирный» делаем Black — иначе его не отличить
    2 -> SpanStyle(fontWeight = FontWeight.Black)
    else -> SpanStyle(fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
}

private fun AnnotatedString.Builder.appendInline(
    s: String,
    linkColor: Color,
    codeBackground: Color,
    lenient: Boolean,
    inLink: Boolean,
) {
    val plain = StringBuilder()
    fun flush() {
        if (plain.isNotEmpty()) {
            append(plain.toString())
            plain.setLength(0)
        }
    }

    var i = 0
    while (i < s.length) {
        val ch = s[i]

        // Экранирование: \* \_ \` …
        if (ch == '\\' && i + 1 < s.length && s[i + 1] in ESCAPABLE) {
            plain.append(s[i + 1])
            i += 2
            continue
        }

        // `код`
        if (ch == '`') {
            var n = 0
            while (i + n < s.length && s[i + n] == '`') n++
            val close = findBacktickClose(s, i + n, n)
            if (close >= 0) {
                flush()
                var code = s.substring(i + n, close).replace('\n', ' ')
                if (code.length > 2 && code.startsWith(" ") && code.endsWith(" ")) {
                    code = code.substring(1, code.length - 1)
                }
                withStyle(
                    SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, background = codeBackground),
                ) { append(code) }
                i = close + n
            } else {
                plain.append(s, i, i + n)
                i += n
            }
            continue
        }

        // [текст](https://ссылка)
        if (ch == '[' && !inLink) {
            val link = parseLink(s, i)
            if (link != null) {
                flush()
                val linkStyles = TextLinkStyles(
                    SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                )
                withLink(LinkAnnotation.Url(link.url, linkStyles)) {
                    appendInline(link.label, linkColor, codeBackground, false, true)
                }
                i = link.end
                continue
            }
        }

        // Голая ссылка: https://…
        if (ch == 'h' && !inLink && (i == 0 || !s[i - 1].isLetterOrDigit()) &&
            (s.startsWith("http://", i) || s.startsWith("https://", i))
        ) {
            var j = i
            while (j < s.length && !s[j].isWhitespace() && s[j] != '<' && s[j] != '>' && s[j] != '"') j++
            var url = s.substring(i, j)
            while (url.isNotEmpty() && url.last() in ".,;:!?)]}'»…*_~") url = url.dropLast(1)
            if (url.length > "https://".length) {
                flush()
                val linkStyles = TextLinkStyles(
                    SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                )
                withLink(LinkAnnotation.Url(url, linkStyles)) { append(url) }
                i += url.length
                continue
            }
        }

        // ~~зачёркнутый~~
        if (ch == '~' && s.startsWith("~~", i) && i + 2 < s.length && !s[i + 2].isWhitespace()) {
            val close = s.indexOf("~~", i + 2)
            if (close > i + 2) {
                flush()
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    appendInline(s.substring(i + 2, close), linkColor, codeBackground, false, inLink)
                }
                i = close + 2
                continue
            }
        }

        // *курсив*, **жирный**, ***жирный курсив***, а также _курсив_ и __жирный__
        if (ch == '*' || ch == '_') {
            var run = 0
            while (i + run < s.length && s[i + run] == ch) run++
            val next = if (i + run < s.length) s[i + run] else null
            val prev = if (i > 0) s[i - 1] else null
            // Открывающая пара: за ней не пробел; «_» внутри слова (snake_case) не считается
            val canOpen = run <= 3 && next != null && !next.isWhitespace() &&
                (ch == '*' || prev == null || !prev.isLetterOrDigit())
            if (canOpen) {
                val close = findEmphasisClose(s, i + run, ch, run)
                if (close >= 0) {
                    flush()
                    withStyle(emphasisStyle(run)) {
                        appendInline(s.substring(i + run, close), linkColor, codeBackground, lenient, inLink)
                    }
                    i = close + run
                    continue
                }
                if (lenient) {
                    // Ответ ещё пишется: закрывающей пары пока нет — оформляем всё до конца
                    flush()
                    withStyle(emphasisStyle(run)) {
                        appendInline(s.substring(i + run), linkColor, codeBackground, true, inLink)
                    }
                    i = s.length
                    continue
                }
            }
            plain.append(s, i, i + run)
            i += run
            continue
        }

        plain.append(ch)
        i++
    }
    flush()
}

/** Индекс закрывающего ряда из [count] обратных кавычек, начиная с [from], или -1. */
private fun findBacktickClose(s: String, from: Int, count: Int): Int {
    var j = from
    while (j < s.length) {
        if (s[j] == '`') {
            var m = 0
            while (j + m < s.length && s[j + m] == '`') m++
            if (m == count) return j
            j += m
        } else {
            j++
        }
    }
    return -1
}

/**
 * Индекс закрывающего ограничителя для пары [delim] (* или _) длиной [strength] (1–3), начиная с [from].
 * Закрывающий ряд не должен идти после пробела; для «_» после него не должно быть буквы или цифры.
 * Если ряд из трёх звёздочек закрывает «жирный» (**…***), первая звёздочка остаётся внутреннему курсиву.
 */
private fun findEmphasisClose(s: String, from: Int, delim: Char, strength: Int): Int {
    var j = from
    while (j < s.length) {
        val c = s[j]
        if (c == '\\') {
            j += 2
            continue
        }
        if (c == '`') {
            var n = 0
            while (j + n < s.length && s[j + n] == '`') n++
            val close = findBacktickClose(s, j + n, n)
            j = if (close >= 0) close + n else j + n
            continue
        }
        if (c == delim) {
            var k = j
            while (k < s.length && s[k] == delim) k++
            val run = k - j
            val fits = run == strength || (strength == 2 && run == 3)
            if (fits && j > from && !s[j - 1].isWhitespace() &&
                (delim == '*' || k >= s.length || !s[k].isLetterOrDigit())
            ) {
                return if (strength == 2 && run == 3) j + 1 else j
            }
            j = k
            continue
        }
        j++
    }
    return -1
}

private class MdLink(val label: String, val url: String, val end: Int)

/** Разбирает [текст](url), начиная с «[» в позиции [start]; небезопасные схемы отбрасываются. */
private fun parseLink(s: String, start: Int): MdLink? {
    var depth = 0
    var j = start
    while (j < s.length) {
        val c = s[j]
        if (c == '\\') {
            j += 2
            continue
        }
        if (c == '[') {
            depth++
        } else if (c == ']') {
            depth--
            if (depth == 0) break
        }
        j++
    }
    if (j >= s.length || j + 1 >= s.length || s[j + 1] != '(') return null

    var k = j + 2
    var parens = 1
    while (k < s.length) {
        val c = s[k]
        if (c == '(') {
            parens++
        } else if (c == ')') {
            parens--
            if (parens == 0) break
        }
        k++
    }
    if (k >= s.length) return null

    val label = s.substring(start + 1, j)
    val url = s.substring(j + 2, k).trim().substringBefore(' ').removePrefix("<").removeSuffix(">")
    if (label.isEmpty() || !isSafeUrl(url)) return null
    return MdLink(label, url, k + 1)
}

/** Открываем только веб-ссылки и почту: ни intent:, ни javascript:, ни file: из ответа модели. */
private fun isSafeUrl(url: String): Boolean {
    val u = url.lowercase()
    return u.startsWith("http://") || u.startsWith("https://") || u.startsWith("mailto:")
}
