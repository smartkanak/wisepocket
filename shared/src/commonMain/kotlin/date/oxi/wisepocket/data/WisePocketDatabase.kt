package date.oxi.wisepocket.data

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

@Database(entities = [TransactionEntity::class], version = 1)
@ConstructedBy(WisePocketDatabaseConstructor::class)
abstract class WisePocketDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
}

/**
 * Room requires this on any target that isn't Android, and generates every `actual` itself — there is no
 * platform code behind it and nothing here for us to get wrong per platform.
 *
 * Worth saying because it's the one `expect` in the codebase that isn't a platform seam. The others exist
 * because Android and iOS genuinely do a thing differently; this one is a handshake with a code generator,
 * which is why ArchitectureTest lists it separately from the seams.
 */
@Suppress("KotlinNoActualForExpect", "NO_ACTUAL_FOR_EXPECT")
expect object WisePocketDatabaseConstructor : RoomDatabaseConstructor<WisePocketDatabase> {
    override fun initialize(): WisePocketDatabase
}
