package date.oxi.wisepocket

import date.oxi.wisepocket.insights.Insights
import date.oxi.wisepocket.model.Category
import date.oxi.wisepocket.model.Transaction
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InsightsTest {

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

    @Test
    fun emptyInputProducesEmptyInsights() {
        val insights = Insights.from(emptyList())
        assertTrue(insights.isEmpty)
        assertNull(insights.topCategory)
        assertEquals(0.0, insights.spent)
    }

    @Test
    fun categoriesAreRankedBySpendWithShares() {
        val insights = Insights.from(
            listOf(
                row("REWE", -30.0, category = Category.GROCERIES),
                row("ALDI", -10.0, category = Category.GROCERIES),
                row("Shell", -40.0, category = Category.TRANSPORT),
            ),
        )

        val top = insights.byCategory.first()
        assertEquals(Category.GROCERIES, top.category)
        assertEquals(40.0, top.spent)
        assertEquals(2, top.count)
        assertEquals(0.5f, top.share)
    }

    @Test
    fun sharesOfAHalfLabelledStatementDoNotAddUpToOne() {
        // Deliberate: categorisation runs on-device and can come back empty. A Wrapped that renormalised
        // the shares would show a confident 100% while silently ignoring half the money.
        val insights = Insights.from(
            listOf(
                row("REWE", -50.0, category = Category.GROCERIES),
                row("UNKNOWN GMBH", -50.0),
            ),
        )

        assertEquals(0.5f, insights.byCategory.single().share)
        assertEquals(50.0, insights.uncategorizedSpent)
        assertEquals(1, insights.uncategorizedCount)
    }

    @Test
    fun incomeIsNotSpendingAndNetIsTheDifference() {
        val insights = Insights.from(
            listOf(row("Employer", 3000.0), row("REWE", -200.0, category = Category.GROCERIES)),
        )

        assertEquals(3000.0, insights.income)
        assertEquals(200.0, insights.spent)
        assertEquals(2800.0, insights.net)
        // Income must never reach a spending category, whatever label a row happens to carry.
        assertEquals(listOf(Category.GROCERIES), insights.byCategory.map { it.category })
    }

    @Test
    fun monthsAreChronologicalAndTheBiggestIsFound() {
        val insights = Insights.from(
            listOf(
                row("A", -10.0, month = 1),
                row("B", -90.0, month = 2),
                row("C", -20.0, month = 3),
            ),
        )

        assertEquals(listOf(1, 2, 3), insights.byMonth.map { it.month.month.number })
        assertEquals(90.0, insights.biggestMonth?.spent)
        assertEquals(40.0, insights.averageMonthlySpend)
    }

    @Test
    fun merchantsAreGroupedCaseInsensitivelyButKeepTheirDisplayName() {
        val insights = Insights.from(listOf(row("Rewe", -10.0), row("REWE", -25.0)))

        val top = insights.topMerchants.single()
        assertEquals("Rewe", top.merchant, "the grouping key must not become the display name")
        assertEquals(35.0, top.spent)
        assertEquals(2, top.count)
    }

    @Test
    fun biggestAndLatestPurchaseAreDifferentQuestions() {
        val insights = Insights.from(
            listOf(
                row("Laptop", -900.0, month = 1, day = 5),
                row("Coffee", -3.0, month = 6, day = 20),
                row("Bonus", 500.0, month = 7, day = 1),
            ),
        )

        assertEquals("Laptop", insights.biggestPurchase?.merchant)
        // Latest *purchase*, not latest row — the bonus is newer and is not a purchase.
        assertEquals("Coffee", insights.latestPurchase?.merchant)
    }

    @Test
    fun mostVisitedIsCountedOverEveryMerchantNotJustTheBigSpenders() {
        val rows = (1..6).map { row("Big$it", -500.0, day = it) } +
            (1..4).map { row("Corner Shop", -3.0, day = it) }

        val insights = Insights.from(rows)

        // Six merchants outspend it, so it isn't in topMerchants at all — but it's still where they go.
        assertTrue(insights.topMerchants.none { it.merchant == "Corner Shop" })
        assertEquals("Corner Shop", insights.mostVisitedMerchant?.merchant)
        assertEquals(4, insights.mostVisitedMerchant?.count)
    }

    @Test
    fun topMerchantsIsALeaderboardNotADirectory() {
        val insights = Insights.from((1..12).map { row("M$it", -it.toDouble()) })
        assertEquals(Insights.TOP_MERCHANTS, insights.topMerchants.size)
        assertEquals("M12", insights.topMerchants.first().merchant)
    }
}
