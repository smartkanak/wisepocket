package date.oxi.wisepocket

import date.oxi.wisepocket.model.Transaction
import date.oxi.wisepocket.transactions.TransactionStore
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransactionStoreTest {

    @BeforeTest
    fun setUp() = TransactionStore.clear()

    @AfterTest
    fun tearDown() = TransactionStore.clear()

    private fun row(id: String, day: Int, amount: Double) =
        Transaction(id = id, date = LocalDate(2026, 3, day), merchant = "m$day", amount = amount)

    @Test
    fun importsFromTwoStatementsDoNotCollide() {
        // Parsed rows arrive as "row-0", "row-1" … unique only within one statement. Without reassignment
        // the second import shadows the first and editing one row would change another.
        TransactionStore.addAll(listOf(row("row-0", 1, -10.0), row("row-1", 2, -20.0)))
        TransactionStore.addAll(listOf(row("row-0", 3, -30.0), row("row-1", 4, -40.0)))

        val ids = TransactionStore.transactions.value.map { it.id }
        assertEquals(4, TransactionStore.transactions.value.size)
        assertEquals(ids.size, ids.toSet().size, "ids collided: $ids")
    }

    @Test
    fun editingOneRowLeavesTheOthersAlone() {
        TransactionStore.addAll(listOf(row("row-0", 1, -10.0), row("row-1", 2, -20.0)))
        val target = TransactionStore.transactions.value.first()

        TransactionStore.update(target.copy(merchant = "corrected", amount = -99.0))

        val all = TransactionStore.transactions.value
        assertEquals(2, all.size)
        assertEquals("corrected", all.first { it.id == target.id }.merchant)
        // The list is newest-first, so the untouched row is the day-1 one.
        assertEquals(-10.0, all.first { it.id != target.id }.amount)
    }

    @Test
    fun rowsAreSortedNewestFirst() {
        TransactionStore.addAll(listOf(row("a", 1, -10.0), row("b", 9, -20.0), row("c", 5, -30.0)))
        assertEquals(
            listOf(LocalDate(2026, 3, 9), LocalDate(2026, 3, 5), LocalDate(2026, 3, 1)),
            TransactionStore.transactions.value.map { it.date },
        )
    }

    @Test
    fun deleteRemovesOnlyTheGivenRow() {
        TransactionStore.addAll(listOf(row("a", 1, -10.0), row("b", 2, -20.0)))
        val victim = TransactionStore.transactions.value.first()

        TransactionStore.remove(victim.id)

        assertEquals(1, TransactionStore.transactions.value.size)
        assertTrue(TransactionStore.transactions.value.none { it.id == victim.id })
    }

    @Test
    fun blankRowIsAddedAtTheTopAndIdentifiable() {
        TransactionStore.addAll(listOf(row("a", 1, -10.0)))
        val id = TransactionStore.addBlank(LocalDate(2026, 3, 4))

        // Returned so the list can open it for editing straight away — it's empty and needs filling in.
        assertEquals(id, TransactionStore.transactions.value.first().id)
        assertEquals("", TransactionStore.transactions.value.first().merchant)
    }
}
