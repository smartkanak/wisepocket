package date.oxi.wisepocket.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    /**
     * Newest first, sorted in SQL rather than in Kotlin — the list is the screen's whole content, so
     * sorting it on every emission is work the index already did.
     */
    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Insert
    suspend fun insert(row: TransactionEntity): Long

    @Insert
    suspend fun insertAll(rows: List<TransactionEntity>)

    @Update
    suspend fun update(row: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: Long)

    /**
     * Empties the table.
     *
     * Note what this does *not* reset: `AUTOINCREMENT` keeps counting, so ids are never reused. That's
     * worth keeping — a stale id held by a screen mid-delete then resolves to nothing rather than to
     * somebody else's row.
     */
    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}
