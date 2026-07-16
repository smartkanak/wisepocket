package date.oxi.wisepocket

import date.oxi.wisepocket.statement.Amounts
import date.oxi.wisepocket.statement.GenericParser
import date.oxi.wisepocket.statement.ProfileDetector
import date.oxi.wisepocket.statement.StatementProfile
import date.oxi.wisepocket.statement.Tokens
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The four sampled banks are used here as *evidence that the generic pipeline generalises* — there is no
 * bank-specific code to test. Each fixture mirrors the layout of a real statement (with invented account
 * data; the real PDFs are personal and gitignored), and each is read by the same detector.
 *
 * A fifth, invented bank is included on purpose: if the pipeline only works on layouts I designed it
 * against, it isn't general, and that test is what would catch it.
 */
class ProfileDetectorTest {

    @Test
    fun detectsExplicitMinusWithGermanNumbers() {
        // Fidor-shaped: -0,09 style, and the balances let it self-verify.
        val lines = """
            Alter Kontostand:             27,66 €
            Neuer Kontostand:             21,68 €
            27.05.2019   25.05.2019   Mastercard Umsatz                  -5,90
            29.05.2019   29.05.2019   Gutschrift                          0,01
            30.05.2019   30.05.2019   Fremdw.gebuehr                     -0,09
        """.trimIndent().lines()

        val profile = ProfileDetector.detect(lines).profile!!
        assertEquals(Amounts.SignConvention.EXPLICIT_MINUS, profile.signConvention)
        assertEquals(Amounts.NumberFormat.GERMAN, profile.numberFormat)
        assertEquals(StatementProfile.Source.RECONCILED, profile.source)
    }

    @Test
    fun detectsUnsignedSpendingWhereOnlyIncomeIsMarked() {
        // Klarna-shaped: no minus signs anywhere. Anything keying on '-' reads these as income.
        val lines = """
            Alter Kontostand vom 01.05.2023          2.075,37 €
            Neuer Kontostand vom 31.05.2023          2.524,68 €
            31.05.2023    Norma                          2,46 €
            24.05.2023    Manar Hussein                +25,00 €
            23.05.2023    Adesso mobile              +1.315,60 €
            22.05.2023    Telekom Deutschland GmbH      34,95 €
            21.05.2023    Vodafone GmbH                853,88 €
        """.trimIndent().lines()

        val profile = ProfileDetector.detect(lines).profile!!
        assertEquals(Amounts.SignConvention.PLUS_MEANS_INCOME, profile.signConvention)
        assertEquals(StatementProfile.Source.RECONCILED, profile.source)

        // Verified by the statement's own arithmetic: 2075,37 + 449,31 = 2524,68.
        val result = GenericParser.parse(lines, profile)
        assertTrue(GenericParser.matches(result.net, 449.31), "net was ${result.net}")
    }

    @Test
    fun detectsSollHabenSuffixes() {
        // MVB-shaped: direction carried by a trailing S/H, no signs at all.
        val lines = """
            alter Kontostand vom 29.05.2020        1.040,98 H
            neuer Kontostand vom 30.06.2020        1.262,71 H
            erstellt am 30.06.2020
            02.06. 02.06. SEPA-BASISLASTSCHR.        19,34 S
            03.06. 03.06. Sepa Gutschrift            74,16 H
            04.06. 04.06. DAUERAUFTRAG              400,00 S
            05.06. 05.06. LOHN                      566,91 H
        """.trimIndent().lines()

        val profile = ProfileDetector.detect(lines).profile!!
        assertEquals(Amounts.SignConvention.SOLL_HABEN, profile.signConvention)
        assertEquals(StatementProfile.Source.RECONCILED, profile.source)
    }

    @Test
    fun detectsAngloNumbersOnAGermanStatement() {
        val lines = """
            TRANSAKTIONEN
            25-02-2022    Geldtransfer   €1,091.24     LOHN / GEHALT
            24-02-2022    Geldtransfer   €-490.90      Donnerstag Abend
            22-02-2022    Direct Debit   €-5.27        Klarna Card
        """.trimIndent().lines()

        val profile = ProfileDetector.detect(lines).profile!!
        assertEquals(Amounts.NumberFormat.ANGLO, profile.numberFormat)
        assertEquals(Tokens.DateFormat.DMY_DASH, profile.dateFormat)

        val result = GenericParser.parse(lines, profile)
        assertEquals(1091.24, result.transactions.first().transaction.amount)
    }

    @Test
    fun resolvesYearlessDatesAcrossNewYear() {
        // A January statement legitimately carries December rows from the year before.
        val lines = """
            alter Kontostand vom 30.12.2020        1.518,08 H
            neuer Kontostand vom 29.01.2021          694,63 H
            erstellt am 29.01.2021
            30.12. 30.12. SEPA-UEBERWEISUNG         823,45 S
        """.trimIndent().lines()

        val profile = ProfileDetector.detect(lines).profile!!
        val result = GenericParser.parse(lines, profile)
        assertEquals(LocalDate(2020, 12, 30), result.transactions.first().transaction.date)
    }

    @Test
    fun readsABankItHasNeverSeen() {
        // Invented layout: ISO dates, Anglo numbers, explicit minus, GBP-ish wording. No code knows it.
        val lines = """
            Opening balance                         1,000.00
            Closing balance                           847.50
            2026-03-01   TESCO STORES 3411              -42.50
            2026-03-04   SALARY ACME LTD               +200.00
            2026-03-09   TFL TRAVEL CHARGE             -310.00
        """.trimIndent().lines()

        val profile = ProfileDetector.detect(lines).profile!!
        assertEquals(Tokens.DateFormat.ISO, profile.dateFormat)
        assertEquals(StatementProfile.Source.RECONCILED, profile.source)

        val result = GenericParser.parse(lines, profile)
        assertEquals(3, result.transactions.size)
        assertTrue(GenericParser.matches(result.net, -152.50), "net was ${result.net}")
    }

    @Test
    fun rowsNeedReviewWhenNothingCouldVerifyTheProfile() {
        val lines = """
            25-02-2022    Geldtransfer   €1,091.24     LOHN
            24-02-2022    Geldtransfer   €-490.90      Transfer
        """.trimIndent().lines()

        val profile = ProfileDetector.detect(lines).profile!!
        assertEquals(StatementProfile.Source.INFERRED, profile.source)
        assertTrue(GenericParser.parse(lines, profile).needsReviewCount > 0)
    }

    @Test
    fun conventionsStatedByTheTextAreNotAmbiguous() {
        // Regression: this statement states no balance, so it can't reconcile — but its conventions are
        // still decidable (explicit minus signs, and "1,091.24" can only be Anglo). Marking it ambiguous
        // sent it to the LLM, which contradicted the evidence and inflated spending a hundredfold.
        val lines = """
            25-02-2022    Geldtransfer   €1,091.24     LOHN
            24-02-2022    Geldtransfer   €-490.90      Transfer
            22-02-2022    Direct Debit   €-5.27        Card
        """.trimIndent().lines()

        val detection = ProfileDetector.detect(lines)
        assertFalse(detection.isAmbiguous, "candidates: ${detection.candidates}")
        assertEquals(Amounts.SignConvention.EXPLICIT_MINUS, detection.profile!!.signConvention)
        assertEquals(Amounts.NumberFormat.ANGLO, detection.profile!!.numberFormat)
    }

    @Test
    fun aStatementWithNoBalanceAndNoSignMarkersIsAmbiguous() {
        // The one case worth asking the model about: nothing to reconcile against, and no marker to read.
        // "Unmarked means spending" is a guess that some bank will break.
        val lines = """
            31.05.2023    Norma          2,46 €
            22.05.2023    Telekom       34,95 €
        """.trimIndent().lines()

        assertTrue(ProfileDetector.detect(lines).isAmbiguous)
    }

    @Test
    fun ignoresLinesThatAreNotTransactions() {
        val lines = listOf(
            "IBAN: DE93 7002 2200 0075 5065 70",
            "Kontoauszug Nr. 5 2019",
            "Seite 1 / 5",
            "27.05.2019   Mastercard Umsatz     -5,90",
        )
        // A row needs a date AND an amount; IBANs and page numbers have neither.
        assertEquals(1, lines.count { Tokens.looksLikeRow(it) })
    }
}
