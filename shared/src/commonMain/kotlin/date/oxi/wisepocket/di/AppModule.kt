package date.oxi.wisepocket.di

import date.oxi.wisepocket.chat.ChatViewModel
import date.oxi.wisepocket.data.WisePocketDatabase
import date.oxi.wisepocket.insights.Categorizer
import date.oxi.wisepocket.llm.LlmProvider
import date.oxi.wisepocket.llm.ModelRepository
import date.oxi.wisepocket.review.ImportViewModel
import date.oxi.wisepocket.statement.StatementImporter
import date.oxi.wisepocket.transactions.TransactionStore
import date.oxi.wisepocket.transactions.TransactionsViewModel
import date.oxi.wisepocket.wrapped.WrappedViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * What the app is made of, in one place.
 *
 * The two `single`s below used to be Kotlin `object`s. The graph is what they always were — the store is
 * the app's data and the provider owns the one native model — but as objects that scope was a property of
 * the language rather than a decision, and nothing could stand in for them in a test.
 */
val appModule = module {

    // App-scoped on purpose, and load-bearing: LlamaBridge is a singleton over native llama.cpp state, so
    // a second LlmProvider would not be a second model — it would be the same native context loaded twice,
    // ~1 GB of GGUF at a time. Koin gives us one, for the process, for everyone who asks.
    single { ModelRepository(fileName = LlmProvider.DEFAULT_MODEL_FILE) }
    single { LlmProvider(repository = get()) }

    // The user's data. The DAO comes from the database each platform module contributes.
    single { get<WisePocketDatabase>().transactionDao() }

    // The scope outlives every screen, because the store's writes must: a row being saved shouldn't be
    // cancelled by the user navigating away mid-insert. SupervisorJob so one failed write doesn't take
    // the store's query down with it.
    single { TransactionStore(dao = get(), scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)) }

    // A factory, not a single: an importer belongs to one import. `engineProvider` stays a lambda rather
    // than an injected engine because the model loads asynchronously — an import started after the model
    // is ready gets the LLM even though the importer was built before it.
    factory { StatementImporter(engineProvider = get<LlmProvider>()::engineOrNull) }

    // Same lambda-not-engine reasoning as the importer above, and for the same reason: categorising is
    // part of an import, and the model may have finished loading only after the screen was built.
    factory { Categorizer(engineProvider = get<LlmProvider>()::engineOrNull) }

    viewModel { TransactionsViewModel(store = get(), categorizer = get(), model = get<LlmProvider>()) }
    viewModel { WrappedViewModel(transactionStore = get()) }
    viewModel { ChatViewModel(llm = get(), transactionStore = get()) }
    viewModel {
        ImportViewModel(importer = get(), categorizer = get(), transactionStore = get(), llm = get())
    }
}
