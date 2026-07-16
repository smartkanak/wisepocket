package date.oxi.wisepocket.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import date.oxi.wisepocket.model.Category
import date.oxi.wisepocket.model.Transaction
import date.oxi.wisepocket.model.categoryOrNull
import date.oxi.wisepocket.model.formatSignedMoney
import date.oxi.wisepocket.ui.theme.Mono
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
                tx.categoryOrNull?.let {
                    Text(it.emoji, Modifier.padding(end = 10.dp), style = MaterialTheme.typography.titleMedium)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        tx.merchant.ifBlank { "(no description)" },
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = if (expanded) Int.MAX_VALUE else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(tx.date.toString(), tx.categoryOrNull?.label).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Money in is mint, money out is plain: most rows are spending, so colouring those would
                // colour everything and mean nothing. Tertiary, not primary — primary is white now.
                Text(
                    "${formatSignedMoney(tx.amount)} ${tx.currency}",
                    style = MaterialTheme.typography.titleSmall.merge(Mono),
                    color = if (tx.amount < 0) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.tertiary
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
                        style = MaterialTheme.typography.bodySmall.merge(Mono),
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
        CategoryPicker(tx, onUpdate)
    }
}

/**
 * Sets the row's [Category].
 *
 * Categories come from a model that guesses, so correcting one has to be as easy as reading it — and the
 * chips are the closed set itself, which means a hand-fixed row lands in exactly the same bucket the model
 * would have used. A text field here would reintroduce the free-form labels the whole design rules out.
 */
@Composable
private fun CategoryPicker(tx: Transaction, onUpdate: (Transaction) -> Unit) {
    val selected = tx.categoryOrNull
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Category",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Category.entries.forEach { category ->
                FilterChip(
                    selected = category == selected,
                    // Tapping the selected chip clears it: "I don't know" is a legitimate answer, and
                    // Insights reports uncategorised spending rather than hiding it.
                    onClick = {
                        onUpdate(tx.copy(category = if (category == selected) null else category.name))
                    },
                    label = { Text("${category.emoji} ${category.label}") },
                )
            }
        }
    }
}
