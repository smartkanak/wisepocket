package date.oxi.wisepocket.data

import date.oxi.wisepocket.model.Transaction
import kotlinx.datetime.LocalDate

/**
 * A hand-written sample bank statement so the chat slice can run without PDF ingestion.
 * Replaced by parsed statements in a later slice.
 */
object MockTransactions {
    val sample: List<Transaction> = listOf(
        Transaction("t1", LocalDate(2026, 6, 1), "Salary — Acme GmbH", 3200.00, category = "Income"),
        Transaction("t2", LocalDate(2026, 6, 2), "REWE Supermarket", -54.30, category = "Groceries"),
        Transaction("t3", LocalDate(2026, 6, 3), "Deutsche Bahn", -19.90, category = "Transport"),
        Transaction("t4", LocalDate(2026, 6, 5), "Netflix", -13.99, category = "Subscriptions"),
        Transaction("t5", LocalDate(2026, 6, 7), "Aldi", -37.81, category = "Groceries"),
        Transaction("t6", LocalDate(2026, 6, 9), "Shell Fuel", -62.40, category = "Transport"),
        Transaction("t7", LocalDate(2026, 6, 12), "Rent — Hausverwaltung", -1150.00, category = "Housing"),
        Transaction("t8", LocalDate(2026, 6, 14), "Amazon", -89.99, category = "Shopping"),
        Transaction("t9", LocalDate(2026, 6, 15), "Edeka", -41.20, category = "Groceries"),
        Transaction("t10", LocalDate(2026, 6, 18), "Spotify", -10.99, category = "Subscriptions"),
        Transaction("t11", LocalDate(2026, 6, 20), "Cafe Central", -8.50, category = "Dining"),
        Transaction("t12", LocalDate(2026, 6, 22), "Vodafone", -29.99, category = "Utilities"),
        Transaction("t13", LocalDate(2026, 6, 25), "Freelance invoice", 480.00, category = "Income"),
        Transaction("t14", LocalDate(2026, 6, 27), "IKEA", -156.00, category = "Shopping"),
        Transaction("t15", LocalDate(2026, 6, 29), "Lidl", -48.75, category = "Groceries"),
    )
}
