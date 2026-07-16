package date.oxi.wisepocket.model

/**
 * The categories WisePocket sorts spending into — a **closed** set, deliberately.
 *
 * The whole point of categorising is that amounts add up per category, and free-form labels don't add up:
 * the same supermarket comes back as "Groceries", "Lebensmittel" and "Supermarkt", and the totals silently
 * split three ways. So the model chooses from this list and nothing else; anything it invents is rejected
 * (see [date.oxi.wisepocket.insights.Categorizer]).
 *
 * Income isn't in here. The sign of the amount already says money came in, and asking the model to
 * re-derive something the data states outright is how it gets a chance to contradict it.
 *
 * Stored as [name] in the existing `category` text column, so adding this needs no Room migration.
 * That also means **renaming a constant is a data migration** — old rows would stop resolving. Add new
 * ones at the end instead.
 *
 * @param label how the category reads in the UI.
 * @param emoji a glyph for the Wrapped screens and list rows.
 * @param hint what the category means, as told to the model. It lives here rather than in the prompt so
 *   the definition and the label can't drift apart into meaning two different things.
 */
enum class Category(
    val label: String,
    val emoji: String,
    val hint: String,
) {
    GROCERIES("Groceries", "🛒", "supermarkets, food shops, bakeries"),
    RESTAURANTS("Restaurants", "🍽️", "restaurants, cafés, bars, food delivery, takeaway"),
    TRANSPORT("Transport", "🚊", "fuel, public transport, taxis, parking, car costs"),
    HOUSING("Housing", "🏠", "rent, mortgage, electricity, water, heating, internet, phone"),
    SHOPPING("Shopping", "🛍️", "clothes, electronics, furniture, general retail, online marketplaces"),
    HEALTH("Health", "💊", "pharmacy, doctors, insurance, gym, personal care"),
    ENTERTAINMENT("Entertainment", "🎬", "cinema, concerts, games, books, hobbies, sport events"),
    SUBSCRIPTIONS("Subscriptions", "🔁", "recurring digital services: streaming, software, memberships"),
    TRAVEL("Travel", "✈️", "flights, hotels, trains for trips, holiday bookings"),
    FEES("Fees", "🏦", "bank charges, account fees, interest, taxes"),
    OTHER("Other", "❓", "anything that clearly fits none of the above"),
    ;

    companion object {
        /** Resolves a stored or model-supplied name, tolerating case and stray whitespace. Null if unknown. */
        fun fromNameOrNull(raw: String?): Category? {
            val key = raw?.trim()?.uppercase() ?: return null
            return entries.firstOrNull { it.name == key }
        }
    }
}

/** The category of this transaction, or null when it was never labelled (or the label no longer resolves). */
val Transaction.categoryOrNull: Category? get() = Category.fromNameOrNull(category)
