package date.oxi.wisepocket.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import date.oxi.wisepocket.insights.Categorizer
import date.oxi.wisepocket.llm.LlmProvider
import date.oxi.wisepocket.model.Transaction
import date.oxi.wisepocket.statement.ImportResult
import date.oxi.wisepocket.statement.ParseConfidence
import date.oxi.wisepocket.statement.ParsedTransaction
import date.oxi.wisepocket.statement.StatementImporter
import date.oxi.wisepocket.statement.StatementProfile
import date.oxi.wisepocket.transactions.TransactionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ImportUiState {
    data object Idle : ImportUiState
    data object Reading : ImportUiState
    data class Failed(val message: String) : ImportUiState

    /**
     * Labelling the rows with the on-device model. Its own step, and blocking, on purpose: this takes
     * seconds, and letting the user start reviewing rows that then relabel themselves underneath them
     * would undermine the one thing the review step is for.
     *
     * @param progress 0..1 across the batches.
     */
    data class Categorizing(val progress: Float) : ImportUiState

    /** Parsed and ready for the user to check. [rows] is editable — the parser's output is a proposal. */
    data class Review(
        val profile: StatementProfile,
        val rows: List<ParsedTransaction>,
        val skippedLines: List<String> = emptyList(),
    ) : ImportUiState {
        val spent: Double get() = rows.sumOf { if (it.transaction.amount < 0) -it.transaction.amount else 0.0 }
        val income: Double get() = rows.sumOf { if (it.transaction.amount > 0) it.transaction.amount else 0.0 }
        val needsReview: Int get() = rows.count { it.confidence == ParseConfidence.NEEDS_REVIEW }
    }
}

/**
 * Drives one import: read the PDF, hold the proposed rows while the user checks them, then hand them to
 * [TransactionStore] — or throw them away.
 *
 * The rows live here, not in the store, until confirmed. That separation is the point: an import is a
 * proposal, and a parser that misreads a bank we've never seen shouldn't be able to quietly corrupt data
 * the user already trusts.
 */
class ImportViewModel(
    // The importer is built around the app's single engine (see appModule), so a statement that can't
    // self-verify gets the model's judgement on its conventions instead of a hardcoded guess.
    private val importer: StatementImporter,
    private val categorizer: Categorizer,
    private val transactionStore: TransactionStore,
    private val llm: LlmProvider,
) : ViewModel() {

    init {
        // Kick off provisioning: import may be the first screen the user touches, and the LLM step needs
        // a loaded engine. No-op if the chat already started it.
        viewModelScope.launch { llm.ensure() }
    }

    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    fun import(bytes: ByteArray) {
        _state.value = ImportUiState.Reading
        viewModelScope.launch {
            _state.value = when (val result = importer.import(bytes)) {
                is ImportResult.Success -> ImportUiState.Review(
                    profile = result.result.profile,
                    rows = categorize(result.result.transactions),
                    skippedLines = result.result.skippedLines,
                )

                ImportResult.NoTransactions -> ImportUiState.Failed(
                    "No transactions found in this PDF. If it's a scan, WisePocket can't read it yet.",
                )

                is ImportResult.Failed -> ImportUiState.Failed(result.message)
            }
        }
    }

    /**
     * Labels the parsed rows, keeping each row's parse metadata (confidence, source line) attached.
     *
     * Categories ride along on [ParsedTransaction.transaction] rather than replacing the list, because
     * confidence is about *the parse* — a category the model guessed badly is a wrong label on a
     * correctly-read row, and conflating the two would flag rows whose numbers are fine.
     */
    private suspend fun categorize(rows: List<ParsedTransaction>): List<ParsedTransaction> {
        _state.value = ImportUiState.Categorizing(progress = 0f)
        // Wait for the engine the init block started. Without this, a PDF picked before the model finished
        // loading would come back silently uncategorised — the race would look exactly like "no model".
        // Idempotent, and returns promptly when there's genuinely nothing to load.
        llm.ensure()
        val labelled = categorizer.categorize(
            transactions = rows.map { it.transaction },
            onProgress = { _state.value = ImportUiState.Categorizing(progress = it) },
        )
        return rows.zip(labelled) { row, transaction -> row.copy(transaction = transaction) }
    }

    fun updateRow(index: Int, transaction: Transaction) = editRows { rows ->
        // Bounds-checked: the row index comes from a list the user is editing while it changes under them.
        val existing = rows.getOrNull(index) ?: return@editRows
        rows[index] = existing.copy(transaction = transaction, confidence = ParseConfidence.HIGH)
    }

    fun deleteRow(index: Int) = editRows { rows ->
        if (index in rows.indices) rows.removeAt(index)
    }

    /** Accepts the reviewed rows into the user's data and closes the import. */
    fun confirm() {
        val review = _state.value as? ImportUiState.Review ?: return
        transactionStore.addAll(review.rows.map { it.transaction })
        _state.value = ImportUiState.Idle
    }

    /** Throws the import away. Nothing reached [TransactionStore], so there's nothing to undo. */
    fun discard() {
        _state.value = ImportUiState.Idle
    }

    private inline fun editRows(edit: (MutableList<ParsedTransaction>) -> Unit) {
        val review = _state.value as? ImportUiState.Review ?: return
        val rows = review.rows.toMutableList()
        edit(rows)
        _state.value = review.copy(rows = rows)
    }
}
