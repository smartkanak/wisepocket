package date.oxi.wisepocket

import date.oxi.wisepocket.chat.ChatMessage
import date.oxi.wisepocket.chat.PromptBuilder
import date.oxi.wisepocket.model.Transaction
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertContains

class PromptBuilderTest {

    @Test
    fun contextHasCategoryBreakdownAndRecentTransactions() {
        val context = PromptBuilder.buildContext(
            transactions = sample,
            history = emptyList(),
        )
        // Precomputed category grounding + a recent merchant (Lidl is the latest transaction).
        assertContains(context, "SPENDING BY CATEGORY")
        assertContains(context, "Groceries")
        assertContains(context, "RECENT TRANSACTIONS")
        assertContains(context, "Lidl")
    }

    @Test
    fun totalsSummarizeSpendAndIncomeAndGroceries() {
        val summary = PromptBuilder.renderTransactions(sample)
        // Income entries (salary + freelance) sum to 3680.00.
        assertContains(summary, "income 3680.00")
        assertContains(summary, "TOTALS: spent")
        // Groceries subtotal is precomputed (54.30 + 37.81 + 41.20 + 48.75 = 182.06).
        assertContains(summary, "Groceries: 182.06")
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

    /** A month of invented transactions — the fixture this test's assertions are written against. */
    private val sample: List<Transaction> = listOf(
        Transaction("t1", LocalDate(2026, 6, 1), "Salary — Acme GmbH", 3200.00, category = "Income"),
        Transaction("t2", LocalDate(2026, 6, 2), "REWE Supermarket", -54.30, category = "Groceries"),
        Transaction("t3", LocalDate(2026, 6, 3), "Deutsche Bahn", -19.90, category = "Transport"),
        Transaction("t4", LocalDate(2026, 6, 5), "Netflix", -13.99, category = "Subscriptions"),
        Transaction("t5", LocalDate(2026, 6, 7), "Aldi", -37.81, category = "Groceries"),
        Transaction("t6", LocalDate(2026, 6, 9), "Shell Fuel", -62.40, category = "Transport"),
        Transaction("t7", LocalDate(2026, 6, 12), "Rent — Hausverwaltung", -1150.00, category = "Housing"),
        Transaction("t8", LocalDate(2026, 6, 14), "Amazon", -89.99, category = "Shopping"),
        Transaction("t9", LocalDate(2026, 6, 15), "Edeka", -41.20, category = "Groceries"),
        Transaction("t10", LocalDate(2026, 6, 18), "Spotify", -10.99, category = "Subscriptions"),
        Transaction("t11", LocalDate(2026, 6, 20), "Cafe Central", -8.50, category = "Dining"),
        Transaction("t12", LocalDate(2026, 6, 22), "Vodafone", -29.99, category = "Utilities"),
        Transaction("t13", LocalDate(2026, 6, 25), "Freelance invoice", 480.00, category = "Income"),
        Transaction("t14", LocalDate(2026, 6, 27), "IKEA", -156.00, category = "Shopping"),
        Transaction("t15", LocalDate(2026, 6, 29), "Lidl", -48.75, category = "Groceries"),
    )
}
