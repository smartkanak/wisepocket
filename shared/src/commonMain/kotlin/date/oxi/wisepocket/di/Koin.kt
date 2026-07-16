package date.oxi.wisepocket.di

import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration
import org.koin.mp.KoinPlatformTools

/**
 * Starts Koin for the process. Called from each platform's entry point — Android's `Application`, iOS's
 * `MainViewController`.
 *
 * Deliberately *not* the `KoinApplication` composable, which is the usual Compose Multiplatform advice.
 * That one ties the container's lifetime to the composition, and on Android a rotation recreates the
 * Activity: Koin would stop and restart, taking [date.oxi.wisepocket.llm.LlmProvider] with it and loading
 * a ~1 GB model into llama.cpp's one native context a second time. The container has to outlive the UI.
 *
 * Idempotent, because the entry points can't all promise to run exactly once — `startKoin` on its own
 * throws the second time.
 *
 * The check goes through [KoinPlatformTools], not `GlobalContext`: the latter reads like the obvious API
 * but is JVM-only, so it compiles on Android and fails to resolve for iOS.
 */
fun initKoin(platformConfig: KoinAppDeclaration = {}) {
    if (KoinPlatformTools.defaultContext().getOrNull() != null) return
    startKoin {
        platformConfig()
        modules(appModule)
    }
}
