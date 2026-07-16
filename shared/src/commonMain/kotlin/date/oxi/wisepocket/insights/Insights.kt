package date.oxi.wisepocket.insights

import date.oxi.wisepocket.model.Category
import date.oxi.wisepocket.model.Transaction
import date.oxi.wisepocket.model.categoryOrNull
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.yearMonth

/** Spending in one [Category], with its share of the total so a screen doesn't have to divide again. */
data class CategorySlice(
    val category: Category,
    val spent: Double,
    val count: Int,
    val share: Float,
)

/** One calendar month of activity. */
data class MonthSlice(
    val month: YearMonth,
    val spent: Double,
    val income: Double,
    val count: Int,
)

/** Everything spent at one merchant. */
data class MerchantSlice(
    val merchant: String,
    val spent: Double,
    val count: Int,
)

/**
 * Every number the app states about a set of transactions, computed once, in code.
 *
 * This is the single source both the Wrapped screens and the chat prompt read from, and that's the point
 * on two counts. A "Wrapped" is aggregates — biggest category, busiest month, most-visited merchant — so
 * the screens are a rendering of this and nothing more. And the chat's job is to *talk about* these
 * figures, not to derive them: a 1.5B model is unreliable at arithmetic and slow at long prompts, so
 * handing it a short list of finished facts is both more accurate and faster than a dump of raw rows.
 *
 * Pure and unit-tested. Anything the UI or the prompt wants to say belongs here first.
 */
data class Insights(
    val transactionCount: Int,
    val currency: String,
    val from: LocalDate?,
    val to: LocalDate?,
    val spent: Double,
    val income: Double,
    val byCategory: List<CategorySlice>,
    val byMonth: List<MonthSlice>,
    val topMerchants: List<MerchantSlice>,
    /**
     * The merchant visited most often — a different question from [topMerchants], which ranks by amount.
     * One car repair outspends a year of coffee without ever being the place you actually go.
     */
    val mostVisitedMerchant: MerchantSlice?,
    val biggestPurchase: Transaction?,
    val latestPurchase: Transaction?,
    /**
     * Spending rows carrying no category. Reported rather than hidden: categorisation runs on-device and
     * can come back empty (no model, a refused batch), and a Wrapped that quietly dropped a third of the
     * spending would be a lie told confidently.
     */
    val uncategorizedSpent: Double,
    val uncategorizedCount: Int,
) {
    val net: Double get() = income - spent
    val isEmpty: Boolean get() = transactionCount == 0

    /** The single biggest category, or null when nothing is categorised yet. */
    val topCategory: CategorySlice? get() = byCategory.firstOrNull()

    /** The month with the most spending — the "your March was expensive" line. */
    val biggestMonth: MonthSlice? get() = byMonth.maxByOrNull { it.spent }

    /** Average spend per month over the months that actually have data, not over the calendar. */
    val averageMonthlySpend: Double get() = if (byMonth.isEmpty()) 0.0 else spent / byMonth.size

    companion object {
        /** How many merchants [topMerchants] keeps — a leaderboard, not a directory. */
        const val TOP_MERCHANTS = 5

        val EMPTY = Insights(
            transactionCount = 0,
            currency = "",
            from = null,
            to = null,
            spent = 0.0,
            income = 0.0,
            byCategory = emptyList(),
            byMonth = emptyList(),
            topMerchants = emptyList(),
            mostVisitedMerchant = null,
            biggestPurchase = null,
            latestPurchase = null,
            uncategorizedSpent = 0.0,
            uncategorizedCount = 0,
        )

        fun from(transactions: List<Transaction>): Insights {
            if (transactions.isEmpty()) return EMPTY

            val spending = transactions.filter { it.amount < 0 }
            val incomeRows = transactions.filter { it.amount > 0 }
            val spent = spending.sumOf { -it.amount }

            val byCategory = spending
                .mapNotNull { row -> row.categoryOrNull?.let { it to row } }
                .groupBy({ it.first }, { it.second })
                .map { (category, rows) ->
                    val sum = rows.sumOf { -it.amount }
                    CategorySlice(
                        category = category,
                        spent = sum,
                        count = rows.size,
                        // Share of *all* spending, including the uncategorised part — so the shares of a
                        // half-labelled statement add up to less than 100%, which is the honest picture.
                        share = if (spent > 0) (sum / spent).toFloat() else 0f,
                    )
                }
                .sortedByDescending { it.spent }

            val byMonth = transactions
                .groupBy { it.date.yearMonth }
                .map { (month, rows) ->
                    MonthSlice(
                        month = month,
                        spent = rows.filter { it.amount < 0 }.sumOf { -it.amount },
                        income = rows.filter { it.amount > 0 }.sumOf { it.amount },
                        count = rows.size,
                    )
                }
                .sortedBy { it.month }

            val merchants = spending
                .groupBy { it.merchant.trim().uppercase() }
                .map { (_, rows) ->
                    MerchantSlice(
                        // The display name comes from a row, not the uppercased grouping key, or every
                        // merchant would shout.
                        merchant = rows.first().merchant.trim(),
                        spent = rows.sumOf { -it.amount },
                        count = rows.size,
                    )
                }

            val uncategorized = spending.filter { it.categoryOrNull == null }

            return Insights(
                transactionCount = transactions.size,
                currency = transactions.first().currency,
                from = transactions.minOf { it.date },
                to = transactions.maxOf { it.date },
                spent = spent,
                income = incomeRows.sumOf { it.amount },
                byCategory = byCategory,
                byMonth = byMonth,
                topMerchants = merchants.sortedByDescending { it.spent }.take(TOP_MERCHANTS),
                // Computed over every merchant, not over topMerchants: the place you go weekly can easily
                // sit outside the five you spend the most at.
                mostVisitedMerchant = merchants.maxByOrNull { it.count },
                biggestPurchase = spending.maxByOrNull { -it.amount },
                latestPurchase = spending.maxByOrNull { it.date },
                uncategorizedSpent = uncategorized.sumOf { -it.amount },
                uncategorizedCount = uncategorized.size,
            )
        }
    }
}
