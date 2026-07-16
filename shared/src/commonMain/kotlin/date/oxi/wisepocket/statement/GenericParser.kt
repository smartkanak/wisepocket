package date.oxi.wisepocket.statement

import date.oxi.wisepocket.model.Transaction
import kotlin.math.abs

/** Where a parsed transaction came from, so the review UI can flag what deserves a second look. */
enum class ParseConfidence {
    /** Read with a profile that reconciles against the statement's own balances. */
    HIGH,

    /** Parsed, but the profile is unverified or the row was odd. */
    NEEDS_REVIEW,
}

data class ParsedTransaction(
    val transaction: Transaction,
    val confidence: ParseConfidence = ParseConfidence.HIGH,
    /** The statement line this came from — shown in review so the user can check it against the source. */
    val sourceLine: String = "",
)

data class ParseResult(
    val profile: StatementProfile,
    val transactions: List<ParsedTransaction>,
    /** Candidate rows the profile couldn't read. Surfaced rather than silently dropped. */
    val skippedLines: List<String> = emptyList(),
) {
    val needsReviewCount: Int get() = transactions.count { it.confidence == ParseConfidence.NEEDS_REVIEW }
    val net: Double get() = transactions.sumOf { it.transaction.amount }
}

/**
 * Reads every candidate row using a [StatementProfile]. Bank-agnostic by construction — it knows only
 * what the profile tells it.
 *
 * Description handling is deliberately simple: whatever text is left on the row once the date and amount
 * are removed. Statements wrap descriptions across lines, so this misses some detail — but it never
 * mis-states a date or an amount, and the review screen exists for the text.
 */
object GenericParser {

    fun parse(lines: List<String>, profile: StatementProfile): ParseResult {
        val skipped = mutableListOf<String>()
        val transactions = mutableListOf<ParsedTransaction>()

        lines.forEachIndexed { index, line ->
            if (!Tokens.looksLikeRow(line)) return@forEachIndexed

            val parsed = parseRow(line, index, profile)
            if (parsed == null) skipped += line.trim() else transactions += parsed
        }

        return ParseResult(profile, transactions, skipped)
    }

    private fun parseRow(line: String, index: Int, profile: StatementProfile): ParsedTransaction? {
        val dateToken = Tokens.dates(line).firstOrNull { it.format == profile.dateFormat } ?: return null
        val date = profile.dateFormat.parse(dateToken.text, profile.statementDate) ?: return null

        val amountTokens = Tokens.amounts(line)
        // The first amount on the row is the transaction amount. A row that also shows a running balance
        // would need more than this — no sampled statement does, and guessing would be worse than failing.
        val amountToken = amountTokens.firstOrNull() ?: return null
        val amount = Amounts.parse(amountToken.text, profile.numberFormat, profile.signConvention)
            ?: return null

        val merchant = describe(line, dateToken, amountTokens)
        return ParsedTransaction(
            transaction = Transaction(
                id = "row-$index",
                date = date,
                merchant = merchant,
                amount = amount,
            ),
            // An unverified profile means every amount is a guess about sign and scale — say so rather
            // than presenting it as fact.
            confidence = if (profile.isVerified) ParseConfidence.HIGH else ParseConfidence.NEEDS_REVIEW,
            sourceLine = line.trim(),
        )
    }

    /** The row text minus its date and amounts — a first guess at the description. */
    private fun describe(line: String, date: Tokens.DateToken, amounts: List<Tokens.AmountToken>): String {
        var text = line.replace(date.text, " ")
        amounts.forEach { text = text.replace(it.text, " ") }
        return text.replace(Regex("""\s{2,}"""), " ").trim()
    }

    /**
     * Sums the rows a profile produces. Used to test a profile against the statement's stated balances —
     * see [ProfileDetector].
     */
    fun netOf(lines: List<String>, profile: StatementProfile): Double? {
        val result = parse(lines, profile)
        if (result.transactions.isEmpty()) return null
        return result.net
    }

    /** Cent-level comparison — these are money sums built from doubles, so exact equality won't do. */
    fun matches(a: Double, b: Double): Boolean = abs(a - b) < 0.005
}
