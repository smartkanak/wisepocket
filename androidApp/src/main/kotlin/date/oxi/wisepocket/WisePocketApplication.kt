package date.oxi.wisepocket

import android.app.Application
import date.oxi.wisepocket.di.initKoin
import date.oxi.wisepocket.di.platformModule
import date.oxi.wisepocket.llm.initWisePocketAndroid
import org.koin.android.ext.koin.androidContext

/**
 * Starts the object graph once, for the process.
 *
 * This is why an Application class exists at all: the work below used to sit in `MainActivity.onCreate`,
 * which runs again on every rotation. Koin is process-scoped precisely so the loaded ~1 GB model survives
 * a configuration change — starting it from an Activity would undo that.
 */
class WisePocketApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initWisePocketAndroid(applicationContext)
        initKoin {
            androidContext(this@WisePocketApplication)
            modules(platformModule())
        }
    }
}
