package date.oxi.wisepocket.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import date.oxi.wisepocket.model.Transaction
import date.oxi.wisepocket.model.formatMoney
import date.oxi.wisepocket.statement.ParseConfidence
import date.oxi.wisepocket.statement.StatementProfile
import date.oxi.wisepocket.transactions.TransactionCard

/**
 * Import shown as a temporary sheet over the transaction list: check what was read, fix or drop rows, then
 * keep the lot or discard it.
 *
 * Nothing reaches the user's data until they say so. A parser can misread a row, and the one on screen
 * that came out wrong is far easier to fix here — next to the statement line it came from — than to find
 * later among hundreds of rows.
 */
@Composable
fun ImportDialog(
    state: ImportUiState,
    onUpdate: (Int, Transaction) -> Unit,
    onDelete: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDiscard: () -> Unit,
    onRetry: () -> Unit,
) {
    if (state is ImportUiState.Idle) return

    Dialog(
        onDismissRequest = onDiscard,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize()) {
            when (state) {
                is ImportUiState.Reading -> ReadingPanel(onDiscard)
                is ImportUiState.Failed -> FailedPanel(state.message, onRetry, onDiscard)
                is ImportUiState.Review -> ReviewPanel(state, onUpdate, onDelete, onConfirm, onDiscard)
                is ImportUiState.Idle -> Unit
            }
        }
    }
}

@Composable
private fun ReadingPanel(onCancel: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator()
            Text("Reading the statement…", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Working out this bank's layout on your device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

@Composable
private fun FailedPanel(message: String, onRetry: () -> Unit, onDiscard: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Couldn't import", style = MaterialTheme.typography.titleMedium)
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error,
            )
            Button(onClick = onRetry) { Text("Try another PDF") }
            TextButton(onClick = onDiscard) { Text("Close") }
        }
    }
}

@Composable
private fun ReviewPanel(
    state: ImportUiState.Review,
    onUpdate: (Int, Transaction) -> Unit,
    onDelete: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDiscard: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Review import", style = MaterialTheme.typography.titleLarge)
                Text(
                    "${state.rows.size} transactions · spent ${formatMoney(state.spent)} · " +
                        "income ${formatMoney(state.income)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                TrustNote(state)
                if (state.skippedLines.isNotEmpty()) {
                    Text(
                        "${state.skippedLines.size} row(s) couldn't be read and were left out.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        HorizontalDivider()

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(state.rows, key = { _, row -> row.transaction.id }) { index, row ->
                TransactionCard(
                    transaction = row.transaction,
                    onUpdate = { onUpdate(index, it) },
                    onDelete = { onDelete(index) },
                    flagged = row.confidence == ParseConfidence.NEEDS_REVIEW,
                    sourceLine = row.sourceLine,
                )
            }
        }

        HorizontalDivider()
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onDiscard, modifier = Modifier.weight(1f)) { Text("Discard") }
            Button(
                onClick = onConfirm,
                enabled = state.rows.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Text("Add ${state.rows.size}")
            }
        }
    }
}

/** How the layout was worked out decides how much to trust it — so say which, rather than implying proof. */
@Composable
private fun TrustNote(state: ImportUiState.Review) {
    when (state.profile.source) {
        StatementProfile.Source.RECONCILED -> Text(
            "✓ Totals match the balance printed on the statement.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )

        StatementProfile.Source.LLM -> Text(
            "Read by the on-device AI — worth a skim.",
            style = MaterialTheme.typography.bodySmall,
        )

        StatementProfile.Source.INFERRED -> Text(
            "This statement prints no balance, so nothing could double-check the read. " +
                "${state.needsReview} row(s) marked — please skim them.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
