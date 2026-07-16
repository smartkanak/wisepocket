package date.oxi.wisepocket.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import date.oxi.wisepocket.model.Transaction
import date.oxi.wisepocket.model.formatMoney

/**
 * The app's home: your transactions. Everything here is editable — tap a row to change it, add one by
 * hand, or pull a statement in. Importing is a way to fill this list, not a destination of its own.
 */
@Composable
fun TransactionsScreen(
    transactions: List<Transaction>,
    onUpdate: (Transaction) -> Unit,
    onDelete: (String) -> Unit,
    onAdd: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
    newlyAddedId: String? = null,
) {
    Column(modifier.fillMaxSize()) {
        SummaryHeader(transactions)
        HorizontalDivider()

        if (transactions.isEmpty()) {
            EmptyState(onImport = onImport, onAdd = onAdd)
            return@Column
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(transactions, key = { it.id }) { tx ->
                TransactionCard(
                    transaction = tx,
                    onUpdate = onUpdate,
                    onDelete = { onDelete(tx.id) },
                    // A row the user just created starts open — it's blank, so it needs filling in.
                    initiallyExpanded = tx.id == newlyAddedId,
                )
            }
        }

        HorizontalDivider()
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Short labels: "Add transaction" wraps to two lines at this width on a phone.
            OutlinedButton(onClick = onAdd, modifier = Modifier.weight(1f)) { Text("Add") }
            Button(onClick = onImport, modifier = Modifier.weight(1f)) { Text("Import PDF") }
        }
    }
}

@Composable
private fun SummaryHeader(transactions: List<Transaction>) {
    val spent = transactions.filter { it.amount < 0 }.sumOf { -it.amount }
    val income = transactions.filter { it.amount > 0 }.sumOf { it.amount }
    val net = income - spent

    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Your transactions", style = MaterialTheme.typography.titleLarge)
            // Both ends come from one pass over a list we've already checked is non-empty, so there is no
            // nullable case left to force with `!!`.
            val range = transactions.map { it.date }.sorted()
                .let { dates -> dates.firstOrNull()?.let { " · $it – ${dates.last()}" } }
                .orEmpty()
            Text(
                "${transactions.size} transaction${if (transactions.size == 1) "" else "s"}$range",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Figure("Spent", formatMoney(spent), Modifier.weight(1f))
                Figure("Income", formatMoney(income), Modifier.weight(1f))
                Figure(
                    label = if (net >= 0) "Saved" else "Overspent",
                    value = formatMoney(if (net >= 0) net else -net),
                    modifier = Modifier.weight(1f),
                    highlight = true,
                )
            }
        }
    }
}

@Composable
private fun Figure(label: String, value: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun EmptyState(onImport: () -> Unit, onAdd: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("No transactions yet", style = MaterialTheme.typography.titleMedium)
            Text(
                "Import a bank statement PDF to get started. It's read on your device — nothing is uploaded.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onImport) { Text("Import PDF") }
            OutlinedButton(onClick = onAdd) { Text("Add one manually") }
        }
    }
}
