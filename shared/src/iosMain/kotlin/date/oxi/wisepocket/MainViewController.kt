package date.oxi.wisepocket

import androidx.compose.ui.window.ComposeUIViewController
import date.oxi.wisepocket.di.initKoin
import date.oxi.wisepocket.di.platformModule

/**
 * The iOS entry point, called from Swift as `MainViewControllerKt.MainViewController()`.
 *
 * PascalCase is not a slip: this reads as a type constructor on the Swift side, which is the convention
 * every Compose Multiplatform iOS host follows. Renaming it to satisfy the Kotlin function-naming rule
 * would make the Swift call site read wrong.
 */
@Suppress("FunctionNaming")
fun MainViewController() = ComposeUIViewController {
    // iOS has no Application to hang this off, so the entry point starts the graph itself. initKoin is
    // idempotent, which is what makes calling it from here safe.
    initKoin { modules(platformModule()) }
    App()
}
