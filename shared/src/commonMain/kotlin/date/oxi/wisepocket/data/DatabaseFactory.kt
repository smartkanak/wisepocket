package date.oxi.wisepocket.data

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

/** The file name is shared, so both platforms name the same database the same thing. */
const val DATABASE_FILE = "wisepocket.db"

/**
 * Finishes a platform's half-built database.
 *
 * Only *where the file goes* differs between Android and iOS; the driver and everything downstream of it
 * are common. [BundledSQLiteDriver] ships its own SQLite rather than borrowing the OS's, so both platforms
 * run the same engine at the same version — the alternative is a statement that behaves subtly differently
 * on a two-year-old Android than on iOS.
 *
 * The query dispatcher is left alone deliberately: Room already defaults it to `Dispatchers.IO`, and that
 * dispatcher isn't reachable from common code anyway (it's internal here, platform-only in coroutines).
 */
fun buildDatabase(builder: RoomDatabase.Builder<WisePocketDatabase>): WisePocketDatabase = builder
    .setDriver(BundledSQLiteDriver())
    .build()
