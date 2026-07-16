package date.oxi.wisepocket.di

import androidx.room.Room
import date.oxi.wisepocket.data.DATABASE_FILE
import date.oxi.wisepocket.data.WisePocketDatabase
import date.oxi.wisepocket.data.buildDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * What Android contributes to the graph.
 *
 * This is deliberately a Koin module rather than another `expect`/`actual` pair: the entry points already
 * differ per platform, so they can carry the one thing that differs — where the database file lives —
 * without widening the `expect` seam that keeps the two PDF engines honest.
 */
fun platformModule() = module {
    single {
        val context = androidContext()
        buildDatabase(
            Room.databaseBuilder<WisePocketDatabase>(
                context = context,
                name = context.getDatabasePath(DATABASE_FILE).absolutePath,
            ),
        )
    }
}
