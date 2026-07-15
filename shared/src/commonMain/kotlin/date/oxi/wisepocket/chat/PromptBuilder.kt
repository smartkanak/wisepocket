package date.oxi.wisepocket.chat

import date.oxi.wisepocket.model.Transaction
import kotlin.math.abs
import kotlin.math.roundToLong

/** A single turn in the chat. */
data class ChatMessage(
    val role: Role,
    val text: String,
) {
    enum class Role { USER, ASSISTANT }
}

/**
 * Builds the grounding pieces handed to the on-device model. The engine (Llamatik) applies the
 * model's chat template, so we supply the system instructions and a context block separately from
 * the raw user question. Pure and unit-testable.
 */
object PromptBuilder {

    const val SYSTEM = "You are WisePocket, a friendly on-device personal-finance assistant. " +
        "Answer ONLY from the context. Amounts are in the stated currency; negative means money spent, positive means received. " +
        "The figures are pre-computed: categories are ranked largest-first, totals include net savings, and the most recent " +
        "purchase is given explicitly. Use those numbers directly — never re-add or re-sort them yourself. " +
        "Be concise, playful but professional. If the data doesn't cover the question, say so."

    /** The grounding context: a compact transaction summary plus recent conversation history. */
    fun buildContext(
        transactions: List<Transaction>,
        history: List<ChatMessage>,
    ): String = buildString {
        appendLine("TRANSACTIONS:")
        append(renderTransactions(transactions))
        if (history.isNotEmpty()) {
            appendLine()
            appendLine()
            appendLine("CONVERSATION SO FAR:")
            history.forEach { msg ->
                val who = if (msg.role == ChatMessage.Role.USER) "User" else "WisePocket"
                appendLine("$who: ${msg.text}")
            }
        }
    }

    /**
     * **Precomputed** per-category spend subtotals + overall totals, then a compact list of the most
     * recent transactions. Small on-device LLMs are unreliable at arithmetic *and* slow at processing
     * long prompts, so we sum in code and keep the token count low (only [RECENT_LIMIT] raw lines).
     */
    fun renderTransactions(transactions: List<Transaction>): String = buildString {
        val currency = transactions.firstOrNull()?.currency ?: ""

        // Categories ranked largest-first and numbered, so "biggest"/"top N" become a lookup.
        appendLine("SPENDING BY CATEGORY (largest first):")
        transactions
            .filter { it.amount < 0 }
            .groupBy { it.category ?: "Uncategorized" }
            .mapValues { (_, txs) -> txs.sumOf { -it.amount } }
            .entries.sortedByDescending { it.value }
            .forEachIndexed { i, (cat, sum) -> appendLine("${i + 1}. $cat: ${money(sum)} $currency") }

        val spent = transactions.filter { it.amount < 0 }.sumOf { -it.amount }
        val income = transactions.filter { it.amount > 0 }.sumOf { it.amount }
        val net = income - spent
        val netLabel = if (net >= 0) "net saved ${money(net)}" else "net overspent ${money(-net)}"
        appendLine("TOTALS: spent ${money(spent)} $currency, income ${money(income)} $currency, $netLabel $currency")

        // Precompute the most recent *purchase* (exclude income) so the model doesn't grab the wrong line.
        transactions.filter { it.amount < 0 }.maxByOrNull { it.date }?.let { p ->
            appendLine()
            appendLine("MOST RECENT PURCHASE: ${p.merchant}, ${money(p.amount)} ${p.currency}, ${p.date} (${p.category ?: "Uncategorized"})")
        }

        val recent = transactions.sortedByDescending { it.date }.take(RECENT_LIMIT)
        if (recent.isNotEmpty()) {
            appendLine()
            appendLine("RECENT TRANSACTIONS (newest first):")
            recent.forEach { t ->
                append("- ${t.date} | ${t.merchant} | ${money(t.amount)} ${t.currency} | ${t.category ?: "Uncategorized"}\n")
            }
        }
    }

    private const val RECENT_LIMIT = 8

    private fun money(value: Double): String {
        val cents = (abs(value) * 100).roundToLong()
        val sign = if (value < 0) "-" else ""
        return "$sign${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
    }
}
