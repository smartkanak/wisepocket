package date.oxi.wisepocket.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * A single bank-statement line item. For this MVP slice these come from [date.oxi.wisepocket.data.MockTransactions];
 * a later slice will populate them from parsed PDF statements.
 *
 * @param amount negative for spending (debits), positive for income (credits), in [currency] major units.
 */
@Serializable
data class Transaction(
    val id: String,
    val date: LocalDate,
    val merchant: String,
    val amount: Double,
    val currency: String = "EUR",
    val category: String? = null,
)
