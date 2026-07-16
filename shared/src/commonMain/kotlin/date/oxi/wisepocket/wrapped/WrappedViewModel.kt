package date.oxi.wisepocket.wrapped

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import date.oxi.wisepocket.insights.Insights
import date.oxi.wisepocket.insights.WrappedCard
import date.oxi.wisepocket.insights.WrappedStory
import date.oxi.wisepocket.transactions.TransactionStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The story, kept in step with the user's data.
 *
 * Thin on purpose: everything interesting is in [WrappedStory] and [Insights], which are pure and tested.
 * This exists so the cards are computed once per data change rather than on every recomposition, and so the
 * screen stays a renderer — the same split that lets every figure the app shows be checked without a device.
 */
class WrappedViewModel(transactionStore: TransactionStore) : ViewModel() {

    val cards: StateFlow<List<WrappedCard>> = transactionStore.transactions
        .map { WrappedStory.from(Insights.from(it)) }
        // Lazily: unlike the chat, nothing needs the story until the tab is actually opened.
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}
