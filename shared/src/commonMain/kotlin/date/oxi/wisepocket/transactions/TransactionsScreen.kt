package date.oxi.wisepocket.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import date.oxi.wisepocket.llm.ModelStatus
import date.oxi.wisepocket.model.Transaction
import date.oxi.wisepocket.model.formatMoney

/**
 * The app's home: your transactions. Everything here is editable — tap a row to change it, add one by
 * hand, or pull a statement in. Importing is a way to fill this list, not a destination of its own.
 */
@Composable
fun TransactionsScreen(
    state: TransactionsUiState,
    onUpdate: (Transaction) -> Unit,
    onDelete: (String) -> Unit,
    onAdd: () -> Unit,
    onImport: () -> Unit,
    onCategorize: () -> Unit,
    onSetUpModel: () -> Unit,
    onOpenWrapped: () -> Unit,
    onDeleteAll: () -> Unit,
    modifier: Modifier = Modifier,
    newlyAddedId: String? = null,
) {
    val transactions = state.transactions
    var confirmDeleteAll by rememberSaveable { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        SummaryHeader(transactions, onDeleteAll = { confirmDeleteAll = true })
        StatusRow(state, onCategorize = onCategorize, onSetUpModel = onSetUpModel)
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
            OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text("Import") }
            // Wrapped is a thing you go and look at and then leave, so it's launched from here rather than
            // sitting in the tab bar pretending to be a place you live.
            Button(onClick = onOpenWrapped, modifier = Modifier.weight(1f)) { Text("Wrapped") }
        }
    }

    if (confirmDeleteAll) {
        ConfirmDeleteAll(
            count = transactions.size,
            onConfirm = {
                confirmDeleteAll = false
                onDeleteAll()
            },
            onDismiss = { confirmDeleteAll = false },
        )
    }
}

/**
 * Confirms wiping the list.
 *
 * Says the number rather than "all your transactions": a count is the one thing that makes someone notice
 * they're about to delete more than the month they were thinking of. There is no undo behind this and no
 * copy elsewhere, so the dialog says so plainly instead of implying a safety net that doesn't exist.
 */
@Composable
private fun ConfirmDeleteAll(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete all $count transaction${if (count == 1) "" else "s"}?") },
        text = {
            Text(
                "This clears the list so you can import a fresh month. Anything you edited or added by " +
                    "hand goes too, and it can't be undone.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete all", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * The one line that says what the app is currently unable to do, and offers the fix.
 *
 * It exists because the app's two "not ready" states used to be invisible: the model could only be obtained
 * from inside the chat, and rows imported before the model arrived stayed uncategorised with nothing
 * anywhere to sort them. Silence about that is what made the whole thing feel arbitrary — the app knew, and
 * didn't say.
 *
 * Shows nothing when there's nothing to act on. A permanent banner is just furniture.
 */
@Composable
private fun StatusRow(state: TransactionsUiState, onCategorize: () -> Unit, onSetUpModel: () -> Unit) {
    val progress = state.categorizingProgress
    when {
        progress != null -> Banner {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Text(
                "Sorting your spending into categories…",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        state.modelStatus is ModelStatus.Downloading -> Banner {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(
                "Downloading the on-device AI…",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        // Order matters: without a model, "12 rows uncategorised" is a complaint with no button behind it.
        !state.isModelReady && state.hasTransactions -> Banner {
            Action(
                text = "Categories and chat need the on-device AI.",
                button = "Set up",
                onClick = onSetUpModel,
            )
        }

        state.isModelReady && state.uncategorizedCount > 0 -> Banner {
            Action(
                text = "${state.uncategorizedCount} transaction${
                    if (state.uncategorizedCount == 1) " has" else "s have"
                } no category yet.",
                button = "Sort them out",
                onClick = onCategorize,
            )
        }
    }
}

@Composable
private fun Banner(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), content = content)
    }
}

@Composable
private fun Action(text: String, button: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        TextButton(onClick = onClick) { Text(button) }
    }
}

@Composable
private fun SummaryHeader(transactions: List<Transaction>, onDeleteAll: () -> Unit) {
    val spent = transactions.filter { it.amount < 0 }.sumOf { -it.amount }
    val income = transactions.filter { it.amount > 0 }.sumOf { it.amount }
    val net = income - spent

    // Deeper than the canvas, not lighter: the header is the thing the list scrolls *over*, so it reads as
    // set back rather than raised.
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Your transactions",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                // Tucked into a menu, not put next to "Add": an irreversible wipe must not sit at the same
                // weight as the everyday buttons, where a thumb finds it by accident.
                if (transactions.isNotEmpty()) OverflowMenu(onDeleteAll)
            }
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
                Figure("Income", formatMoney(income), Modifier.weight(1f), MaterialTheme.colorScheme.tertiary)
                // Saved and overspent are the same figure with opposite meanings, so colour is the only
                // thing that tells them apart at a glance — the label is read second, if at all.
                Figure(
                    label = if (net >= 0) "Saved" else "Overspent",
                    value = formatMoney(if (net >= 0) net else -net),
                    modifier = Modifier.weight(1f),
                    color = if (net >= 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun OverflowMenu(onDeleteAll: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Text("⋮", style = MaterialTheme.typography.titleLarge)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Delete all transactions", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    open = false
                    onDeleteAll()
                },
            )
        }
    }
}

/** One figure in the header. [color] defaults to plain body white — spending needs no colour to explain it. */
@Composable
private fun Figure(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.titleMedium, color = color)
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
