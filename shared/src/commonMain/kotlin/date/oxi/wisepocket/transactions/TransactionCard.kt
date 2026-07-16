package date.oxi.wisepocket.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import date.oxi.wisepocket.model.Transaction
import date.oxi.wisepocket.model.formatSignedMoney
import kotlinx.datetime.LocalDate

/**
 * One transaction, tap to edit. Shared by the transaction list and the import dialog so a row looks and
 * behaves the same wherever it appears.
 *
 * @param flagged draws attention to a row the parser wasn't confident about.
 * @param sourceLine the statement line this came from, shown while editing so the user can check the
 *   parse without reopening the PDF. Null for hand-added rows.
 */
@Composable
fun TransactionCard(
    transaction: Transaction,
    onUpdate: (Transaction) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    flagged: Boolean = false,
    sourceLine: String? = null,
    initiallyExpanded: Boolean = false,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val tx = transaction

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = if (flagged && !expanded) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(
            Modifier.clickable { expanded = !expanded }.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        tx.merchant.ifBlank { "(no description)" },
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = if (expanded) Int.MAX_VALUE else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        tx.date.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "${formatSignedMoney(tx.amount)} ${tx.currency}",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (tx.amount < 0) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }

            if (expanded) {
                HorizontalDivider()
                Editor(tx, onUpdate)
                if (!sourceLine.isNullOrBlank()) {
                    Text(
                        "From the statement:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        sourceLine,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { expanded = false }) { Text("Done") }
                    TextButton(onClick = onDelete) { Text("Delete") }
                }
            }
        }
    }
}

@Composable
private fun Editor(tx: Transaction, onUpdate: (Transaction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = tx.merchant,
            onValueChange = { onUpdate(tx.copy(merchant = it)) },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // The raw text is kept while typing: parsing every keystroke would fight the user over
            // intermediate states like "-" or "12.".
            var amountText by remember(tx.id) { mutableStateOf(tx.amount.toString()) }
            OutlinedTextField(
                value = amountText,
                onValueChange = { text ->
                    amountText = text
                    text.toDoubleOrNull()?.let { onUpdate(tx.copy(amount = it)) }
                },
                label = { Text("Amount") },
                supportingText = { Text("negative = spent") },
                singleLine = true,
                isError = amountText.toDoubleOrNull() == null,
                modifier = Modifier.weight(1f),
            )
            var dateText by remember(tx.id) { mutableStateOf(tx.date.toString()) }
            OutlinedTextField(
                value = dateText,
                onValueChange = { text ->
                    dateText = text
                    runCatching { LocalDate.parse(text) }.getOrNull()?.let { onUpdate(tx.copy(date = it)) }
                },
                label = { Text("Date") },
                supportingText = { Text("YYYY-MM-DD") },
                singleLine = true,
                isError = runCatching { LocalDate.parse(dateText) }.getOrNull() == null,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
