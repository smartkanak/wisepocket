package date.oxi.wisepocket

import date.oxi.wisepocket.model.Transaction
import date.oxi.wisepocket.transactions.TransactionStore
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransactionStoreTest {

    private fun row(id: String, day: Int, amount: Double) =
        Transaction(id = id, date = LocalDate(2026, 3, day), merchant = "m$day", amount = amount)

    /**
     * A store per test, on an unconfined dispatcher so the writes it launches have landed by the time the
     * assertion reads them back. Storing to a fake keeps this suite free of a native SQLite.
     *
     * `backgroundScope`, not the test scope: the store's `stateIn` collector runs until cancelled, and
     * `runTest` waits at the end for everything launched in its own scope — which would simply hang.
     */
    private fun TestScope.store() = TransactionStore(FakeTransactionDao(), backgroundScope)

    @Test
    fun importsFromTwoStatementsDoNotCollide() = runTest(UnconfinedTestDispatcher()) {
        val store = store()
        // Parsed rows arrive as "row-0", "row-1" … unique only within one statement. Without reassignment
        // the second import shadows the first and editing one row would change another.
        store.addAll(listOf(row("row-0", 1, -10.0), row("row-1", 2, -20.0)))
        store.addAll(listOf(row("row-0", 3, -30.0), row("row-1", 4, -40.0)))

        val ids = store.transactions.value.map { it.id }
        assertEquals(4, store.transactions.value.size)
        assertEquals(ids.size, ids.toSet().size, "ids collided: $ids")
    }

    @Test
    fun editingOneRowLeavesTheOthersAlone() = runTest(UnconfinedTestDispatcher()) {
        val store = store()
        store.addAll(listOf(row("row-0", 1, -10.0), row("row-1", 2, -20.0)))
        val target = store.transactions.value.first()

        store.update(target.copy(merchant = "corrected", amount = -99.0))

        val all = store.transactions.value
        assertEquals(2, all.size)
        assertEquals("corrected", all.first { it.id == target.id }.merchant)
        // The list is newest-first, so the untouched row is the day-1 one.
        assertEquals(-10.0, all.first { it.id != target.id }.amount)
    }

    @Test
    fun rowsAreSortedNewestFirst() = runTest(UnconfinedTestDispatcher()) {
        val store = store()
        store.addAll(listOf(row("a", 1, -10.0), row("b", 9, -20.0), row("c", 5, -30.0)))
        assertEquals(
            listOf(LocalDate(2026, 3, 9), LocalDate(2026, 3, 5), LocalDate(2026, 3, 1)),
            store.transactions.value.map { it.date },
        )
    }

    @Test
    fun deleteRemovesOnlyTheGivenRow() = runTest(UnconfinedTestDispatcher()) {
        val store = store()
        store.addAll(listOf(row("a", 1, -10.0), row("b", 2, -20.0)))
        val victim = store.transactions.value.first()

        store.remove(victim.id)

        assertEquals(1, store.transactions.value.size)
        assertTrue(store.transactions.value.none { it.id == victim.id })
    }

    @Test
    fun blankRowIsAddedAtTheTopAndIdentifiable() = runTest(UnconfinedTestDispatcher()) {
        val store = store()
        store.addAll(listOf(row("a", 1, -10.0)))
        val id = store.addBlank(LocalDate(2026, 3, 4))

        // Returned so the list can open it for editing straight away — it's empty and needs filling in.
        assertEquals(id, store.transactions.value.first().id)
        assertEquals("", store.transactions.value.first().merchant)
    }
}
