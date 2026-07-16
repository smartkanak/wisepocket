package date.oxi.wisepocket.di

import androidx.room.Room
import date.oxi.wisepocket.data.DATABASE_FILE
import date.oxi.wisepocket.data.WisePocketDatabase
import date.oxi.wisepocket.data.buildDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.dsl.module
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/** What iOS contributes to the graph — see the Android twin for why this is a module and not an `expect`. */
fun platformModule() = module {
    single {
        buildDatabase(
            Room.databaseBuilder<WisePocketDatabase>(name = "${documentDirectory()}/$DATABASE_FILE"),
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun documentDirectory(): String {
    val documents = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    return requireNotNull(documents?.path) { "iOS gave us no Documents directory to put the database in" }
}
