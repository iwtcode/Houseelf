package com.example.houseelf.markdown

private val HEADING = Regex("^(#{1,6})\\s+(.+?)(?:\\s+#+)?\\s*$")

private val RULE = Regex("^([-*_])(?:\\s*\\1){2,}\\s*$")

private val BULLET = Regex("^(\\s*)([-*+•])\\s+(.*)$")

private val ORDERED = Regex("^(\\s*)(\\d{1,9})[.)]\\s+(.*)$")

private val TABLE_SEPARATOR_CELL = Regex("^:?-+:?$")

fun parseMarkdown(source: String): List<MdBlock> =
    parseLines(source.replace("\r\n", "\n").replace('\r', '\n').split('\n'))

private fun parseLines(lines: List<String>): List<MdBlock> {
    val out = ArrayList<MdBlock>()
    val para = ArrayList<String>()

    fun flushParagraph() {
        if (para.isNotEmpty()) {
            out.add(MdBlock.Paragraph(para.joinToString("\n")))
            para.clear()
        }
    }

    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        // Пустая строка — конец абзаца
        if (trimmed.isEmpty()) {
            flushParagraph()
            i++
            continue
        }

        // Блок кода: ``` или ~~~ (не закрыт — значит, до конца текста, так бывает при стриминге)
        val fenceLine = line.trimStart()
        if (fenceLine.startsWith("```") || fenceLine.startsWith("~~~")) {
            val fenceChar = fenceLine[0]
            val fenceLen = fenceLine.takeWhile { it == fenceChar }.length
            val info = fenceLine.substring(fenceLen).trim()
            // «```код```» в одной строке — это не блок, а обычный текст с инлайн-кодом
            if (!(fenceChar == '`' && info.contains('`'))) {
                flushParagraph()
                val code = ArrayList<String>()
                i++
                while (i < lines.size && !isFenceClose(lines[i], fenceChar, fenceLen)) {
                    code.add(lines[i])
                    i++
                }
                if (i < lines.size) i++ // пропускаем закрывающую ограду
                out.add(MdBlock.Code(info.substringBefore(' '), code.joinToString("\n")))
                continue
            }
        }

        // Заголовок
        val heading = HEADING.matchEntire(trimmed)
        if (heading != null) {
            flushParagraph()
            out.add(MdBlock.Heading(heading.groupValues[1].length, heading.groupValues[2].trim()))
            i++
            continue
        }

        // Горизонтальная линия: ---, ***, ___
        if (RULE.matches(trimmed)) {
            flushParagraph()
            out.add(MdBlock.Rule)
            i++
            continue
        }

        // Цитата
        if (trimmed.startsWith(">")) {
            flushParagraph()
            val inner = ArrayList<String>()
            while (i < lines.size && lines[i].trim().startsWith(">")) {
                inner.add(lines[i].trim().removePrefix(">").removePrefix(" "))
                i++
            }
            out.add(MdBlock.Quote(parseLines(inner)))
            continue
        }

        // Таблица: строка с «|» и следом строка-разделитель |---|---|
        if (trimmed.contains('|') && i + 1 < lines.size) {
            val aligns = tableAligns(lines[i + 1])
            val header = splitRow(trimmed)
            if (aligns != null && header.size == aligns.size) {
                flushParagraph()
                i += 2
                val rows = ArrayList<List<String>>()
                while (i < lines.size && lines[i].isNotBlank() && lines[i].contains('|')) {
                    val cells = splitRow(lines[i])
                    rows.add(List(header.size) { idx -> cells.getOrElse(idx) { "" } })
                    i++
                }
                out.add(MdBlock.Table(header, aligns, rows))
                continue
            }
        }

        // Пункт списка. Нумерованный посреди абзаца начинает список только с «1.»:
        // так «В 2024. Году» не превращается в список.
        val item = BULLET.matchEntire(line)
            ?: ORDERED.matchEntire(line)?.takeIf { para.isEmpty() || it.groupValues[2] == "1" }
        if (item != null) {
            flushParagraph()
            val ordered = item.groupValues[2].first().isDigit()
            var marker = if (ordered) item.groupValues[2] + "." else "•"
            var text = item.groupValues[3].trim()
            if (!ordered) {
                if (text.startsWith("[ ] ")) {
                    marker = "☐"
                    text = text.substring(4).trim()
                } else if (text.startsWith("[x] ", ignoreCase = true)) {
                    marker = "☑"
                    text = text.substring(4).trim()
                }
            }
            val indent = indentOf(item.groupValues[1])
            i++
            val full = StringBuilder(text)
            while (i < lines.size && isListContinuation(lines[i])) {
                full.append('\n').append(lines[i].trim())
                i++
            }
            out.add(MdBlock.ListItem(marker, indent, full.toString()))
            continue
        }

        para.add(trimmed)
        i++
    }
    flushParagraph()
    return out
}

private fun isFenceClose(line: String, fenceChar: Char, fenceLen: Int): Boolean {
    val t = line.trim()
    return t.length >= fenceLen && t.all { it == fenceChar }
}

private fun indentOf(whitespace: String): Int {
    var n = 0
    for (c in whitespace) n += if (c == '\t') 4 else 1
    return n
}

/** Строка с отступом, продолжающая предыдущий пункт списка. */
private fun isListContinuation(line: String): Boolean {
    if (line.isBlank() || !line[0].isWhitespace()) return false
    if (BULLET.matches(line) || ORDERED.matches(line)) return false
    val t = line.trimStart()
    return !t.startsWith("```") && !t.startsWith("~~~") && !t.startsWith(">")
}

/** Выравнивание столбцов, если [line] — строка-разделитель таблицы, иначе null. */
private fun tableAligns(line: String): List<MdAlign>? {
    if (!line.contains('|')) return null
    val cells = splitRow(line)
    if (cells.isEmpty() || !cells.all { TABLE_SEPARATOR_CELL.matches(it) }) return null
    return cells.map {
        when {
            it.startsWith(":") && it.endsWith(":") -> MdAlign.Center
            it.endsWith(":") -> MdAlign.End
            else -> MdAlign.Start
        }
    }
}

/** Делит строку таблицы на ячейки; «\|» и «|» внутри `кода` разделителями не считаются. */
private fun splitRow(line: String): List<String> {
    var t = line.trim()
    if (t.startsWith("|")) t = t.substring(1)
    if (t.endsWith("|") && !t.endsWith("\\|")) t = t.dropLast(1)
    val cells = ArrayList<String>()
    val cur = StringBuilder()
    var inCode = false
    var k = 0
    while (k < t.length) {
        val c = t[k]
        if (c == '\\' && k + 1 < t.length && t[k + 1] == '|') {
            cur.append('|')
            k += 2
            continue
        }
        if (c == '`') inCode = !inCode
        if (c == '|' && !inCode) {
            cells.add(cur.toString().trim())
            cur.setLength(0)
        } else {
            cur.append(c)
        }
        k++
    }
    cells.add(cur.toString().trim())
    return cells
}
