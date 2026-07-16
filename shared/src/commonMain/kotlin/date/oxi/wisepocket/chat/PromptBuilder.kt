package date.oxi.wisepocket.chat

import date.oxi.wisepocket.insights.Insights
import date.oxi.wisepocket.model.Transaction
import date.oxi.wisepocket.model.categoryOrNull
import date.oxi.wisepocket.model.formatMoney
import kotlin.math.roundToInt

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
        "Answer ONLY from the context. Amounts are in the stated currency; " +
        "negative means money spent, positive means received. " +
        "Every figure in the context is already computed — categories, monthly and per-merchant totals " +
        "are ranked largest-first, and the biggest and most recent purchases are given explicitly. " +
        "Use those numbers directly — never re-add or re-sort them yourself. " +
        "Some spending may be listed as uncategorised; if it affects the answer, say so rather than " +
        "assuming which category it belongs to. " +
        "Be concise, playful but professional. If the data doesn't cover the question, say so."

    /** The grounding context: a compact transaction summary plus recent conversation history. */
    fun buildContext(
        transactions: List<Transaction>,
        history: List<ChatMessage>,
    ): String = buildString {
        append(renderTransactions(transactions))
        if (history.isNotEmpty()) {
            appendLine()
            appendLine("CONVERSATION SO FAR:")
            history.forEach { msg ->
                val who = if (msg.role == ChatMessage.Role.USER) "User" else "WisePocket"
                appendLine("$who: ${msg.text}")
            }
        }
    }

    /**
     * Renders the **precomputed** [Insights] rather than the transactions themselves.
     *
     * This is what makes the chat able to answer at all. Dumping raw rows meant the model saw the newest
     * handful and never the other fifty, so any question about a total was guesswork over a sample. Small
     * on-device models are also unreliable at arithmetic and slow at long prompts — both point the same
     * way: sum in code, hand over finished facts, keep it short. A few recent rows still come along, for
     * the questions aggregates genuinely can't answer ("what did I buy last week?").
     */
    fun renderTransactions(transactions: List<Transaction>): String = buildString {
        val insights = Insights.from(transactions)
        if (insights.isEmpty) {
            appendLine("TRANSACTIONS: none on file.")
            return@buildString
        }
        val currency = insights.currency

        appendLine(
            "PERIOD: ${insights.from} to ${insights.to} " +
                "(${insights.transactionCount} transactions, $currency)",
        )
        val net = insights.net
        val netLabel = if (net >= 0) "net saved ${money(net)}" else "net overspent ${money(-net)}"
        appendLine(
            "TOTALS: spent ${money(insights.spent)}, income ${money(insights.income)}, $netLabel ($currency)",
        )

        appendLine()
        appendLine("SPENDING BY CATEGORY (largest first):")
        insights.byCategory.forEachIndexed { i, slice ->
            appendLine(
                "${i + 1}. ${slice.category.label}: ${money(slice.spent)} " +
                    "(${percent(slice.share)}, ${slice.count} transactions)",
            )
        }
        // Stated, not hidden: the model must be able to say "some spending isn't categorised" instead of
        // quietly answering as if the labelled part were the whole picture.
        if (insights.uncategorizedCount > 0) {
            appendLine(
                "Uncategorised: ${money(insights.uncategorizedSpent)} " +
                    "(${insights.uncategorizedCount} transactions)",
            )
        }

        if (insights.byMonth.size > 1) {
            appendLine()
            appendLine("BY MONTH:")
            insights.byMonth.forEach { m ->
                appendLine("- ${m.month}: spent ${money(m.spent)}, income ${money(m.income)}")
            }
        }

        appendLine()
        appendLine("TOP MERCHANTS BY SPEND:")
        insights.topMerchants.forEachIndexed { i, m ->
            appendLine("${i + 1}. ${m.merchant}: ${money(m.spent)} (${m.count} transactions)")
        }

        appendLine()
        insights.biggestPurchase?.let { appendLine("BIGGEST PURCHASE: ${describe(it)}") }
        insights.latestPurchase?.let { appendLine("MOST RECENT PURCHASE: ${describe(it)}") }

        val recent = transactions.sortedByDescending { it.date }.take(RECENT_LIMIT)
        appendLine()
        appendLine("RECENT TRANSACTIONS (newest first):")
        recent.forEach { appendLine("- ${describe(it)}") }
    }

    private const val RECENT_LIMIT = 8

    private const val PERCENT = 100

    private fun describe(t: Transaction): String =
        "${t.date} | ${t.merchant} | ${money(t.amount)} ${t.currency} | ${t.categoryLabel}"

    /** One spelling of "no category", so the summary and the recent lines can't disagree. */
    private val Transaction.categoryLabel: String get() = categoryOrNull?.label ?: "Uncategorised"

    private fun percent(share: Float): String = "${(share * PERCENT).roundToInt()}%"

    private fun money(value: Double): String = formatMoney(value)
}
