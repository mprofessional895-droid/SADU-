package com.sadu.assistant.ui.chat

/**
 * The blocks a reply is made of.
 *
 * Models write Markdown whether or not anyone asked for it: headings, lists, fenced code and
 * tables. Rendering those as plain text loses the structure that makes an answer readable, so
 * the text is split into blocks first and each block is drawn in its own way.
 */
sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullet(val items: List<String>) : MdBlock
    data class Numbered(val items: List<Pair<String, String>>) : MdBlock
    data class Code(val language: String, val code: String) : MdBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock
}

object Markdown {

    private val HEADING = Regex("^(#{1,6})\\s*(.+)$")
    private val BULLET = Regex("^\\s*[-*+\u2022]\\s+(.+)$")
    private val NUMBERED = Regex("^\\s*(\\d{1,2})[.)]\\s+(.+)$")
    private val FENCE = Regex("^\\s*(```|~~~)(.*)$")

    /** True when the line is a table's dashed separator, e.g. |---|:--:|. */
    fun isSeparator(line: String): Boolean {
        val t = line.trim()
        if (!t.contains('|')) return false
        val cells = cells(t)
        return cells.isNotEmpty() && cells.all { cell ->
            val c = cell.trim()
            c.isNotEmpty() && c.all { it == '-' || it == ':' }
        }
    }

    fun cells(line: String): List<String> {
        var t = line.trim()
        if (t.startsWith("|")) t = t.substring(1)
        if (t.endsWith("|")) t = t.dropLast(1)
        return t.split('|').map { it.trim() }
    }

    /**
     * Only the escapes and control tokens are undone here. ReplyText.clean strips code fences,
     * which would flatten every code block and table into a paragraph.
     */
    private fun prepare(raw: String): String = raw
        .replace("\\r\\n", "\n")
        .replace("\\n", "\n")
        .replace("\\t", "    ")
        .replace("\\\"", "\"")
        .replace("<end_of_turn>", "")
        .replace("<start_of_turn>", "")
        .replace("<eos>", "")
        .replace("<bos>", "")
        .replace("\r\n", "\n")
        .replace('\r', '\n')

    /** Reads a reply, keeping its structure. Safe on half-arrived text. */
    fun parse(raw: String): List<MdBlock> {
        val text = prepare(raw)
        if (text.isBlank()) return emptyList()
        val lines = text.split('\n')
        val out = ArrayList<MdBlock>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val fence = FENCE.find(line)
            if (fence != null) {
                val language = fence.groupValues[2].trim()
                val body = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```") &&
                    !lines[i].trim().startsWith("~~~")
                ) {
                    if (body.isNotEmpty()) body.append('\n')
                    body.append(lines[i])
                    i++
                }
                i++
                out.add(MdBlock.Code(language, body.toString()))
                continue
            }

            if (line.contains('|') && i + 1 < lines.size && isSeparator(lines[i + 1])) {
                val header = cells(line)
                val rows = ArrayList<List<String>>()
                i += 2
                while (i < lines.size && lines[i].contains('|') && lines[i].isNotBlank()) {
                    rows.add(cells(lines[i]))
                    i++
                }
                out.add(MdBlock.Table(header, rows))
                continue
            }

            val heading = HEADING.find(line)
            if (heading != null) {
                out.add(MdBlock.Heading(heading.groupValues[1].length, heading.groupValues[2].trim()))
                i++
                continue
            }

            val bullet = BULLET.find(line)
            if (bullet != null) {
                val items = ArrayList<String>()
                while (i < lines.size) {
                    val b = BULLET.find(lines[i]) ?: break
                    items.add(b.groupValues[1].trim())
                    i++
                }
                out.add(MdBlock.Bullet(items))
                continue
            }

            val numbered = NUMBERED.find(line)
            if (numbered != null) {
                val items = ArrayList<Pair<String, String>>()
                while (i < lines.size) {
                    val n = NUMBERED.find(lines[i]) ?: break
                    items.add(n.groupValues[1] to n.groupValues[2].trim())
                    i++
                }
                out.add(MdBlock.Numbered(items))
                continue
            }

            if (line.isBlank()) {
                i++
                continue
            }

            val para = StringBuilder(line.trim())
            i++
            while (i < lines.size && lines[i].isNotBlank() &&
                !lines[i].contains('|') &&
                HEADING.find(lines[i]) == null &&
                BULLET.find(lines[i]) == null &&
                NUMBERED.find(lines[i]) == null &&
                FENCE.find(lines[i]) == null
            ) {
                para.append('\n').append(lines[i].trim())
                i++
            }
            out.add(MdBlock.Paragraph(para.toString()))
        }
        return out
    }
}
