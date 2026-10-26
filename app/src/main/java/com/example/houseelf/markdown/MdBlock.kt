package com.example.houseelf.markdown

/**
 * Разбор markdown на блоки. Здесь нет ни Compose, ни Android — только текст,
 * поэтому парсер просто проверять юнит-тестами (см. MarkdownParserTest).
 *
 * Поддерживается то, что модели реально выдают в чате: абзацы, заголовки, маркированные
 * и нумерованные списки (с вложенностью и чекбоксами), цитаты, блоки кода, таблицы, линии.
 * Парсер терпим к «недописанному» тексту: пока ответ стримится, незакрытый блок кода
 * или неполная таблица разбираются как есть и не ломают остальное.
 */
sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock

    /** Пункт списка. [marker] — готовый к показу маркер: «•», «1.», «☐», «☑». */
    data class ListItem(val marker: String, val indent: Int, val text: String) : MdBlock
    data class Quote(val blocks: List<MdBlock>) : MdBlock
    data class Code(val language: String, val code: String) : MdBlock
    data object Rule : MdBlock
    data class Table(val header: List<String>, val aligns: List<MdAlign>, val rows: List<List<String>>) : MdBlock
}

enum class MdAlign { Start, Center, End }
