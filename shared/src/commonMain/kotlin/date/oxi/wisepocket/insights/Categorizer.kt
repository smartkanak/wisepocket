package date.oxi.wisepocket.insights

import date.oxi.wisepocket.llm.LlmEngine
import date.oxi.wisepocket.llm.Sampling
import date.oxi.wisepocket.model.Category
import date.oxi.wisepocket.model.Transaction
import kotlin.time.TimeSource

private const val LOG = "WP-CAT"

/**
 * Labels spending with a [Category] using the on-device model.
 *
 * Three properties keep a non-deterministic step from producing numbers that don't add up:
 *
 * - **It asks about merchants, not transactions.** A statement names the same supermarket a dozen times;
 *   asking once per *distinct* merchant cuts the prompt down (inference here is prefill-bound, so cost
 *   tracks input length) and, more importantly, makes the answer self-consistent — the same merchant
 *   cannot land in two categories and split its own total in half.
 * - **The answer is closed.** Only names in [Category] are accepted; invented ones are dropped, and that
 *   row stays null rather than inventing a bucket that the totals would then have to carry.
 * - **It can't fail the import.** No model on disk, a refusal, a garbled reply — each leaves rows
 *   uncategorised, which is exactly where they'd be without this step anyway.
 *
 * Income is left alone: its sign already says what it is.
 */
class Categorizer(private val engineProvider: () -> LlmEngine?) {

    /**
     * Returns [transactions] with spending rows labelled where the model gave a usable answer — same
     * rows, same order, so callers can zip the result back onto whatever they track alongside them.
     *
     * @param onProgress fraction in 0..1, called as batches complete — this takes seconds on-device, so
     *   the UI needs something to show.
     */
    suspend fun categorize(
        transactions: List<Transaction>,
        onProgress: (Float) -> Unit = {},
    ): List<Transaction> {
        val engine = engineProvider() ?: run {
            println("$LOG no engine — leaving ${transactions.size} rows uncategorised")
            return transactions
        }

        // Distinct spending merchants only. Keyed case-insensitively so "REWE" and "Rewe" ask once.
        val merchants = transactions
            .filter { it.amount < 0 && it.merchant.isNotBlank() }
            .map { it.merchant }
            .distinctBy { it.trim().uppercase() }
        if (merchants.isEmpty()) return transactions

        val started = TimeSource.Monotonic.markNow()
        val labels = mutableMapOf<String, Category>()
        val batches = merchants.chunked(BATCH_SIZE)
        batches.forEachIndexed { index, batch ->
            labels += askBatch(engine, batch)
            onProgress((index + 1).toFloat() / batches.size)
        }
        println(
            "$LOG labelled ${labels.size}/${merchants.size} merchants " +
                "in ${batches.size} batches, ${started.elapsedNow()}",
        )

        return transactions.map { transaction ->
            if (transaction.amount >= 0) return@map transaction
            val category = labels[transaction.merchant.trim().uppercase()] ?: return@map transaction
            transaction.copy(category = category.name)
        }
    }

    /** One round-trip. Returns uppercased-merchant → category for the ones that came back usable. */
    private suspend fun askBatch(engine: LlmEngine, batch: List<String>): Map<String, Category> {
        val numbered = batch
            .mapIndexed { i, merchant -> "${i + 1}. ${merchant.trim().take(MERCHANT_CHARS)}" }
            .joinToString("\n")

        val reply = StringBuilder()
        runCatching {
            engine.generate(system = SYSTEM, context = numbered, user = USER, sampling = Sampling.PRECISE)
                .collect { reply.append(it) }
        }.onFailure {
            println("$LOG batch failed: ${it.message}")
            return emptyMap()
        }

        val labels = readLabels(reply.toString(), batch)
        // The model's answer, always — "4/12 labelled" and nothing else made a parser that was silently
        // discarding good replies look identical to a model that couldn't categorise, and we spent a long
        // time blaming the wrong one. Logging only on a parse *failure* hides the other half: a reply that
        // parses perfectly and is nonsense.
        //
        // The reply only — never `numbered`, which is the user's actual merchants. Debug output is not a
        // place to make an exception to "nothing leaves the phone"; a category list gives up nothing.
        println(
            "$LOG ${labels.size}/${batch.size} parsed | reply: " +
                reply.toString().take(REPLY_PREVIEW_CHARS).replace("\n", " ⏎ "),
        )
        return labels
    }

    /**
     * Reads a category per line out of the reply.
     *
     * **Anchored on the category name, not on the position of the answer** — and that distinction was the
     * single biggest cause of merchants coming back unlabelled. The first version matched
     * `(\d+)[:.] (\w+)`, i.e. "the first word after the number", which only ever works if the model replies
     * in exactly the terse form we asked for. Small models very often echo their input first —
     * `1. AMZN Mktp DE - SHOPPING` — and then that regex reads the word `AMZN`, fails to resolve it, and
     * discards a perfectly good answer. Measured: four of six realistic reply shapes yielded *zero* labels.
     * Worse, echoing is what a model does with names it **recognises**, so the failure fell hardest on the
     * merchants it actually knew.
     *
     * The echoed merchant is removed before scanning, using the name we sent for that index. Without that,
     * a merchant whose own name contains a category word ("TRAVEL AGENCY XY") would answer for itself.
     * The **last** remaining mention wins: the answer follows the echo.
     *
     * Still unforgiving about content — an index outside the batch, or a word that isn't a [Category], is
     * dropped and the row stays null.
     */
    internal fun readLabels(reply: String, batch: List<String>): Map<String, Category> {
        val labelled = reply.lines().mapNotNull { line -> readLine(line, batch) }
        if (labelled.isNotEmpty()) return labelled.toMap()

        // Nothing carried an index. A model that answers with a bare list in order is still answering, so
        // take it positionally — but only when the count matches exactly, or we'd be pairing up at random.
        val bare = reply.lines()
            .mapNotNull { line -> Category.entries.lastOrNull { line.contains(it.name, ignoreCase = true) } }
        if (bare.size != batch.size) return emptyMap()
        return batch.mapIndexed { i, merchant -> merchant.trim().uppercase() to bare[i] }.toMap()
    }

    private fun readLine(line: String, batch: List<String>): Pair<String, Category>? {
        val index = LEADING_INDEX.find(line)?.groupValues?.get(1)?.toIntOrNull()?.minus(1) ?: return null
        val merchant = batch.getOrNull(index) ?: return null
        // Strip the echo so the merchant's own name can't be mistaken for the answer.
        val answer = line.replace(merchant.trim(), " ", ignoreCase = true)
        val category = Category.entries.lastOrNull { answer.contains(it.name, ignoreCase = true) }
        return category?.let { merchant.trim().uppercase() to it }
    }

    private companion object {
        /** Enough merchants to amortise the prompt, few enough to stay well inside the 256-token answer cap. */
        const val BATCH_SIZE = 12

        /** Merchant lines carry booking refs and timestamps; the name is at the front. Cap the prefill. */
        const val MERCHANT_CHARS = 48

        /** Enough of a bad reply to see what shape it was, not so much that it floods the log. */
        const val REPLY_PREVIEW_CHARS = 300

        val LEADING_INDEX = Regex("""^\W*(\d+)\s*[:.)\-]""")

        const val USER = "Categorise each merchant."

        /**
         * Two deliberate choices, neither of which is knowledge about any particular shop:
         *
         * - **It is told to guess.** The old wording — "if a merchant fits none of them, answer OTHER" —
         *   offered OTHER as a resting place, and a small model takes an easy out when handed one. A bank
         *   statement is abbreviations and legal entities; "most likely" is the only useful standard, and a
         *   wrong guess is one visibly wrong chip, not a corrupted total.
         * - **A worked example.** Format compliance is what small models fail at, and one example does more
         *   than any amount of describing the format. The example merchant is invented and deliberately
         *   cryptic, to demonstrate guessing from a fragment rather than recognition.
         */
        val SYSTEM: String = buildString {
            append("You categorise bank-statement merchants. /no_think\n")
            append("For each numbered merchant, reply with one line: the number, a colon, and the category.\n")
            append("Categories (use these exact words):\n")
            Category.entries.forEach { append("  ${it.name} — ${it.hint}\n") }
            append("\nAlways choose the single most likely category. Statement lines are abbreviated, ")
            append("truncated and full of company suffixes — infer from any part of the name you recognise, ")
            append("and guess if you must. Answer OTHER only when the name tells you nothing at all.\n\n")
            append("Example:\n")
            append("Input:\n1. KFZ-WERKSTATT SCHMIDT GMBH\n2. Blossom & Bean Ltd\n")
            append("Output:\n1:TRANSPORT\n2:RESTAURANTS")
        }
    }
}
