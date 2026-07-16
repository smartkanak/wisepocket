package date.oxi.wisepocket

import date.oxi.wisepocket.chat.ChatMessage
import date.oxi.wisepocket.chat.PromptBuilder
import date.oxi.wisepocket.model.Category
import date.oxi.wisepocket.model.Transaction
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PromptBuilderTest {

    @Test
    fun contextCarriesTheWholeStatementNotJustTheRecentRows() {
        val context = PromptBuilder.buildContext(transactions = sample, history = emptyList())

        assertContains(context, "SPENDING BY CATEGORY")
        assertContains(context, "TOP MERCHANTS BY SPEND")
        assertContains(context, "PERIOD: 2026-06-01 to 2026-06-29")
        assertContains(context, "15 transactions")
        // Rent is the biggest single line but is not among the newest rows. Under the old raw-row dump the
        // model never saw it; the whole point of grounding on aggregates is that it does now.
        assertContains(context, "Rent — Hausverwaltung")
    }

    @Test
    fun totalsAndCategorySubtotalsArePrecomputed() {
        val summary = PromptBuilder.renderTransactions(sample)

        // Income entries (salary + freelance) sum to 3680.00.
        assertContains(summary, "income 3680.00")
        // Groceries subtotal: 54.30 + 37.81 + 41.20 + 48.75 = 182.06.
        assertContains(summary, "Groceries: 182.06")
        assertContains(summary, "net saved")
    }

    @Test
    fun uncategorisedSpendingIsStatedRatherThanDropped() {
        val withUnknown = sample + Transaction("t16", LocalDate(2026, 6, 30), "SEPA 4711", -75.00)
        val summary = PromptBuilder.renderTransactions(withUnknown)

        // Silence here would let the model answer as if the labelled part were the whole picture.
        assertContains(summary, "Uncategorised: 75.00")
    }

    @Test
    fun emptyTransactionsSayNothingIsOnFile() {
        val summary = PromptBuilder.renderTransactions(emptyList())

        assertContains(summary, "none on file")
        assertFalse(summary.contains("TOTALS"), "an empty statement must not report totals")
    }

    @Test
    fun aSingleMonthStatementSkipsTheMonthlyBreakdown() {
        // One month of data makes "BY MONTH" a restatement of TOTALS — and prefill is what costs time
        // on-device, so a line that adds nothing shouldn't be paid for.
        assertFalse(PromptBuilder.renderTransactions(sample).contains("BY MONTH"))

        val july = Transaction("t16", LocalDate(2026, 7, 2), "REWE", -20.00, category = Category.GROCERIES.name)
        assertTrue(PromptBuilder.renderTransactions(sample + july).contains("BY MONTH"))
    }

    @Test
    fun historyIsIncludedWhenPresent() {
        val context = PromptBuilder.buildContext(
            transactions = sample,
            history = listOf(
                ChatMessage(ChatMessage.Role.USER, "Hi"),
                ChatMessage(ChatMessage.Role.ASSISTANT, "Hello! How can I help with your finances?"),
            ),
        )
        assertContains(context, "CONVERSATION SO FAR:")
        assertContains(context, "How can I help with your finances?")
    }

    /**
     * A month of invented transactions — the fixture this test's assertions are written against.
     *
     * Categories are [Category] names, not prose. The earlier fixture used "Dining", "Utilities" and
     * "Income", which is exactly the drift the closed set exists to stop: three labels that no longer
     * add up with the ones the model would produce.
     */
    private val sample: List<Transaction> = listOf(
        Transaction("t1", LocalDate(2026, 6, 1), "Salary — Acme GmbH", 3200.00),
        Transaction("t2", LocalDate(2026, 6, 2), "REWE Supermarket", -54.30, category = Category.GROCERIES.name),
        Transaction("t3", LocalDate(2026, 6, 3), "Deutsche Bahn", -19.90, category = Category.TRANSPORT.name),
        Transaction("t4", LocalDate(2026, 6, 5), "Netflix", -13.99, category = Category.SUBSCRIPTIONS.name),
        Transaction("t5", LocalDate(2026, 6, 7), "Aldi", -37.81, category = Category.GROCERIES.name),
        Transaction("t6", LocalDate(2026, 6, 9), "Shell Fuel", -62.40, category = Category.TRANSPORT.name),
        Transaction(
            "t7", LocalDate(2026, 6, 12), "Rent — Hausverwaltung", -1150.00,
            category = Category.HOUSING.name,
        ),
        Transaction("t8", LocalDate(2026, 6, 14), "Amazon", -89.99, category = Category.SHOPPING.name),
        Transaction("t9", LocalDate(2026, 6, 15), "Edeka", -41.20, category = Category.GROCERIES.name),
        Transaction("t10", LocalDate(2026, 6, 18), "Spotify", -10.99, category = Category.SUBSCRIPTIONS.name),
        Transaction("t11", LocalDate(2026, 6, 20), "Cafe Central", -8.50, category = Category.RESTAURANTS.name),
        Transaction("t12", LocalDate(2026, 6, 22), "Vodafone", -29.99, category = Category.HOUSING.name),
        Transaction("t13", LocalDate(2026, 6, 25), "Freelance invoice", 480.00),
        Transaction("t14", LocalDate(2026, 6, 27), "IKEA", -156.00, category = Category.SHOPPING.name),
        Transaction("t15", LocalDate(2026, 6, 29), "Lidl", -48.75, category = Category.GROCERIES.name),
    )
}
