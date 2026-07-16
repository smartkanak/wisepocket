package date.oxi.wisepocket.statement

/**
 * Amount parsing for the formats seen across real statements. Getting this wrong is silent and
 * expensive — a misread sign turns an expense into income and corrupts every total downstream — so each
 * convention is handled explicitly rather than guessed at.
 *
 * Observed in the sample statements:
 * - `-0,09`      German digits, explicit minus.
 * - `€-490.90`   **Anglo** digits (dot decimal, comma thousands) on a German statement.
 * - `2,46 €` / `+25,00 €`  no minus at all — bare means *outgoing*, `+` means incoming.
 * - `19,34 S` / `74,16 H`  Soll (debit) / Haben (credit) suffix, never a sign.
 */
object Amounts {

    /** How a statement encodes the direction of money. */
    enum class SignConvention {
        /** A leading `-` marks spending; anything else is income. */
        EXPLICIT_MINUS,

        /** A leading `+` marks income; an unmarked amount is spending (Klarna). */
        PLUS_MEANS_INCOME,

        /** Trailing `S` = Soll (spending), `H` = Haben (income) (MVB and most German bank house formats). */
        SOLL_HABEN,
    }

    /** How digits are grouped and the decimal separator written. */
    enum class NumberFormat {
        /** `1.315,60` — dot groups thousands, comma is the decimal separator. */
        GERMAN,

        /** `1,091.24` — comma groups thousands, dot is the decimal separator. */
        ANGLO,
    }

    /**
     * Parses [raw] into a signed major-unit amount: negative for spending, positive for income —
     * matching [date.oxi.wisepocket.model.Transaction.amount].
     *
     * Returns null when [raw] isn't an amount, so callers can use this to *test* candidate text.
     */
    fun parse(raw: String, format: NumberFormat, convention: SignConvention): Double? {
        val text = raw.trim()
        if (text.isEmpty()) return null

        val sollHaben = if (convention == SignConvention.SOLL_HABEN) {
            Regex("""\s([SH])$""").find(text)?.groupValues?.get(1) ?: return null
        } else {
            null
        }

        val digits = Regex("""[-+]?[\d.,]*\d""").find(text)?.value ?: return null
        val magnitude = parseMagnitude(digits, format) ?: return null

        val negative = when (convention) {
            SignConvention.EXPLICIT_MINUS -> digits.startsWith("-")
            SignConvention.PLUS_MEANS_INCOME -> !digits.startsWith("+")
            SignConvention.SOLL_HABEN -> sollHaben == "S"
        }
        return if (negative) -magnitude else magnitude
    }

    /** Parses the unsigned numeric part, stripping the thousands separator for [format]. */
    private fun parseMagnitude(digits: String, format: NumberFormat): Double? {
        val bare = digits.trimStart('-', '+')
        val normalised = when (format) {
            NumberFormat.GERMAN -> bare.replace(".", "").replace(',', '.')
            NumberFormat.ANGLO -> bare.replace(",", "")
        }
        return normalised.toDoubleOrNull()
    }
}
