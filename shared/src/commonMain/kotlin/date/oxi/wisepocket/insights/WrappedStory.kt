package date.oxi.wisepocket.insights

import date.oxi.wisepocket.model.formatMoney
import kotlin.math.roundToInt

/** One entry in a ranked card — already carries its share, so the screen never divides. */
data class RankEntry(
    val emoji: String,
    val label: String,
    val value: String,
    val share: Float,
)

/**
 * One screen of the story. A closed set: the renderer handles these four shapes and nothing else, so a new
 * kind of card is a deliberate act rather than a `when` that silently falls through.
 */
sealed interface WrappedCard {

    /** Opener: what period this is about. */
    data class Intro(val period: String, val transactionCount: Int) : WrappedCard

    /** One figure, made large. */
    data class BigNumber(
        val emoji: String,
        val label: String,
        val value: String,
        val caption: String,
    ) : WrappedCard

    /** A podium — categories or merchants. */
    data class Ranking(
        val emoji: String,
        val title: String,
        val entries: List<RankEntry>,
    ) : WrappedCard

    /**
     * Says what the story can't show and why. Not decoration: categorisation runs on-device and can come
     * back empty, and the alternative to admitting that is a Wrapped that looks complete while quietly
     * omitting most of the money.
     */
    data class Nudge(val emoji: String, val title: String, val message: String) : WrappedCard
}

/**
 * Turns [Insights] into the story's cards.
 *
 * Deliberately pure and Compose-free, which is what lets the interesting part — *which* cards a given
 * statement earns — be unit-tested. A card is only built when its data exists: one month of spending has
 * no "busiest month", an uncategorised statement has no category podium, and inventing either would mean
 * showing someone a confident fact about their money that isn't one.
 */
// One small builder per card is the shape LongMethod asked for when `from` was a single 94-line list.
// Splitting it then trips TooManyFunctions, which is the same code judged by the opposite rule — and of the
// two shapes, named-function-per-card is the one where a card's conditions are readable. Local, so the next
// object that accretes twelve unrelated functions is still caught.
@Suppress("TooManyFunctions")
object WrappedStory {

    /** Below this, a "top merchant" is just the only merchant. */
    private const val MIN_MERCHANT_VISITS = 2

    /** A podium, not a leaderboard — three is what fits on a phone and what anyone remembers. */
    private const val PODIUM = 3

    private const val PERCENT = 100

    /** Each builder returns null when the statement hasn't earned that card. */
    fun from(insights: Insights): List<WrappedCard> {
        if (insights.isEmpty) return emptyList()
        return listOfNotNull(
            intro(insights),
            totalSpent(insights),
            biggestCategory(insights),
            podium(insights),
            favouriteMerchant(insights),
            priciestMonth(insights),
            biggestPurchase(insights),
            mysteryNudge(insights),
            finale(insights),
        )
    }

    private fun intro(insights: Insights) = WrappedCard.Intro(
        period = "${insights.from} — ${insights.to}",
        transactionCount = insights.transactionCount,
    )

    private fun totalSpent(insights: Insights) = WrappedCard.BigNumber(
        emoji = "💸",
        label = "You spent",
        value = "${formatMoney(insights.spent)} ${insights.currency}",
        caption = "across ${transactions(insights.transactionCount)}",
    )

    /** "1 transaction", not "1 transactions" — the story is meant to read like a sentence. */
    private fun transactions(count: Int) = if (count == 1) "1 transaction" else "$count transactions"

    private fun biggestCategory(insights: Insights) = insights.topCategory?.let { top ->
        WrappedCard.BigNumber(
            emoji = top.category.emoji,
            label = "Your biggest category",
            value = top.category.label,
            caption = "${formatMoney(top.spent)} ${insights.currency} · " +
                "${percent(top.share)} of everything you spent",
        )
    }

    /** A ranking of one isn't a ranking — the headline card already said it. */
    private fun podium(insights: Insights) = insights.byCategory
        .takeIf { it.size >= 2 }
        ?.let { categories ->
            WrappedCard.Ranking(
                emoji = "🏆",
                title = "Where it went",
                entries = categories.take(PODIUM).map {
                    RankEntry(
                        emoji = it.category.emoji,
                        label = it.category.label,
                        value = "${formatMoney(it.spent)} ${insights.currency}",
                        share = it.share,
                    )
                },
            )
        }

    private fun favouriteMerchant(insights: Insights) = insights.mostVisitedMerchant
        ?.takeIf { it.count >= MIN_MERCHANT_VISITS }
        ?.let { top ->
            WrappedCard.BigNumber(
                emoji = "🔁",
                label = "Your most-visited",
                value = top.merchant,
                caption = "${top.count} times · ${formatMoney(top.spent)} ${insights.currency}",
            )
        }

    /** Only meaningful once there's another month to beat. */
    private fun priciestMonth(insights: Insights) = insights.biggestMonth
        ?.takeIf { insights.byMonth.size >= 2 }
        ?.let { month ->
            WrappedCard.BigNumber(
                emoji = "📅",
                label = "Your priciest month",
                value = month.month.toString(),
                caption = "${formatMoney(month.spent)} ${insights.currency} · " +
                    "average was ${formatMoney(insights.averageMonthlySpend)} ${insights.currency}",
            )
        }

    private fun biggestPurchase(insights: Insights) = insights.biggestPurchase?.let { biggest ->
        WrappedCard.BigNumber(
            emoji = "🎯",
            label = "Your biggest single purchase",
            value = "${formatMoney(-biggest.amount)} ${insights.currency}",
            caption = "${biggest.merchant} · ${biggest.date}",
        )
    }

    private fun mysteryNudge(insights: Insights) = insights.uncategorizedCount
        .takeIf { it > 0 }
        ?.let { count ->
            WrappedCard.Nudge(
                emoji = "❓",
                title = "Some of it is a mystery",
                message = "${formatMoney(insights.uncategorizedSpent)} ${insights.currency} across " +
                    "${transactions(count)} isn't categorised yet, so it's missing from the breakdown " +
                    "above. Tap any of those rows to sort them out.",
            )
        }

    private fun finale(insights: Insights): WrappedCard {
        val net = insights.net
        val caption = "income ${formatMoney(insights.income)} · spending ${formatMoney(insights.spent)}"
        return if (net >= 0) {
            WrappedCard.BigNumber(
                emoji = "🌱",
                label = "You kept",
                value = "${formatMoney(net)} ${insights.currency}",
                caption = caption,
            )
        } else {
            WrappedCard.BigNumber(
                emoji = "🌊",
                label = "You spent more than came in",
                value = "${formatMoney(-net)} ${insights.currency}",
                caption = caption,
            )
        }
    }

    private fun percent(share: Float): String = "${(share * PERCENT).roundToInt()}%"
}
