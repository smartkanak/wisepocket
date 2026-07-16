package date.oxi.wisepocket

import date.oxi.wisepocket.pdf.LayoutText
import date.oxi.wisepocket.pdf.PdfChar
import date.oxi.wisepocket.pdf.PdfPageText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the geometry → text stage, where every bug is a silently wrong number rather than a crash.
 *
 * Each case here reproduces a failure seen on a real statement. They're written against synthetic
 * characters instead of a PDF so they run anywhere and stay readable — the shapes (tiny box on the
 * baseline for a full stop, floating mid-height box for a hyphen, narrow glyph box for a "1") are what
 * broke the real thing.
 */
class LayoutTextTest {

    // A line of body text: letters sit between these, with the baseline at the bottom.
    private val letterTop = 90f
    private val baseline = 100f
    private val advance = 6f

    /** A letter-shaped glyph: full x-height, glyph box narrower than its advance. */
    private fun letter(c: Char, x: Float, space: Boolean = false, glyphWidth: Float = 4f) =
        PdfChar(c, left = x, top = letterTop, right = x + glyphWidth, bottom = baseline, precededBySpace = space)

    /** A full stop or comma: a tiny box resting on the baseline — its centre is far below the letters'. */
    private fun dot(c: Char, x: Float) =
        PdfChar(c, left = x, top = baseline - 2f, right = x + 1.2f, bottom = baseline)

    /** A hyphen/minus: a small box floating at mid-height, touching neither baseline nor x-height. */
    private fun hyphen(x: Float, space: Boolean = false) =
        PdfChar('-', left = x, top = 94f, right = x + 3f, bottom = 96f, precededBySpace = space)

    private fun page(chars: List<PdfChar>) =
        PdfPageText(index = 0, widthPoints = 600f, heightPoints = 800f, chars = chars)

    /** Lays out a string of plain letters starting at [x], one advance apart. */
    private fun word(text: String, x: Float, space: Boolean = false): List<PdfChar> =
        text.mapIndexed { i, c -> letter(c, x + i * advance, space = space && i == 0) }

    @Test
    fun fullStopsStayOnTheirLine() {
        // Grouping by box centre exiles a full stop to its own line, because its tiny box sits on the
        // baseline while a letter's centre is half an x-height higher. That shreds every date on the page.
        val chars = listOf(
            letter('2', 0f), letter('7', 6f), dot('.', 12f),
            letter('0', 18f), letter('5', 24f), dot('.', 30f),
            letter('2', 36f), letter('0', 42f), letter('1', 48f), letter('9', 54f),
        )
        assertEquals(listOf("27.05.2019"), LayoutText.linesOf(page(chars)))
    }

    @Test
    fun minusSignsStayOnTheirLine() {
        // Grouping by baseline fixes full stops but strands the hyphen, which touches no baseline at all.
        // Losing a minus sign on a bank statement turns spending into income.
        val chars = listOf(hyphen(0f)) + listOf(
            letter('5', 6f), dot(',', 12f), letter('9', 18f), letter('0', 24f),
        )
        assertEquals(listOf("-5,90"), LayoutText.linesOf(page(chars)))
    }

    @Test
    fun separateLinesDoNotMerge() {
        val first = word("REWE", 0f)
        val second = first.map { it.copy(top = it.top + 20f, bottom = it.bottom + 20f) }
        assertEquals(listOf("REWE", "REWE"), LayoutText.linesOf(page(first + second)))
    }

    @Test
    fun narrowGlyphsDoNotSplitWords() {
        // "1" and "." have glyph boxes far narrower than their advance, so the gap to the next character
        // is as wide as a real space. Word breaks must come from the PDF's own spacing, not from geometry.
        val chars = listOf(
            letter('1', 0f, glyphWidth = 1.2f),
            letter('1', 6f, glyphWidth = 1.2f),
            dot('.', 12f),
            letter('0', 18f, glyphWidth = 1.2f),
            letter('5', 24f, glyphWidth = 1.2f),
        )
        assertEquals(listOf("11.05"), LayoutText.linesOf(page(chars)))
    }

    @Test
    fun spacesInTheDocumentBecomeSpaces() {
        val chars = word("REWE", 0f) + word("Markt", 30f, space = true)
        val line = LayoutText.linesOf(page(chars)).single()
        assertTrue(line.startsWith("REWE"), "was: $line")
        assertTrue(line.contains("REWE ") || line.contains("REWE  "), "expected a gap: $line")
        assertTrue(line.trim().endsWith("Markt"), "was: $line")
    }

    @Test
    fun wideColumnGapsSurviveAsRunsOfSpaces() {
        // The parsers key on "date … description … amount" being separated. If words drifted or collapsed
        // together, a row would stop looking like a row.
        val chars = word("REWE", 0f) + word("48", 300f, space = true)
        val line = LayoutText.linesOf(page(chars)).single()
        assertTrue(Regex("""REWE\s{2,}48""").containsMatchIn(line), "was: |$line|")
    }

    @Test
    fun wordsDoNotDriftRightAcrossALine() {
        // Columns are derived from the median *advance*. Using the median glyph width instead (which is
        // narrower) makes every computed column overshoot, so each successive word is padded further out
        // than the last — "27.05.201   9".
        val chars = word("AA", 0f) +
            word("BB", 60f, space = true) +
            word("CC", 120f, space = true) +
            word("DD", 180f, space = true)
        val line = LayoutText.linesOf(page(chars)).single()

        // Each word should land near its true column (x / advance), not progressively further right.
        assertEquals(0, line.indexOf("AA"))
        assertTrue(line.indexOf("DD") in 28..32, "DD at ${line.indexOf("DD")} in |$line|")
    }

    @Test
    fun emptyPageYieldsNoLines() {
        assertEquals(emptyList(), LayoutText.linesOf(page(emptyList())))
    }

    @Test
    fun zeroSizedBoxesAreIgnored() {
        // A broken extractor can report an empty box; it carries no position, so it can't be placed.
        val chars = word("OK", 0f) + listOf(PdfChar('X', 0f, 0f, 0f, 0f))
        assertEquals(listOf("OK"), LayoutText.linesOf(page(chars)))
    }
}
