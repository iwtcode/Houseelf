package com.example.houseelf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun paragraphsAreSplitByBlankLine() {
        val blocks = parseMarkdown("Привет\nкак дела\n\nВторой абзац")
        assertEquals(
            listOf(MdBlock.Paragraph("Привет\nкак дела"), MdBlock.Paragraph("Второй абзац")),
            blocks,
        )
    }

    @Test
    fun headingsAndRule() {
        val blocks = parseMarkdown("# Один\n## Два ##\n---\nтекст")
        assertEquals(
            listOf(
                MdBlock.Heading(1, "Один"),
                MdBlock.Heading(2, "Два"),
                MdBlock.Rule,
                MdBlock.Paragraph("текст"),
            ),
            blocks,
        )
    }

    @Test
    fun hashWithoutSpaceIsNotHeading() {
        assertEquals(listOf(MdBlock.Paragraph("#тег")), parseMarkdown("#тег"))
    }

    @Test
    fun bulletAndOrderedLists() {
        val blocks = parseMarkdown("- молоко\n* хлеб\n\n1. раз\n2) два")
        assertEquals(
            listOf(
                MdBlock.ListItem("•", 0, "молоко"),
                MdBlock.ListItem("•", 0, "хлеб"),
                MdBlock.ListItem("1.", 0, "раз"),
                MdBlock.ListItem("2.", 0, "два"),
            ),
            blocks,
        )
    }

    @Test
    fun nestedListAndContinuation() {
        val blocks = parseMarkdown("- родитель\n  продолжение\n    - вложенный")
        assertEquals(
            listOf(
                MdBlock.ListItem("•", 0, "родитель\nпродолжение"),
                MdBlock.ListItem("•", 4, "вложенный"),
            ),
            blocks,
        )
    }

    @Test
    fun taskList() {
        val blocks = parseMarkdown("- [ ] купить\n- [x] сделать")
        assertEquals(
            listOf(MdBlock.ListItem("☐", 0, "купить"), MdBlock.ListItem("☑", 0, "сделать")),
            blocks,
        )
    }

    @Test
    fun orderedNumberInsideParagraphIsNotList() {
        val blocks = parseMarkdown("Это было в\n2024. году")
        assertEquals(listOf(MdBlock.Paragraph("Это было в\n2024. году")), blocks)
    }

    @Test
    fun boldAtLineStartIsNotBullet() {
        assertEquals(listOf(MdBlock.Paragraph("**Важно:** текст")), parseMarkdown("**Важно:** текст"))
    }

    @Test
    fun codeBlockClosedAndUnclosed() {
        val closed = parseMarkdown("до\n```kotlin\nval x = 1\n```\nпосле")
        assertEquals(
            listOf(MdBlock.Paragraph("до"), MdBlock.Code("kotlin", "val x = 1"), MdBlock.Paragraph("после")),
            closed,
        )
        // Так выглядит ответ, который ещё пишется
        val open = parseMarkdown("```\nline1\nline2")
        assertEquals(listOf(MdBlock.Code("", "line1\nline2")), open)
    }

    @Test
    fun markdownInsideCodeBlockIsNotParsed() {
        val blocks = parseMarkdown("```\n# не заголовок\n- не список\n```")
        assertEquals(listOf(MdBlock.Code("", "# не заголовок\n- не список")), blocks)
    }

    @Test
    fun quoteIsParsedRecursively() {
        val blocks = parseMarkdown("> цитата\n> - пункт")
        assertEquals(
            listOf(MdBlock.Quote(listOf(MdBlock.Paragraph("цитата"), MdBlock.ListItem("•", 0, "пункт")))),
            blocks,
        )
    }

    @Test
    fun table() {
        val blocks = parseMarkdown("| Имя | Возраст |\n|:---|---:|\n| Аня | 5 |\n| Боря |")
        val table = blocks.single() as MdBlock.Table
        assertEquals(listOf("Имя", "Возраст"), table.header)
        assertEquals(listOf(MdAlign.Start, MdAlign.End), table.aligns)
        assertEquals(listOf(listOf("Аня", "5"), listOf("Боря", "")), table.rows)
    }

    @Test
    fun pipeWithoutSeparatorIsParagraph() {
        assertTrue(parseMarkdown("a | b\nc | d").single() is MdBlock.Paragraph)
    }

    @Test
    fun windowsLineEndings() {
        val blocks = parseMarkdown("# Заголовок\r\n\r\n- пункт")
        assertEquals(listOf(MdBlock.Heading(1, "Заголовок"), MdBlock.ListItem("•", 0, "пункт")), blocks)
    }
}
