package date.oxi.wisepocket

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import date.oxi.wisepocket.chat.ChatScreen
import date.oxi.wisepocket.pdf.rememberPdfPicker
import date.oxi.wisepocket.review.ImportDialog
import date.oxi.wisepocket.review.ImportViewModel
import date.oxi.wisepocket.transactions.TransactionStore
import date.oxi.wisepocket.transactions.TransactionsScreen
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Today, in the user's own timezone — a new row should be dated now, not at some fixed point. */
@OptIn(ExperimentalTime::class)
private fun today() = Clock.System.todayIn(TimeZone.currentSystemDefault())

@Composable
@Preview
fun App() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val importViewModel: ImportViewModel = viewModel { ImportViewModel() }
            val importState by importViewModel.state.collectAsState()
            val transactions by TransactionStore.transactions.collectAsState()
            var tab by remember { mutableStateOf(0) }
            var newlyAddedId by remember { mutableStateOf<String?>(null) }

            val pickPdf = rememberPdfPicker { bytes ->
                if (bytes != null) importViewModel.import(bytes)
            }

            Column(Modifier.fillMaxSize().safeContentPadding()) {
                PrimaryTabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Transactions") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Chat") })
                }
                when (tab) {
                    0 -> TransactionsScreen(
                        transactions = transactions,
                        onUpdate = TransactionStore::update,
                        onDelete = TransactionStore::remove,
                        onAdd = {
                            newlyAddedId = TransactionStore.addBlank(today())
                        },
                        onImport = pickPdf,
                        newlyAddedId = newlyAddedId,
                    )
                    // Falls back to the mock statement while the user has no data of their own, so the
                    // chat still has something to talk about on a fresh install.
                    else -> ChatScreen(transactions = transactions)
                }
            }

            // Import sits over the list rather than beside it: it's a task with an end, not a place.
            ImportDialog(
                state = importState,
                onUpdate = importViewModel::updateRow,
                onDelete = importViewModel::deleteRow,
                onConfirm = importViewModel::confirm,
                onDiscard = importViewModel::discard,
                onRetry = {
                    importViewModel.discard()
                    pickPdf()
                },
            )
        }
    }
}
