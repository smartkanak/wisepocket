package date.oxi.wisepocket.review

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
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
                is ImportUiState.Categorizing -> CategorizingPanel(state.progress, onDiscard)
                is ImportUiState.Failed -> FailedPanel(state.message, onRetry, onDiscard)
                is ImportUiState.Review -> ReviewPanel(state, onUpdate, onDelete, onConfirm, onDiscard)
                is ImportUiState.Idle -> Unit
            }
        }
    }
}

@Composable
private fun ReadingPanel(onCancel: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(56.dp),
                strokeWidth = 4.dp
            )
            Text(
                "Reading the statement…",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                "Working out this bank's layout on your device. Everything remains offline.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onCancel, shape = MaterialTheme.shapes.medium) {
                Text("Cancel")
            }
        }
    }
}

/**
 * The one slow step with a knowable end, so it gets a real progress bar rather than a spinner: the work is
 * a fixed number of batches and the user is waiting on an on-device model, which is worth naming.
 */
@Composable
private fun CategorizingPanel(progress: Float, onCancel: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
            )
            Text(
                "Sorting into categories…",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                "The on-device AI is labelling your spending. Nothing leaves your phone.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onCancel, shape = MaterialTheme.shapes.medium) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun FailedPanel(message: String, onRetry: () -> Unit, onDiscard: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("🚨", style = MaterialTheme.typography.displayMedium)
            Text("Couldn't import statement", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Try another PDF")
            }
            OutlinedButton(
                onClick = onDiscard,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Close")
            }
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
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "Review Import",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${state.rows.size} transactions · spent ${formatMoney(state.spent)} · " +
                        "income ${formatMoney(state.income)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TrustNote(state)
                if (state.skippedLines.isNotEmpty()) {
                    Text(
                        "${state.skippedLines.size} row(s) couldn't be read and were left out.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
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
            OutlinedButton(
                onClick = onDiscard,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Discard")
            }
            Button(
                onClick = onConfirm,
                enabled = state.rows.isNotEmpty(),
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Add ${state.rows.size}")
            }
        }
    }
}

/** How the layout was worked out decides how much to trust it — so say which, rather than implying proof. */
@Composable
private fun TrustNote(state: ImportUiState.Review) {
    val (text, color, icon) = when (state.profile.source) {
        StatementProfile.Source.RECONCILED -> Triple(
            "Totals match the balance printed on the statement.",
            MaterialTheme.colorScheme.tertiary,
            "✓"
        )

        StatementProfile.Source.LLM -> Triple(
            "Read by the on-device AI — worth a skim.",
            MaterialTheme.colorScheme.secondary,
            "🤖"
        )

        StatementProfile.Source.INFERRED -> Triple(
            "This statement prints no balance, so nothing could double-check the read. " +
                "${state.needsReview} row(s) marked — please skim them.",
            MaterialTheme.colorScheme.error,
            "⚠️"
        )
    }
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(icon, style = MaterialTheme.typography.bodyMedium, color = color)
            Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.weight(1f))
        }
    }
}
