package date.oxi.wisepocket.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import date.oxi.wisepocket.insights.Categorizer
import date.oxi.wisepocket.llm.ModelGateway
import date.oxi.wisepocket.llm.ModelStatus
import date.oxi.wisepocket.model.Transaction
import date.oxi.wisepocket.model.categoryOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** What the home screen needs to know about itself, in one object rather than assembled in the composable. */
data class TransactionsUiState(
    val transactions: List<Transaction> = emptyList(),
    val modelStatus: ModelStatus = ModelStatus.Checking,
    /** Spending rows with no category — what the "Sort them out" action would work on. */
    val uncategorizedCount: Int = 0,
    /** 0..1 while a catch-up categorisation runs, null when none is. */
    val categorizingProgress: Float? = null,
) {
    val isModelReady: Boolean get() = modelStatus is ModelStatus.Ready
    val hasTransactions: Boolean get() = transactions.isNotEmpty()

    /** True on a genuinely fresh install: nothing imported, and no model. */
    val isFirstRun: Boolean get() = !hasTransactions && modelStatus is ModelStatus.Absent
}

/**
 * The home screen's state and the actions that change it.
 *
 * [categorizeExisting] is the piece the app was missing entirely: categorisation only ever ran inside an
 * import, so downloading the model *after* importing left every row uncategorised forever, with nothing in
 * the UI that could fix it. Labelling is not part of importing — it's something the model does to rows
 * whenever it becomes available.
 */
class TransactionsViewModel(
    private val store: TransactionStore,
    private val categorizer: Categorizer,
    private val model: ModelGateway,
) : ViewModel() {

    private val progress = MutableStateFlow<Float?>(null)

    val state: StateFlow<TransactionsUiState> =
        combine(store.transactions, model.status, progress) { transactions, modelStatus, categorizing ->
            TransactionsUiState(
                transactions = transactions,
                modelStatus = modelStatus,
                uncategorizedCount = transactions.count { it.amount < 0 && it.categoryOrNull == null },
                categorizingProgress = categorizing,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, TransactionsUiState())

    init {
        viewModelScope.launch { model.ensure() }
    }

    /** Starts (or resumes) fetching the model. */
    fun downloadModel(url: String, token: String?) {
        viewModelScope.launch { model.ensure(downloadUrl = url, authToken = token) }
    }

    /**
     * Labels the stored rows that have no category yet.
     *
     * Only the uncategorised ones are sent: re-labelling a row the user corrected by hand would overwrite
     * their answer with the model's, and their answer is the better one.
     */
    fun categorizeExisting() {
        if (progress.value != null) return
        val pending = store.transactions.value.filter { it.amount < 0 && it.categoryOrNull == null }
        if (pending.isEmpty()) return

        viewModelScope.launch {
            progress.value = 0f
            try {
                val labelled = categorizer.categorize(pending) { progress.value = it }
                labelled.filter { it.categoryOrNull != null }.forEach(store::update)
            } finally {
                // In a finally: a cancelled or failed run must not leave the UI stuck on a progress bar.
                progress.value = null
            }
        }
    }

    /** Deletes every transaction. The screen confirms first — there is no undo behind this. */
    fun deleteAll() = store.clear()

    fun update(transaction: Transaction) = store.update(transaction)

    fun remove(id: String) = store.remove(id)

    suspend fun addBlank(date: LocalDate): String = store.addBlank(date)
}
