package date.oxi.wisepocket.transactions

import date.oxi.wisepocket.model.Transaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The user's transactions — the app's actual subject matter. Importing is one way to fill this; adding a
 * row by hand is another, and neither is privileged.
 *
 * App-scoped so the list survives moving between screens. Held in memory for this slice; persistence is
 * its own chapter.
 */
object TransactionStore {

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()

    /**
     * Ids are reassigned on the way in. Parsed rows arrive as "row-0", "row-1" … which are only unique
     * within one statement — importing a second statement would otherwise collide with the first and make
     * edits hit the wrong row.
     */
    private var nextId = 0

    /** Adds imported rows, newest first. */
    fun addAll(incoming: List<Transaction>) {
        if (incoming.isEmpty()) return
        val withIds = incoming.map { it.copy(id = "tx-${nextId++}") }
        _transactions.value = (_transactions.value + withIds).sortedByDescending { it.date }
    }

    /** Adds one blank row for the user to fill in, and returns its id so the UI can open it for editing. */
    fun addBlank(date: kotlinx.datetime.LocalDate): String {
        val id = "tx-${nextId++}"
        val blank = Transaction(id = id, date = date, merchant = "", amount = 0.0)
        _transactions.value = (listOf(blank) + _transactions.value)
        return id
    }

    fun update(transaction: Transaction) {
        _transactions.value = _transactions.value.map { if (it.id == transaction.id) transaction else it }
    }

    fun remove(id: String) {
        _transactions.value = _transactions.value.filterNot { it.id == id }
    }

    /** Resets the store. Used by tests to isolate cases; the app has no "delete everything" action. */
    fun clear() {
        _transactions.value = emptyList()
    }
}
