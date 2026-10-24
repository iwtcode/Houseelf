package com.example.houseelf

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.houseelf.ui.theme.Duo

// ───────── Показ markdown в Compose ─────────

/**
 * Текст с markdown-разметкой: **жирный**, *курсив*, ~~зачёркнутый~~, `код`, [ссылки](https://…),
 * заголовки, списки, цитаты, блоки кода, таблицы.
 *
 * [streaming] — ответ ещё пишется: незакрытый в последнем абзаце «**жирный» сразу показывается
 * жирным, а не мигает звёздочками, пока не придёт закрывающая пара.
 */
@Composable
fun MarkdownText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    onDark: Boolean = false,
    streaming: Boolean = false,
) {
    val blocks = remember(text) { parseMarkdown(text) }
    val palette = remember(color, onDark) { MdPalette.of(color, onDark) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        blocks.forEachIndexed { index, block ->
            MdBlockView(block, style, palette, lenient = streaming && index == blocks.lastIndex)
        }
    }
}

private data class MdPalette(
    val text: Color,
    val muted: Color,
    val link: Color,
    val codeBg: Color,
    val codeBlockBg: Color,
    val line: Color,
    val quoteBar: Color,
    val tableHeaderBg: Color,
) {
    companion object {
        fun of(text: Color, onDark: Boolean): MdPalette =
            if (onDark) {
                MdPalette(
                    text = text,
                    muted = text.copy(alpha = 0.75f),
                    link = text,
                    codeBg = Color.White.copy(alpha = 0.22f),
                    codeBlockBg = Color.Black.copy(alpha = 0.18f),
                    line = Color.White.copy(alpha = 0.4f),
                    quoteBar = Color.White.copy(alpha = 0.6f),
                    tableHeaderBg = Color.White.copy(alpha = 0.15f),
                )
            } else {
                MdPalette(
                    text = text,
                    muted = Duo.Muted,
                    link = Duo.Teal,
                    codeBg = Duo.Track,
                    codeBlockBg = Duo.Bg,
                    line = Duo.Line,
                    quoteBar = Duo.Mint,
                    tableHeaderBg = Duo.Bg,
                )
            }
    }
}

@Composable
private fun rememberInline(text: String, palette: MdPalette, lenient: Boolean): AnnotatedString =
    remember(text, palette, lenient) { buildInline(text, palette.link, palette.codeBg, lenient) }

@Composable
private fun MdBlockView(block: MdBlock, style: TextStyle, palette: MdPalette, lenient: Boolean) {
    when (block) {
        is MdBlock.Paragraph -> Text(
            text = rememberInline(block.text, palette, lenient),
            style = style,
            color = palette.text,
        )

        is MdBlock.Heading -> {
            val headingStyle = when (block.level) {
                1 -> style.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Black)
                2 -> style.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Black)
                3 -> style.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Black)
                else -> style.copy(fontWeight = FontWeight.Black)
            }
            Text(
                text = rememberInline(block.text, palette, lenient),
                style = headingStyle,
                color = palette.text,
                modifier = Modifier.padding(top = 3.dp),
            )
        }

        is MdBlock.ListItem -> Row(
            modifier = Modifier.padding(start = (block.indent.coerceAtMost(12) * 4).dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = block.marker,
                style = style,
                color = palette.text,
                modifier = Modifier.widthIn(min = 14.dp),
            )
            Text(
                text = rememberInline(block.text, palette, lenient),
                style = style,
                color = palette.text,
                modifier = Modifier.weight(1f),
            )
        }

        is MdBlock.Quote -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind { drawRect(palette.quoteBar, size = Size(3.dp.toPx(), size.height)) }
                .padding(start = 11.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            block.blocks.forEachIndexed { index, inner ->
                MdBlockView(inner, style, palette, lenient && index == block.blocks.lastIndex)
            }
        }

        is MdBlock.Code -> Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(palette.codeBlockBg),
        ) {
            if (block.language.isNotEmpty()) {
                Text(
                    text = block.language,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted,
                    modifier = Modifier.padding(start = 10.dp, top = 6.dp),
                )
            }
            Text(
                text = block.code,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                ),
                color = palette.text,
                softWrap = false,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(10.dp),
            )
        }

        is MdBlock.Rule -> Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(palette.line),
        )

        is MdBlock.Table -> MdTable(block, style, palette, lenient)
    }
}

@Composable
private fun MdTable(table: MdBlock.Table, style: TextStyle, palette: MdPalette, lenient: Boolean) {
    val shape = RoundedCornerShape(10.dp)
    val cellStyle = style.copy(fontSize = 13.sp, lineHeight = 18.sp)
    // Ширина столбца — по самой длинной ячейке, но в разумных пределах; шире экрана — прокрутка
    val widths = remember(table) {
        table.header.indices.map { col ->
            var longest = table.header[col].length
            for (row in table.rows) longest = maxOf(longest, row[col].length)
            (longest * 8 + 22).coerceIn(64, 220).dp
        }
    }
    val textAligns = remember(table) {
        table.aligns.map {
            when (it) {
                MdAlign.Start -> TextAlign.Start
                MdAlign.Center -> TextAlign.Center
                MdAlign.End -> TextAlign.End
            }
        }
    }

    Column(
        Modifier
            .clip(shape)
            .border(1.dp, palette.line, shape)
            .horizontalScroll(rememberScrollState()),
    ) {
        Row(Modifier.background(palette.tableHeaderBg)) {
            table.header.forEachIndexed { col, cell ->
                Text(
                    text = rememberInline(cell, palette, false),
                    style = cellStyle.copy(fontWeight = FontWeight.Black),
                    color = palette.text,
                    textAlign = textAligns[col],
                    modifier = Modifier.width(widths[col]).padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
        table.rows.forEachIndexed { rowIndex, row ->
            val isLast = lenient && rowIndex == table.rows.lastIndex
            Row(
                Modifier.drawBehind {
                    drawLine(palette.line, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
                },
            ) {
                row.forEachIndexed { col, cell ->
                    Text(
                        text = rememberInline(cell, palette, isLast),
                        style = cellStyle,
                        color = palette.text,
                        textAlign = textAligns[col],
                        modifier = Modifier.width(widths[col]).padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

// ───────── Инлайн-разметка → AnnotatedString ─────────

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
