package id.haseda.margaretha.timeline

/** One subtitle cue. Times are microseconds on the project timeline. */
data class SubtitleCue(val startUs: Long, val endUs: Long, val text: String)

/**
 * SubRip (.srt) reader/writer. The parser is tolerant: BOM, CRLF, missing index lines, "." or ","
 * as the millisecond separator, short hour fields, position hints after the time range and basic
 * HTML/ASS styling tags are all accepted. Malformed blocks are skipped and counted, never guessed.
 */
object Srt {
    data class ParseResult(val cues: List<SubtitleCue>, val skipped: Int)

    private val timeLine = Regex("""^\s*(\d{1,3}):(\d{1,2}):(\d{1,2})[,.](\d{1,3})\s*-->\s*(\d{1,3}):(\d{1,2}):(\d{1,2})[,.](\d{1,3})""")
    private val tags = Regex("""<[^>]{1,40}>|\{\\[^}]{1,40}\}""")

    fun parse(text: String): ParseResult {
        val lines = text.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val cues = mutableListOf<SubtitleCue>()
        var skipped = 0
        var i = 0
        while (i < lines.size) {
            val match = timeLine.find(lines[i])
            if (match == null) {
                i++
                continue
            }
            i++
            val body = StringBuilder()
            // The body ends at a blank line, or right before the next cue's "index + time" pair.
            while (i < lines.size && lines[i].isNotBlank() && timeLine.find(lines[i]) == null) {
                val nextIsTime = i + 1 < lines.size && timeLine.find(lines[i + 1]) != null
                if (nextIsTime && lines[i].trim().all(Char::isDigit)) break
                if (body.isNotEmpty()) body.append('\n')
                body.append(lines[i].trim())
                i++
            }
            val g = match.groupValues
            val start = toUs(g[1], g[2], g[3], g[4])
            val end = toUs(g[5], g[6], g[7], g[8])
            val clean = tags.replace(body.toString(), "").trim()
            if (start == null || end == null || end <= start || clean.isEmpty()) skipped++ else cues += SubtitleCue(start, end, clean)
        }
        return ParseResult(cues.sortedBy { it.startUs }, skipped)
    }

    private fun toUs(h: String, m: String, s: String, frac: String): Long? {
        val minutes = m.toLong()
        val seconds = s.toLong()
        if (minutes > 59 || seconds > 59) return null
        val ms = frac.padEnd(3, '0').toLong()
        return ((h.toLong() * 3600 + minutes * 60 + seconds) * 1000 + ms) * US_PER_MS
    }

    fun format(cues: List<SubtitleCue>): String = buildString {
        cues.sortedBy { it.startUs }.forEachIndexed { index, cue ->
            append(index + 1).append('\n')
            append(stamp(cue.startUs)).append(" --> ").append(stamp(cue.endUs)).append('\n')
            append(cue.text.lines().filter { it.isNotBlank() }.joinToString("\n")).append("\n\n")
        }
    }

    private fun stamp(timeUs: Long): String {
        val totalMs = timeUs.coerceAtLeast(0L) / US_PER_MS
        val h = totalMs / 3_600_000
        val m = totalMs / 60_000 % 60
        val s = totalMs / 1000 % 60
        val ms = totalMs % 1000
        fun p(v: Long, n: Int) = v.toString().padStart(n, '0')
        return "${p(h, 2)}:${p(m, 2)}:${p(s, 2)},${p(ms, 3)}"
    }

    /** Turns cues into subtitle text items; cues shorter than the engine minimum are stretched. */
    fun toTextItems(cues: List<SubtitleCue>, ids: List<String>, style: TextStyle = TextStyle.SUBTITLE): List<TextItem> {
        require(ids.size == cues.size) { "ids must match cues" }
        return cues.mapIndexed { i, cue ->
            TextItem(
                id = ids[i],
                text = cue.text.take(TextItem.MAX_LENGTH),
                startUs = cue.startUs,
                durationUs = (cue.endUs - cue.startUs).coerceAtLeast(TextItem.MIN_US),
                x = 0.5f,
                y = TextItem.SUBTITLE_Y,
                style = style,
            )
        }
    }

    fun fromTextItems(items: List<TextItem>): List<SubtitleCue> = items.map { SubtitleCue(it.startUs, it.endUs, it.text) }
}
