package com.example.houseelf.markdown

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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.houseelf.ui.theme.Duo

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
