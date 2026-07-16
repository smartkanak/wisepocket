package date.oxi.wisepocket.statement

import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/**
 * Bank-agnostic token recognition: what a date looks like, what an amount looks like.
 *
 * Nothing here knows about any particular bank. The rule these sample statements all share — and the
 * only structural assumption this pipeline makes — is that **a transaction row carries a date and an
 * amount**. Everything else (which column, which sign convention, which number format) is discovered
 * per document, not hardcoded.
 */
object Tokens {

    /** A date found in a line, with where it sat so column roles can be worked out. */
    data class DateToken(val text: String, val start: Int, val format: DateFormat)

    /** An amount-shaped token, unparsed — the sign convention isn't known yet at this stage. */
    data class AmountToken(val text: String, val start: Int)

    /**
     * Date layouts seen in the wild. `DMY_NO_YEAR` matters more than it looks: some statements print
     * only `dd.MM.`, so the year has to come from the document and can straddle new year.
     */
    enum class DateFormat(internal val pattern: Regex) {
        /** `31.05.2023` */
        DMY_DOT(Regex("""\b(\d{2})\.(\d{2})\.(\d{4})\b""")),

        /** `25-02-2022` */
        DMY_DASH(Regex("""\b(\d{2})-(\d{2})-(\d{4})\b""")),

        /** `31/05/2023` */
        DMY_SLASH(Regex("""\b(\d{2})/(\d{2})/(\d{4})\b""")),

        /** `2023-05-31` */
        ISO(Regex("""\b(\d{4})-(\d{2})-(\d{2})\b""")),

        /** `02.06.` — year absent, resolved against the statement's own date. */
        DMY_NO_YEAR(Regex("""(?<!\d)(\d{2})\.(\d{2})\.(?!\d)""")),
        ;

        fun find(line: String): List<DateToken> =
            pattern.findAll(line).map { DateToken(it.value, it.range.first, this) }.toList()

        /** Resolves a match to a date. [statementYear] is only consulted for [DMY_NO_YEAR]. */
        fun parse(text: String, statementDate: LocalDate?): LocalDate? {
            val m = pattern.find(text) ?: return null
            val g = m.groupValues
            return runCatching {
                when (this) {
                    DMY_DOT, DMY_DASH, DMY_SLASH -> LocalDate(g[3].toInt(), g[2].toInt(), g[1].toInt())
                    ISO -> LocalDate(g[1].toInt(), g[2].toInt(), g[3].toInt())
                    DMY_NO_YEAR -> {
                        val ref = statementDate ?: return null
                        val month = g[2].toInt()
                        // A row dated later in the year than the statement itself belongs to the previous
                        // year: a January statement legitimately carries December rows.
                        val year = if (month > ref.month.number) ref.year - 1 else ref.year
                        LocalDate(year, month, g[1].toInt())
                    }
                }
            }.getOrNull()
        }
    }

    /**
     * Money-shaped tokens: an optional sign, digits with separators, exactly two decimals, and an
     * optional trailing currency symbol or Soll/Haben marker.
     *
     * Requiring two decimals is what keeps reference numbers, IBANs and dates out — the single most
     * effective filter, and it holds across every sampled bank.
     */
    private val AMOUNT = Regex(
        // Two details here are load-bearing:
        // - the trailing (?![\d.,]) stops this matching "27.05" inside the date "27.05.2019"; a date read
        //   as an amount quietly wrecks every total.
        // - the currency group carries its own optional space rather than a leading \s*, which would eat
        //   the space before a Soll/Haben marker and silently drop the "S" in "19,34 S".
        """(?<![\d.,])([-+]?\d{1,3}(?:[.,]\d{3})*[.,]\d{2}|[-+]?\d+[.,]\d{2})(?![\d.,])(?:\s*(?:€|EUR|\$))?(?:\s+[SH]\b)?""",
    )

    fun amounts(line: String): List<AmountToken> =
        AMOUNT.findAll(line)
            .map { AmountToken(it.value.trim(), it.range.first) }
            .filterNot { looksLikeDate(it.text) }
            .toList()

    /** `12.06` style fragments can match the amount shape; a date token in the same text gives it away. */
    private fun looksLikeDate(text: String): Boolean =
        DateFormat.entries.any { it.pattern.containsMatchIn(text) }

    fun dates(line: String): List<DateToken> = DateFormat.entries.flatMap { it.find(line) }

    /**
     * Balance and summary lines. Every statement has them and they are the one thing that reliably looks
     * exactly like a transaction — "Alter Kontostand vom 01.05.2023   2.075,37 €" carries both a date and
     * an amount. Counting those as transactions doesn't just add rows, it poisons the totals we rely on to
     * verify a profile.
     */
    private val SUMMARY = Regex("""Kontostand|[Ss]aldo|[Bb]alance|Vortrag|Übertrag|Uebertrag""")

    fun isSummaryLine(line: String): Boolean = SUMMARY.containsMatchIn(line)

    /**
     * The one structural assumption: a transaction row has a date and an amount, and isn't a balance line.
     *
     * Deliberately loose otherwise — over-collecting is cheap because the profile step and the review
     * screen both filter later, whereas a row missed here is gone for good.
     */
    fun looksLikeRow(line: String): Boolean =
        !isSummaryLine(line) && dates(line).isNotEmpty() && amounts(line).isNotEmpty()
}
