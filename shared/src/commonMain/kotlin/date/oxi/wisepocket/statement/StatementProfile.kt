package date.oxi.wisepocket.statement

import date.oxi.wisepocket.statement.Amounts.NumberFormat
import date.oxi.wisepocket.statement.Amounts.SignConvention
import kotlinx.datetime.LocalDate

/**
 * How one statement encodes its rows. This is the whole point of the design: instead of a parser per
 * bank, we work out a document's conventions once and then read every row deterministically.
 *
 * The four sampled banks needed no bank-specific code — only different values of these fields:
 * ```
 * Fidor   DMY_DOT   GERMAN  EXPLICIT_MINUS      -0,09
 * Klarna  DMY_DOT   GERMAN  PLUS_MEANS_INCOME   2,46 € / +25,00 €
 * MVB     DMY_NO_YEAR GERMAN SOLL_HABEN         19,34 S / 74,16 H
 * ```
 * A fifth bank we've never seen is just another combination.
 */
data class StatementProfile(
    val dateFormat: Tokens.DateFormat,
    val numberFormat: NumberFormat,
    val signConvention: SignConvention,
    /**
     * The statement's own date, used to resolve year-less rows. Null when the document doesn't say
     * (and then [Tokens.DateFormat.DMY_NO_YEAR] rows can't be dated at all).
     */
    val statementDate: LocalDate? = null,
    /** How the profile was arrived at — surfaced to the user, since it determines how much to trust the import. */
    val source: Source = Source.INFERRED,
) {
    enum class Source {
        /** Reproduces the statement's own stated balances to the cent. Effectively proven. */
        RECONCILED,

        /** The on-device LLM read a sample of rows and named the conventions. */
        LLM,

        /** Chosen by heuristics because nothing could verify it. Treat every row as needing review. */
        INFERRED,
    }

    val isVerified: Boolean get() = source == Source.RECONCILED
}
