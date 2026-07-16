package date.oxi.wisepocket

import androidx.compose.ui.window.ComposeUIViewController

/**
 * The iOS entry point, called from Swift as `MainViewControllerKt.MainViewController()`.
 *
 * PascalCase is not a slip: this reads as a type constructor on the Swift side, which is the convention
 * every Compose Multiplatform iOS host follows. Renaming it to satisfy the Kotlin function-naming rule
 * would make the Swift call site read wrong.
 */
@Suppress("FunctionNaming")
fun MainViewController() = ComposeUIViewController { App() }
