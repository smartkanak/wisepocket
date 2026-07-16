package date.oxi.wisepocket.pdf

import kotlin.math.roundToInt

/**
 * Rebuilds the visual layout of a page as monospaced text: characters are grouped into lines and each
 * word is placed in the column its x-position implies. The result resembles `pdftotext -layout`, which is
 * the shape the statement pipeline reads.
 *
 * Working from geometry rather than the PDF's internal text order matters for statements: extraction
 * order isn't guaranteed to match visual order, and column alignment is often the only signal for which
 * number on a row is the amount.
 */
object LayoutText {

    /** A character joins a line when this much of its box lies inside the line's vertical band. */
    private const val OVERLAP_FRACTION = 0.25f

    /** Fallback column width (points) when a page has no usable character boxes. */
    private const val FALLBACK_UNIT = 5f

    /**
     * Smallest left-to-left step counted as a real advance (points).
     *
     * Filters out characters stacked at the same x — overlapping glyphs and zero-width marks would
     * otherwise drag the median advance toward zero and collapse every column.
     */
    private const val MIN_ADVANCE = 0.1f

    fun linesOf(page: PdfPageText): List<String> {
        val chars = page.chars.filter { it.width > 0f && it.height > 0f }
        if (chars.isEmpty()) return emptyList()

        val lines = groupIntoLines(chars)
        val unit = columnUnit(lines)
        return lines.map { renderLine(it, unit) }
    }

    fun linesOf(doc: PdfDocText): List<String> = doc.pages.flatMap { linesOf(it) }

    /**
     * Column width: the median *advance* — the left-to-left distance between neighbouring characters.
     *
     * Not the median glyph width, which is what the boxes give and is systematically narrower than the
     * real advance. Underestimating here makes every computed column drift rightward of the text actually
     * emitted, so each word gets padded further out than the last ("27.05.201   9").
     */
    private fun columnUnit(lines: List<List<PdfChar>>): Float {
        val advances = lines.flatMap { line ->
            line.sortedBy { it.left }.zipWithNext { a, b -> b.left - a.left }.filter { it > MIN_ADVANCE }
        }.sorted()
        if (advances.isEmpty()) return FALLBACK_UNIT
        val median = advances[advances.size / 2]
        return if (median > MIN_ADVANCE) median else FALLBACK_UNIT
    }

    /**
     * Groups characters into visual lines by **vertical overlap** with the line's band.
     *
     * Neither of the obvious alternatives survives contact with a real statement. Grouping by box *centre*
     * exiles full stops and commas onto their own line — a `.` is a tiny box on the baseline, so its centre
     * sits far below the letters' — which shreds every date and amount on the page. Grouping by *baseline*
     * fixes punctuation but strands the minus sign, because a hyphen floats at mid-height and touches no
     * baseline at all. Losing minus signs on a bank statement is about as bad as it gets.
     *
     * Overlap handles all three: a full stop, a hyphen and a capital letter all fall *within* the vertical
     * band of their line, however their boxes are shaped. The threshold is low because a comma clears the
     * baseline by only a fraction of its own height.
     */
    private fun groupIntoLines(chars: List<PdfChar>): List<List<PdfChar>> {
        val sorted = chars.sortedBy { it.top }
        // The line being built is always the last one in `lines`, so there is no separate `current` to keep
        // in sync and no leftover to flush after the loop.
        val lines = mutableListOf<MutableList<PdfChar>>()
        var bandTop = 0f
        var bandBottom = 0f

        for (c in sorted) {
            val current = lines.lastOrNull()
            val overlap = if (current == null) {
                Float.NEGATIVE_INFINITY
            } else {
                minOf(c.bottom, bandBottom) - maxOf(c.top, bandTop)
            }

            if (current != null && overlap >= c.height * OVERLAP_FRACTION) {
                current.add(c)
                bandTop = minOf(bandTop, c.top)
                bandBottom = maxOf(bandBottom, c.bottom)
            } else {
                lines.add(mutableListOf(c))
                bandTop = c.top
                bandBottom = c.bottom
            }
        }
        return lines
    }

    /**
     * Renders one line, placing each word at the column its x-position implies.
     *
     * Word boundaries come from [PdfChar.precededBySpace] — the PDF's own spacing — rather than from gaps
     * between glyph boxes, which are tight outlines and make narrow characters look like word breaks.
     */
    private fun renderLine(line: List<PdfChar>, unit: Float): String {
        val sb = StringBuilder()
        for ((index, c) in line.sortedBy { it.left }.withIndex()) {
            if (index == 0 || c.precededBySpace) {
                val column = (c.left / unit).roundToInt()
                if (column > sb.length) {
                    sb.append(" ".repeat(column - sb.length))
                } else if (sb.isNotEmpty()) {
                    // Column already passed (the line ran long) — keep the words apart regardless.
                    sb.append(' ')
                }
            }
            sb.append(c.char)
        }
        return sb.toString().trimEnd()
    }
}
