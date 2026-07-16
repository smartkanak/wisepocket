package date.oxi.wisepocket.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * A single bank-statement line item — parsed from an imported PDF, or added by hand.
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
