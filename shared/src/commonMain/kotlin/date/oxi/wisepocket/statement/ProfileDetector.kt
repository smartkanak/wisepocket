package date.oxi.wisepocket.statement

import date.oxi.wisepocket.statement.Amounts.NumberFormat
import date.oxi.wisepocket.statement.Amounts.SignConvention
import kotlinx.datetime.LocalDate

private const val LOG = "WP-PDF"

/**
 * Works out a document's conventions without knowing which bank issued it.
 *
 * The search space is tiny — a handful of date formats × 2 number formats × 3 sign conventions — so
 * rather than guessing, we **try them all and check the answer**. Most statements print their own old and
 * new balance, which makes the check exact: the right profile is the one whose transactions sum to
 * `new − old` to the cent. A wrong sign convention misses by a mile, so this is a real proof, not a
 * heuristic.
 *
 * When a document states no balances, there's nothing to check against and we fall back
 * to [BalanceHints]-free scoring — or hand off to the LLM, which is the only thing left that can read a
 * layout it has never seen.
 */
object ProfileDetector {

    /**
     * The document's own opening/closing balance lines, kept as raw text.
     *
     * Raw, not parsed, on purpose: `1,000.00` is one thousand or one, depending on a number format we
     * haven't determined yet. Reading them too early gave `1.0` and broke reconciliation — so each
     * candidate profile parses them with its own format via [expectedNet].
     */
    data class BalanceHints(val openingText: String?, val closingText: String?) {

        /** Net change the document claims, read in [format]. Null if the document didn't state both. */
        fun expectedNet(format: NumberFormat): Double? {
            val opening = openingText?.let { parseBalance(it, format) } ?: return null
            val closing = closingText?.let { parseBalance(it, format) } ?: return null
            return closing - opening
        }

        /** A balance is written unsigned; a trailing `S` (Soll) is what makes it negative. */
        private fun parseBalance(token: String, format: NumberFormat): Double? {
            val negative = Regex("""\bS\b""").containsMatchIn(token)
            val magnitude = Amounts.parse(token, format, SignConvention.EXPLICIT_MINUS) ?: return null
            return if (negative) -kotlin.math.abs(magnitude) else magnitude
        }
    }

    data class Detection(
        val profile: StatementProfile?,
        val hints: BalanceHints,
        /** Every profile consistent with the text. One candidate means the document decided it for us. */
        val candidates: List<StatementProfile> = emptyList(),
    ) {
        /**
         * True only when the text genuinely leaves the conventions open — the sole case worth spending a
         * minute of on-device inference on.
         */
        val isAmbiguous: Boolean get() = candidates.size > 1
    }

    /**
     * Language-agnostic-ish balance markers. German statements dominate our samples; the words are a
     * hint, not a requirement — a document that doesn't match simply gets no free verification.
     */
    private val OPENING = Regex("""(?:alter|Alter|Vortrag|opening|Opening|previous|Previous)[^\n]{0,30}?(?:Kontostand|[Ss]aldo|[Bb]alance)""")
    private val CLOSING = Regex("""(?:neuer|Neuer|new|New|closing|Closing)[^\n]{0,30}?(?:Kontostand|[Ss]aldo|[Bb]alance)""")

    /** `erstellt am 30.06.2020`, `Datum: 01.06.2019` — anything that dates the document itself. */
    private val STATEMENT_DATE = Regex("""(?:erstellt am|Datum|Date|created|vom)\D{0,10}(\d{2}[.\-/]\d{2}[.\-/]\d{4})""")

    fun detect(lines: List<String>): Detection {
        val hints = readBalances(lines)
        val statementDate = readStatementDate(lines)
        val candidates = candidates(lines, statementDate)

        if (candidates.isEmpty()) return Detection(null, hints, candidates)

        // The correct profile is the one that reproduces the document's own stated net exactly. Each
        // candidate reads the balances in its own number format, since that's part of what's being tested.
        val proven = candidates.firstOrNull { profile ->
            val expected = hints.expectedNet(profile.numberFormat) ?: return@firstOrNull false
            GenericParser.netOf(lines, profile)?.let { GenericParser.matches(it, expected) } == true
        }
        if (proven != null) {
            val verified = proven.copy(source = StatementProfile.Source.RECONCILED)
            println("$LOG profile RECONCILED: $verified")
            return Detection(verified, hints, candidates)
        }

        // Nothing to verify against: prefer whichever profile reads the most rows, but say it's a guess.
        val best = candidates.maxByOrNull { GenericParser.parse(lines, it).transactions.size }
        return Detection(best?.copy(source = StatementProfile.Source.INFERRED), hints, candidates)
    }

    /** Every profile worth trying for this document: only formats actually present in the text. */
    fun candidates(lines: List<String>, statementDate: LocalDate?): List<StatementProfile> {
        val dateFormats = presentDateFormats(lines)
        val numberFormats = presentNumberFormats(lines)
        val signConventions = presentSignConventions(lines)

        return dateFormats.flatMap { date ->
            numberFormats.flatMap { number ->
                signConventions.map { sign ->
                    StatementProfile(date, number, sign, statementDate)
                }
            }
        }
    }

    /** Date formats that actually occur, most frequent first — a stray date elsewhere shouldn't win. */
    private fun presentDateFormats(lines: List<String>): List<Tokens.DateFormat> =
        Tokens.DateFormat.entries
            .map { format -> format to lines.count { format.find(it).isNotEmpty() } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }

    /**
     * Number format is decidable from the text alone when a thousands separator is present: `1,091.24`
     * can only be Anglo and `1.315,60` can only be German. Short amounts like `2,46` are ambiguous, so
     * both formats stay in the running and reconciliation settles it.
     */
    private fun presentNumberFormats(lines: List<String>): List<NumberFormat> {
        val text = lines.joinToString("\n")
        val anglo = Regex("""\d{1,3},\d{3}\.\d{2}""").containsMatchIn(text)
        val german = Regex("""\d{1,3}\.\d{3},\d{2}""").containsMatchIn(text)
        return when {
            anglo && !german -> listOf(NumberFormat.ANGLO)
            german && !anglo -> listOf(NumberFormat.GERMAN)
            // Ambiguous or mixed — let reconciliation decide.
            else -> listOf(NumberFormat.GERMAN, NumberFormat.ANGLO)
        }
    }

    /**
     * Which sign conventions this document could plausibly use.
     *
     * The dangerous case is a statement that marks nothing: no `-` anywhere means unmarked amounts are
     * spending, which reads as income to anything that keys on a minus sign.
     */
    private fun presentSignConventions(lines: List<String>): List<SignConvention> {
        // Read markers only from amounts on transaction rows. Scanning the whole document instead finds a
        // "+" in a phone number ("+4915…") and concludes the statement might mark income with a plus —
        // which made a perfectly decidable statement look ambiguous and sent it off for three minutes of
        // inference it never needed.
        val rowAmounts = lines.filter { Tokens.looksLikeRow(it) }
            .flatMap { Tokens.amounts(it) }
            .map { it.text }

        val sollHaben = rowAmounts.count { Regex("""\s[SH]$""").containsMatchIn(it) } >= 3
        val hasMinus = rowAmounts.any { it.startsWith("-") }
        val hasPlus = rowAmounts.any { it.startsWith("+") }

        return buildList {
            if (sollHaben) add(SignConvention.SOLL_HABEN)
            if (hasMinus) add(SignConvention.EXPLICIT_MINUS)
            if (hasPlus || !hasMinus) add(SignConvention.PLUS_MEANS_INCOME)
            if (isEmpty()) addAll(SignConvention.entries)
        }
    }

    private fun readStatementDate(lines: List<String>): LocalDate? =
        lines.firstNotNullOfOrNull { line ->
            STATEMENT_DATE.find(line)?.groupValues?.get(1)?.let { raw ->
                Tokens.dates(raw).firstNotNullOfOrNull { it.format.parse(it.text, null) }
            }
        }

    /** Grabs the opening/closing balance tokens as raw text — see [BalanceHints] for why unparsed. */
    private fun readBalances(lines: List<String>): BalanceHints =
        BalanceHints(
            openingText = balanceTextFor(lines, OPENING),
            closingText = balanceTextFor(lines, CLOSING),
        )

    /**
     * The balance is the first amount *after* the marker, not the last one on the line. Statement headers
     * put several figures side by side — Fidor prints "Alter Kontostand: 27,66 €   Zinssatz p.a.: 0,00 %",
     * where taking the last amount reads the interest rate as the balance and reconciliation never fires.
     */
    private fun balanceTextFor(lines: List<String>, marker: Regex): String? =
        lines.firstNotNullOfOrNull { line ->
            val match = marker.find(line) ?: return@firstNotNullOfOrNull null
            Tokens.amounts(line).firstOrNull { it.start >= match.range.last }?.text
        }
}
