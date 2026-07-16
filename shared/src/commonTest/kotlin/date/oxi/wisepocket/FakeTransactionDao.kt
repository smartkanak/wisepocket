package date.oxi.wisepocket

import date.oxi.wisepocket.data.TransactionDao
import date.oxi.wisepocket.data.TransactionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Stands in for Room so the store's own rules can be tested without a device or a native SQLite.
 *
 * It mimics the two behaviours [TransactionStore] leans on: the primary key is assigned here, not by the
 * caller, and [observeAll] sorts the same way the DAO's `ORDER BY` does. The real SQL is exercised on
 * device instead — a fake can agree with a wrong expectation, so it isn't asked to prove the schema.
 */
class FakeTransactionDao : TransactionDao {

    private val rows = MutableStateFlow<List<TransactionEntity>>(emptyList())
    private var nextId = 1L

    private val newestFirst =
        compareByDescending<TransactionEntity> { it.date }.thenByDescending { it.id }

    override fun observeAll(): Flow<List<TransactionEntity>> =
        rows.map { all -> all.sortedWith(newestFirst) }

    override suspend fun insert(row: TransactionEntity): Long {
        val id = nextId++
        rows.value = rows.value + row.copy(id = id)
        return id
    }

    override suspend fun insertAll(rows: List<TransactionEntity>) {
        rows.forEach { insert(it) }
    }

    override suspend fun update(row: TransactionEntity) {
        rows.value = rows.value.map { if (it.id == row.id) row else it }
    }

    override suspend fun delete(id: Long) {
        rows.value = rows.value.filterNot { it.id == id }
    }
}
