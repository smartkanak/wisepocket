package date.oxi.wisepocket.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import date.oxi.wisepocket.model.Transaction
import kotlinx.datetime.LocalDate

/**
 * A transaction as it sits on disk.
 *
 * Separate from [Transaction] rather than annotating the domain type directly: the domain is what the
 * parser produces and the chat reasons about, and it shouldn't have to answer to a schema. The two look
 * alike today; the mapping is what lets the table change without the pipeline moving.
 */
@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /**
     * ISO-8601 (`2026-03-04`). Stored as text because SQLite has no date type, and in this format
     * lexicographic order *is* chronological order — which is what lets the DAO sort in SQL.
     */
    val date: String,
    val merchant: String,
    val amount: Double,
    val currency: String,
    val category: String?,
)

fun TransactionEntity.toDomain() = Transaction(
    id = id.toString(),
    date = LocalDate.parse(date),
    merchant = merchant,
    amount = amount,
    currency = currency,
    category = category,
)

/**
 * @param asNewRow when true the id is dropped so SQLite assigns a fresh one. Parsed rows arrive as
 * "row-0", "row-1" … which are only unique within one statement — importing a second statement would
 * otherwise collide with the first and make later edits hit the wrong row.
 */
fun Transaction.toEntity(asNewRow: Boolean = false) = TransactionEntity(
    id = if (asNewRow) 0 else id.toLongOrNull() ?: 0,
    date = date.toString(),
    merchant = merchant,
    amount = amount,
    currency = currency,
    category = category,
)
