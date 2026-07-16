package date.oxi.wisepocket

import date.oxi.wisepocket.insights.Categorizer
import date.oxi.wisepocket.llm.LlmEngine
import date.oxi.wisepocket.model.Category
import date.oxi.wisepocket.model.Transaction
import date.oxi.wisepocket.transactions.TransactionStore
import date.oxi.wisepocket.transactions.TransactionsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Covers the gap that made the app feel arbitrary: categorisation only ever ran inside an import, so
 * someone who imported first and fetched the model afterwards could never get a category, and nothing in
 * the UI could fix it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModelTest {

    // The real ViewModel, not a stand-in — viewModelScope needs a Main dispatcher, and that's all it needs.
    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun row(id: String, merchant: String, amount: Double) =
        Transaction(id = id, date = LocalDate(2026, 3, 1), merchant = merchant, amount = amount)

    private fun store() = TransactionStore(FakeTransactionDao(), scope = MainScope())

    private fun subject(store: TransactionStore, engine: LlmEngine?, gateway: FakeModelGateway) =
        TransactionsViewModel(store = store, categorizer = Categorizer { engine }, model = gateway)

    /**
     * Answers by merchant rather than by position.
     *
     * Written this way because the naive version — a fixed "1:GROCERIES\n2:TRANSPORT" — quietly passed the
     * wrong labels: the batch arrives in the store's newest-first order, so on same-day rows the numbering
     * followed the ids, not the order they were written in the test.
     */
    private fun engineLabelling(vararg labels: Pair<String, Category>) = FakeLlmEngine { context ->
        context.lines().mapNotNull { line ->
            val index = line.substringBefore('.').trim()
            labels.firstOrNull { (merchant, _) -> line.contains(merchant) }
                ?.let { (_, category) -> "$index:${category.name}" }
        }.joinToString("\n")
    }

    @Test
    fun categorisesRowsThatWereImportedBeforeTheModelExisted() = runTest(UnconfinedTestDispatcher()) {
        val store = store()
        store.addAll(listOf(row("row-0", "REWE", -10.0), row("row-1", "Shell", -60.0)))
        assertNull(store.transactions.value.first().category, "precondition: nothing is categorised")

        // The model turns up only now — exactly the "downloaded it afterwards" case that did nothing.
        val gateway = FakeModelGateway()
        val engine = engineLabelling("REWE" to Category.GROCERIES, "Shell" to Category.TRANSPORT)
        val subject = subject(store, engine, gateway)
        gateway.becomeReady()
        subject.categorizeExisting()

        val byMerchant = store.transactions.value.associateBy { it.merchant }
        assertEquals(Category.GROCERIES.name, byMerchant.getValue("REWE").category)
        assertEquals(Category.TRANSPORT.name, byMerchant.getValue("Shell").category)
    }

    @Test
    fun aHandCorrectedCategoryIsNotOverwritten() = runTest(UnconfinedTestDispatcher()) {
        val store = store()
        store.addAll(listOf(row("row-0", "REWE", -10.0), row("row-1", "Shell", -60.0)))
        val rewe = store.transactions.value.first { it.merchant == "REWE" }
        store.update(rewe.copy(category = Category.RESTAURANTS.name))

        // The model would say GROCERIES for REWE. The user already said otherwise, and they were there.
        val engine = engineLabelling("REWE" to Category.GROCERIES, "Shell" to Category.TRANSPORT)
        subject(store, engine, FakeModelGateway()).categorizeExisting()

        val byMerchant = store.transactions.value.associateBy { it.merchant }
        assertEquals(Category.RESTAURANTS.name, byMerchant.getValue("REWE").category)
        assertEquals(Category.TRANSPORT.name, byMerchant.getValue("Shell").category)
        assertEquals("1. Shell", engine.contexts.single().trim(), "REWE must not have been asked about")
    }

    @Test
    fun withoutAModelNothingChangesAndNothingBreaks() = runTest(UnconfinedTestDispatcher()) {
        val store = store()
        store.addAll(listOf(row("row-0", "REWE", -10.0)))

        subject(store, engine = null, gateway = FakeModelGateway()).categorizeExisting()

        assertNull(store.transactions.value.single().category)
    }

    @Test
    fun theProgressIndicatorIsClearedWhenTheRunEnds() = runTest(UnconfinedTestDispatcher()) {
        val store = store()
        store.addAll(listOf(row("row-0", "REWE", -10.0)))
        val subject = subject(store, engineLabelling("REWE" to Category.GROCERIES), FakeModelGateway())

        subject.categorizeExisting()

        // Left set, the UI would sit on a progress bar for the rest of the session.
        assertNull(subject.state.value.categorizingProgress)
    }

    @Test
    fun theUncategorisedCountIgnoresIncome() = runTest(UnconfinedTestDispatcher()) {
        val store = store()
        store.addAll(listOf(row("row-0", "Employer", 3000.0), row("row-1", "REWE", -10.0)))

        val subject = subject(store, engine = null, gateway = FakeModelGateway())

        // Income has no category by design, so counting it would leave a banner no action could ever clear.
        assertEquals(1, subject.state.value.uncategorizedCount)
    }

    @Test
    fun aFreshInstallIsRecognisedAndStopsBeingOneOnceThereIsData() = runTest(UnconfinedTestDispatcher()) {
        val store = store()
        val gateway = FakeModelGateway()
        val subject = subject(store, engine = null, gateway = gateway)

        // No model, nothing imported — this is what the onboarding screen keys off, derived rather than
        // stored, which is why the app needs no "hasSeenOnboarding" flag and no DataStore.
        assertEquals(true, subject.state.value.isFirstRun)

        store.addAll(listOf(row("row-0", "REWE", -10.0)))
        assertEquals(false, subject.state.value.isFirstRun)
    }
}
