package com.sadu.assistant.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sadu.assistant.ui.Ink

/**
 * A reply drawn with its structure intact: headings, lists, tables and code, and inline
 * emphasis inside them. Plain paragraphs stay as plain text, so a short answer does not look
 * like a document.
 */
@Composable
fun MarkdownBody(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Ink.Text,
    accent: Color = Ink.Accent,
    size: Int = 15,
) {
    val blocks = remember(text) { Markdown.parse(text) }
    Column(modifier) {
        blocks.forEachIndexed { index, block ->
            if (index > 0) Spacer(Modifier.height(10.dp))
            when (block) {
                is MdBlock.Heading -> {
                    val px = when (block.level) {
                        1 -> size + 8
                        2 -> size + 5
                        3 -> size + 3
                        else -> size + 1
                    }
                    Text(
                        text = inline(block.text, accent, bold = true),
                        color = color,
                        fontSize = px.sp,
                        lineHeight = (px + 8).sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                is MdBlock.Paragraph -> Text(
                    text = inline(block.text, accent),
                    color = color,
                    fontSize = size.sp,
                    lineHeight = (size + 7).sp,
                )

                is MdBlock.Bullet -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    block.items.forEach { item ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                text = "\u2022",
                                color = accent,
                                fontSize = size.sp,
                                lineHeight = (size + 7).sp,
                                modifier = Modifier.width(14.dp),
                            )
                            Text(
                                text = inline(item, accent),
                                color = color,
                                fontSize = size.sp,
                                lineHeight = (size + 7).sp,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                is MdBlock.Numbered -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    block.items.forEach { (number, item) ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                text = "$number.",
                                color = accent,
                                fontSize = size.sp,
                                lineHeight = (size + 7).sp,
                                modifier = Modifier.width(26.dp),
                            )
                            Text(
                                text = inline(item, accent),
                                color = color,
                                fontSize = size.sp,
                                lineHeight = (size + 7).sp,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                is MdBlock.Code -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Ink.Elevated)
                        .padding(12.dp),
                ) {
                    if (block.language.isNotBlank()) {
                        Text(
                            text = block.language,
                            color = Ink.TextDim,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    Text(
                        text = block.code,
                        color = color,
                        fontFamily = FontFamily.Monospace,
                        fontSize = (size - 1).sp,
                        lineHeight = (size + 5).sp,
                        softWrap = false,
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    )
                }

                is MdBlock.Table -> TableBlock(block.header, block.rows, accent, color, size)
            }
        }
    }
}

@Composable
private fun TableBlock(
    header: List<String>,
    rows: List<List<String>>,
    accent: Color,
    color: Color,
    size: Int,
) {
    val columns = maxOf(header.size, rows.maxOfOrNull { it.size } ?: 0)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Ink.Elevated)
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 6.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            repeat(columns) { c ->
                Text(
                    text = inline(header.getOrElse(c) { "" }, accent, bold = true),
                    color = color,
                    fontSize = (size - 1).sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(120.dp),
                )
            }
        }
        Box {
            Column(Modifier.fillMaxWidth().height(1.dp).background(Ink.Border)) { }
        }
        rows.forEach { row ->
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                repeat(columns) { c ->
                    Text(
                        text = inline(row.getOrElse(c) { "" }, accent),
                        color = color,
                        fontSize = (size - 1).sp,
                        modifier = Modifier.width(120.dp),
                    )
                }
            }
        }
    }
}

private val INLINE = Regex("\\*\\*(.+?)\\*\\*|__(.+?)__|`([^`]+)`|\\*(.+?)\\*|_(.+?)_")

private fun inline(src: String, accent: Color, bold: Boolean = false): AnnotatedString =
    buildAnnotatedString {
        var last = 0
        for (m in INLINE.findAll(src)) {
            if (m.range.first > last) {
                withStyle(SpanStyle(fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)) {
                    append(src.substring(last, m.range.first))
                }
            }
            val g = m.groupValues
            when {
                g[1].isNotEmpty() || g[2].isNotEmpty() ->
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(if (g[1].isNotEmpty()) g[1] else g[2])
                    }

                g[3].isNotEmpty() -> withStyle(
                    SpanStyle(fontFamily = FontFamily.Monospace, background = accent.copy(alpha = 0.12f)),
                ) { append(g[3]) }

                else -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(if (g[4].isNotEmpty()) g[4] else g[5])
                }
            }
            last = m.range.last + 1
        }
        if (last < src.length) append(src.substring(last))
    }
