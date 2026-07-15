package date.oxi.wisepocket

import date.oxi.wisepocket.chat.ChatMessage
import date.oxi.wisepocket.chat.PromptBuilder
import date.oxi.wisepocket.data.MockTransactions
import kotlin.test.Test
import kotlin.test.assertContains

class PromptBuilderTest {

    @Test
    fun contextHasCategoryBreakdownAndRecentTransactions() {
        val context = PromptBuilder.buildContext(
            transactions = MockTransactions.sample,
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
        val summary = PromptBuilder.renderTransactions(MockTransactions.sample)
        // Income entries (salary + freelance) sum to 3680.00.
        assertContains(summary, "income 3680.00")
        assertContains(summary, "TOTALS: spent")
        // Groceries subtotal is precomputed (54.30 + 37.81 + 41.20 + 48.75 = 182.06).
        assertContains(summary, "Groceries: 182.06")
    }

    @Test
    fun historyIsIncludedWhenPresent() {
        val context = PromptBuilder.buildContext(
            transactions = MockTransactions.sample,
            history = listOf(
                ChatMessage(ChatMessage.Role.USER, "Hi"),
                ChatMessage(ChatMessage.Role.ASSISTANT, "Hello! How can I help with your finances?"),
            ),
        )
        assertContains(context, "CONVERSATION SO FAR:")
        assertContains(context, "How can I help with your finances?")
    }
}
