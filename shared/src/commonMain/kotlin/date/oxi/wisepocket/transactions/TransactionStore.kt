package date.oxi.wisepocket.transactions

import date.oxi.wisepocket.data.TransactionDao
import date.oxi.wisepocket.data.toDomain
import date.oxi.wisepocket.data.toEntity
import date.oxi.wisepocket.model.Transaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * The user's transactions — the app's actual subject matter. Importing is one way to fill this; adding a
 * row by hand is another, and neither is privileged.
 *
 * Backed by Room, so the list is now the database's and survives the app being closed. Reads are a Flow
 * the database owns: nothing here caches a copy, which is what keeps a write and the screen from ever
 * disagreeing. Writes are fire-and-forget into [scope] — the UI learns they happened by the query
 * re-emitting, not by being told.
 *
 * Registered as a Koin `single` so every screen observes the same query.
 */
class TransactionStore(
    private val dao: TransactionDao,
    private val scope: CoroutineScope,
) {
    // Eagerly, not WhileSubscribed: the chat reads this to know what it may talk about even while the
    // transactions screen isn't composed, and a cold start there would answer from an empty list.
    val transactions: StateFlow<List<Transaction>> =
        dao.observeAll()
            .map { rows -> rows.map { it.toDomain() } }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Adds imported rows. Ids are assigned by the database — see [toEntity]. */
    fun addAll(incoming: List<Transaction>) {
        if (incoming.isEmpty()) return
        scope.launch { dao.insertAll(incoming.map { it.toEntity(asNewRow = true) }) }
    }

    /** Adds one blank row for the user to fill in, and returns its id so the UI can open it for editing. */
    suspend fun addBlank(date: LocalDate): String {
        val blank = Transaction(id = "", date = date, merchant = "", amount = 0.0)
        return dao.insert(blank.toEntity(asNewRow = true)).toString()
    }

    fun update(transaction: Transaction) {
        scope.launch { dao.update(transaction.toEntity()) }
    }

    fun remove(id: String) {
        val rowId = id.toLongOrNull() ?: return
        scope.launch { dao.delete(rowId) }
    }
}
