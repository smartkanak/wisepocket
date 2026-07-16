package date.oxi.wisepocket

import date.oxi.wisepocket.insights.Insights
import date.oxi.wisepocket.insights.WrappedCard
import date.oxi.wisepocket.insights.WrappedStory
import date.oxi.wisepocket.model.Category
import date.oxi.wisepocket.model.Transaction
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WrappedStoryTest {

    private var seq = 0

    private fun row(
        merchant: String,
        amount: Double,
        month: Int = 3,
        day: Int = 1,
        category: Category? = null,
    ) = Transaction(
        id = "r${seq++}",
        date = LocalDate(2026, month, day),
        merchant = merchant,
        amount = amount,
        category = category?.name,
    )

    private fun story(rows: List<Transaction>) = WrappedStory.from(Insights.from(rows))

    private inline fun <reified T : WrappedCard> List<WrappedCard>.of() = filterIsInstance<T>()

    @Test
    fun noTransactionsMeansNoStory() {
        assertEquals(emptyList(), story(emptyList()))
    }

    @Test
    fun aNormalStatementOpensWithTheperiodAndClosesWithTheNet() {
        val cards = story(listOf(row("Employer", 2000.0), row("REWE", -500.0, category = Category.GROCERIES)))

        assertTrue(cards.first() is WrappedCard.Intro)
        val finale = cards.last() as WrappedCard.BigNumber
        assertEquals("You kept", finale.label)
        assertEquals("1500.00 EUR", finale.value)
    }

    @Test
    fun overspendingGetsItsOwnFinale() {
        val cards = story(listOf(row("Employer", 100.0), row("IKEA", -500.0, category = Category.SHOPPING)))

        val finale = cards.last() as WrappedCard.BigNumber
        assertEquals("You spent more than came in", finale.label)
        assertEquals("400.00 EUR", finale.value)
    }

    @Test
    fun anUncategorisedStatementShowsNoCategoryCardsAndSaysWhy() {
        // The honest consequence of LLM-only categorisation with no model on disk: there is nothing to
        // rank. Showing a podium of one "Other" bucket would be a fact about their money that isn't one.
        val cards = story(listOf(row("SEPA 4711", -100.0), row("SEPA 4712", -50.0)))

        assertTrue(cards.of<WrappedCard.Ranking>().isEmpty())
        val nudge = cards.of<WrappedCard.Nudge>().single()
        assertTrue(nudge.message.contains("150.00 EUR"), "the nudge must name what's missing: ${nudge.message}")
        assertTrue(nudge.message.contains("2 transactions"))
    }

    @Test
    fun oneTransactionIsNotOneTransactions() {
        // Spotted on a real screen. The story is prose, and "1 transactions" reads like a bug.
        val cards = story(listOf(row("SEPA 4711", -120.0)))

        assertTrue(cards.of<WrappedCard.Nudge>().single().message.contains("1 transaction isn't"))
        assertTrue(cards.of<WrappedCard.BigNumber>().any { it.caption == "across 1 transaction" })
    }

    @Test
    fun aFullyCategorisedStatementNeedsNoNudge() {
        val cards = story(
            listOf(
                row("REWE", -100.0, category = Category.GROCERIES),
                row("Shell", -50.0, category = Category.TRANSPORT),
            ),
        )

        assertTrue(cards.of<WrappedCard.Nudge>().isEmpty())
    }

    @Test
    fun oneCategoryEarnsAHeadlineButNoPodium() {
        // A ranking of one isn't a ranking.
        val cards = story(listOf(row("REWE", -100.0, category = Category.GROCERIES)))

        assertTrue(cards.of<WrappedCard.Ranking>().isEmpty())
        assertTrue(cards.of<WrappedCard.BigNumber>().any { it.label == "Your biggest category" })
    }

    @Test
    fun thePodiumIsRankedAndCappedAtThree() {
        val cards = story(
            listOf(
                row("A", -10.0, category = Category.HEALTH),
                row("B", -40.0, category = Category.GROCERIES),
                row("C", -30.0, category = Category.TRANSPORT),
                row("D", -20.0, category = Category.SHOPPING),
            ),
        )

        val podium = cards.of<WrappedCard.Ranking>().single { it.title == "Where it went" }
        assertEquals(listOf("Groceries", "Transport", "Shopping"), podium.entries.map { it.label })
        assertEquals(0.4f, podium.entries.first().share)
    }

    @Test
    fun aSingleMonthHasNoPriciestMonth() {
        // Everything is the priciest month when there's only one, which tells the user nothing.
        val single = story(listOf(row("A", -10.0, month = 3), row("B", -20.0, month = 3)))
        assertTrue(single.of<WrappedCard.BigNumber>().none { it.label == "Your priciest month" })

        val across = story(listOf(row("A", -10.0, month = 3), row("B", -90.0, month = 4)))
        val month = across.of<WrappedCard.BigNumber>().single { it.label == "Your priciest month" }
        assertEquals("2026-04", month.value)
    }

    @Test
    fun aMerchantVisitedOnceIsNotAFavourite() {
        val once = story(listOf(row("REWE", -10.0), row("Aldi", -20.0)))
        assertTrue(once.of<WrappedCard.BigNumber>().none { it.label == "Your most-visited" })

        val twice = story(listOf(row("REWE", -10.0, day = 1), row("REWE", -20.0, day = 2)))
        val favourite = twice.of<WrappedCard.BigNumber>().single { it.label == "Your most-visited" }
        assertEquals("REWE", favourite.value)
        assertTrue(favourite.caption.contains("2 times"))
    }

    @Test
    fun theMostVisitedMerchantIsNotTheMostExpensiveOne() {
        // Caught on a real screen, not here: the card used to come off the spend ranking, so a single
        // 899-euro purchase made the "most-visited" merchant a shop visited once — and the rule that a
        // favourite needs two visits then dropped the card entirely, hiding the weekly supermarket.
        val cards = story(
            listOf(
                row("MediaMarkt", -899.0, day = 1, category = Category.SHOPPING),
                row("REWE", -20.0, day = 2, category = Category.GROCERIES),
                row("REWE", -30.0, day = 3, category = Category.GROCERIES),
                row("REWE", -25.0, day = 4, category = Category.GROCERIES),
            ),
        )

        val favourite = cards.of<WrappedCard.BigNumber>().single { it.label == "Your most-visited" }
        assertEquals("REWE", favourite.value)
        assertTrue(favourite.caption.contains("3 times"))
    }

    @Test
    fun theBiggestPurchaseIsSpendingNotIncome() {
        val cards = story(listOf(row("Bonus", 5000.0), row("Laptop", -900.0, category = Category.SHOPPING)))

        val biggest = cards.of<WrappedCard.BigNumber>().single { it.label == "Your biggest single purchase" }
        assertEquals("900.00 EUR", biggest.value)
        assertTrue(biggest.caption.contains("Laptop"))
    }
}
