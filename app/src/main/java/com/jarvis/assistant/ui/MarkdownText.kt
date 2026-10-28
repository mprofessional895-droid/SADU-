package com.jarvis.assistant.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.ReplyText

/**
 * Shows model text without the ugly artefacts. Literal "\n" becomes a real line
 * break, **bold** becomes bold, "# heading" becomes a heading, and "- item" gets a
 * coloured dot. Anything the model got wrong is simply dropped.
 */
@Composable
fun MarkdownText(
    text: String,
    color: Color,
    accent: Color,
    modifier: Modifier = Modifier,
    size: Int = 16,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val annotated = remember(text, accent) { markdown(text, accent) }
    Text(
        text = annotated,
        color = color,
        fontSize = size.sp,
        lineHeight = (size + 7).sp,
        modifier = modifier,
        maxLines = maxLines,
        overflow = overflow,
    )
}

private val INLINE = Regex("\\*\\*(.+?)\\*\\*|__(.+?)__|`([^`]+)`|\\*(.+?)\\*|_(.+?)_")
private val HEADING = Regex("^(#{1,6})\\s*(.+)$")
private val BULLET = Regex("^\\s*[-*•]\\s+(.+)$")
private val NUMBERED = Regex("^\\s*(\\d{1,2})[.)]\\s+(.+)$")

private fun markdown(src: String, accent: Color): AnnotatedString = buildAnnotatedString {
    val kept = ReplyText.clean(src).split('\n').filterNot { line ->
        line.contains('|') && line.replace(Regex("[\\s|:-]"), "").isEmpty()
    }
    kept.forEachIndexed { index, original ->
        if (index > 0) append('\n')
        val line = original.trimEnd()

        val heading = HEADING.find(line)
        if (heading != null) {
            val size = when (heading.groupValues[1].length) {
                1 -> 21
                2 -> 19
                else -> 17
            }
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = size.sp, color = accent)) {
                appendInline(heading.groupValues[2], accent)
            }
            return@forEachIndexed
        }

        val bullet = BULLET.find(line)
        if (bullet != null) {
            withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold)) { append("•  ") }
            appendInline(bullet.groupValues[1], accent)
            return@forEachIndexed
        }

        val numbered = NUMBERED.find(line)
        if (numbered != null) {
            withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold)) {
                append(numbered.groupValues[1] + ".  ")
            }
            appendInline(numbered.groupValues[2], accent)
            return@forEachIndexed
        }

        appendInline(line, accent)
    }
}

private fun AnnotatedString.Builder.appendInline(line: String, accent: Color) {
    var last = 0
    for (m in INLINE.findAll(line)) {
        if (m.range.first > last) append(line.substring(last, m.range.first))
        val g = m.groupValues
        when {
            g[1].isNotEmpty() || g[2].isNotEmpty() ->
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(if (g[1].isNotEmpty()) g[1] else g[2])
                }

            g[3].isNotEmpty() ->
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = accent.copy(alpha = 0.14f),
                    )
                ) { append(g[3]) }

            g[4].isNotEmpty() || g[5].isNotEmpty() ->
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(if (g[4].isNotEmpty()) g[4] else g[5])
                }
        }
        last = m.range.last + 1
    }
    if (last < line.length) append(line.substring(last))
}
