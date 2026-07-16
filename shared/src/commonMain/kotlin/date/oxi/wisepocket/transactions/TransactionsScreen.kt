package date.oxi.wisepocket.transactions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import date.oxi.wisepocket.llm.ModelStatus
import date.oxi.wisepocket.model.Transaction
import date.oxi.wisepocket.model.formatMoney
import date.oxi.wisepocket.ui.theme.Mono

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

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            SummaryHeader(transactions, onOpenWrapped = onOpenWrapped, onDeleteAll = { confirmDeleteAll = true })
            StatusRow(state, onCategorize = onCategorize, onSetUpModel = onSetUpModel)
            HorizontalDivider()

            if (transactions.isEmpty()) {
                EmptyState(onImport = onImport, onAdd = onAdd)
                return@Column
            }

            // The nav-bar inset is spent here, as content padding, not baked into the frame — so the list
            // reaches the bottom edge and the last row just scrolls clear of the navigation bar, instead of
            // the whole list ending at a fixed dead band above it.
            val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 64.dp + navBottom),
                verticalArrangement = Arrangement.spacedBy(12.dp),
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
        }

        if (transactions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FloatingActionButton(
                    onClick = onImport,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Filled.DocumentScanner,
                        contentDescription = "Import PDF"
                    )
                }
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add"
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Add", style = MaterialTheme.typography.labelLarge)
                }
            }
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
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
            )
            Text(
                "Sorting your spending into categories…",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        state.modelStatus is ModelStatus.Downloading -> Banner {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape))
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
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), content = content)
    }
}

@Composable
private fun Action(text: String, button: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("⚡", style = MaterialTheme.typography.bodyMedium)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Button(
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.height(32.dp)
        ) {
            Text(button, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SummaryHeader(
    transactions: List<Transaction>,
    onOpenWrapped: () -> Unit,
    onDeleteAll: () -> Unit
) {
    val spent = transactions.filter { it.amount < 0 }.sumOf { -it.amount }
    val income = transactions.filter { it.amount > 0 }.sumOf { it.amount }
    val net = income - spent

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Transactions",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = onOpenWrapped,
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CardGiftcard,
                        contentDescription = "Wrapped",
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Text("Wrapped", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
                if (transactions.isNotEmpty()) OverflowMenu(onDeleteAll)
            }
            val range = transactions.map { it.date }.sorted()
                .let { dates -> dates.firstOrNull()?.let { " · $it – ${dates.last()}" } }
                .orEmpty()
            Text(
                "${transactions.size} transaction${if (transactions.size == 1) "" else "s"}$range",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Figure("Spent", formatMoney(spent), Modifier.weight(1f))
                Figure("Income", formatMoney(income), Modifier.weight(1f), MaterialTheme.colorScheme.tertiary)
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

@Composable
private fun Figure(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium.merge(Mono),
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun EmptyState(onImport: () -> Unit, onAdd: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Surface(
                modifier = Modifier.padding(bottom = 8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = CircleShape,
            ) {
                Box(modifier = Modifier.padding(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.CreditCard,
                        contentDescription = "Transactions list empty",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                "No transactions yet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                "Import a bank statement PDF to get started. It's read on your device — nothing is uploaded.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onImport,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Import PDF Statement")
            }
            OutlinedButton(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Add transaction manually")
            }
        }
    }
}
