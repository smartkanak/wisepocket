package date.oxi.wisepocket

import date.oxi.wisepocket.insights.Categorizer
import date.oxi.wisepocket.llm.Sampling
import date.oxi.wisepocket.model.Category
import date.oxi.wisepocket.model.Transaction
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CategorizerTest {

    private fun row(merchant: String, amount: Double, day: Int = 1) =
        Transaction(id = "r$day-$merchant", date = LocalDate(2026, 3, day), merchant = merchant, amount = amount)

    private fun categorizer(reply: (String) -> String) = Categorizer { FakeLlmEngine(reply) }

    @Test
    fun labelsSpendingFromTheModelsReply() = runTest {
        val result = categorizer { "1:GROCERIES\n2:RESTAURANTS" }
            .categorize(listOf(row("REWE", -10.0), row("Pizzeria", -20.0)))

        assertEquals(Category.GROCERIES.name, result[0].category)
        assertEquals(Category.RESTAURANTS.name, result[1].category)
    }

    @Test
    fun withoutAnEngineRowsAreReturnedUntouched() = runTest {
        val rows = listOf(row("REWE", -10.0))
        // The honest consequence of LLM-only categorisation: no model on disk means no categories. It must
        // not mean a failed import.
        val result = Categorizer { null }.categorize(rows)

        assertEquals(rows, result)
        assertNull(result.single().category)
    }

    @Test
    fun inventedCategoriesAreRejected() = runTest {
        // Free-form labels are the failure this whole design exists to prevent: "Supermarkt" and
        // "GROCERIES" would be two buckets for one merchant, and the totals would split.
        val result = categorizer { "1:Supermarkt\n2:FOOD_AND_DRINK" }
            .categorize(listOf(row("REWE", -10.0), row("Pizzeria", -20.0)))

        assertTrue(result.all { it.category == null }, "invented labels leaked in: ${result.map { it.category }}")
    }

    @Test
    fun indicesOutsideTheBatchAreIgnored() = runTest {
        val result = categorizer { "1:GROCERIES\n7:TRAVEL\n0:FEES" }
            .categorize(listOf(row("REWE", -10.0)))

        assertEquals(Category.GROCERIES.name, result.single().category)
    }

    @Test
    fun promptShapeVariationsStillParse() = runTest {
        // Small models fence their output, renumber with ')', and preface it with a sentence. None of that
        // changes the answer, so none of it may change the parse.
        val result = categorizer {
            """
            Sure! Here are the categories:
            ```
            1) groceries
            2. TRANSPORT
            ```
            """.trimIndent()
        }.categorize(listOf(row("REWE", -10.0), row("Shell", -60.0)))

        assertEquals(Category.GROCERIES.name, result[0].category)
        assertEquals(Category.TRANSPORT.name, result[1].category)
    }

    @Test
    fun aReplyThatEchoesTheMerchantIsStillUnderstood() = runTest {
        // The failure that made real statements come back blank. Echoing is what a model does with a name
        // it *recognises*, so the old "first word after the number" parser lost precisely the merchants it
        // knew: it read "AMZN" as the answer and threw the line away.
        val result = categorizer {
            "1. ebay Kleinanzeigen GmbH - SHOPPING\n2. AMZN Mktp DE - SHOPPING"
        }.categorize(listOf(row("ebay Kleinanzeigen GmbH", -25.0), row("AMZN Mktp DE", -13.99)))

        assertTrue(result.all { it.category == Category.SHOPPING.name }, "got ${result.map { it.category }}")
    }

    @Test
    fun aMerchantNamedAfterACategoryDoesNotAnswerForItself() = runTest {
        // "TRAVEL AGENCY" echoed back contains a category word before the real answer. The echo is stripped
        // using the name we sent, so the merchant can't out-shout the model.
        val result = categorizer { "1. TRAVEL AGENCY MUELLER: SHOPPING" }
            .categorize(listOf(row("TRAVEL AGENCY MUELLER", -80.0)))

        assertEquals(Category.SHOPPING.name, result.single().category)
    }

    @Test
    fun aBareListInOrderIsAccepted() = runTest {
        // No indices at all, just answers. It's still an answer.
        val result = categorizer { "GROCERIES\nTRANSPORT" }
            .categorize(listOf(row("REWE", -10.0), row("Shell", -60.0)))

        assertEquals(Category.GROCERIES.name, result[0].category)
        assertEquals(Category.TRANSPORT.name, result[1].category)
    }

    @Test
    fun aBareListOfTheWrongLengthIsRefusedRatherThanGuessed() = runTest {
        // Pairing 1 answer onto 2 merchants would be inventing data. Better no category than a wrong one.
        val result = categorizer { "GROCERIES" }
            .categorize(listOf(row("REWE", -10.0), row("Shell", -60.0)))

        assertTrue(result.all { it.category == null }, "got ${result.map { it.category }}")
    }

    @Test
    fun categorisingAsksForGreedyDecoding() = runTest {
        val engine = FakeLlmEngine { "1:GROCERIES" }
        Categorizer { engine }.categorize(listOf(row("REWE", -10.0)))

        // Not a preference. Measured on-device: at the chat's temperature 0.7 the model answered FEES for
        // five of eleven merchants — Amazon, Shell and a pharmacy among them — and got six right at 0.
        // A question with one correct answer must not be sampled from a distribution.
        assertEquals(listOf(Sampling.PRECISE), engine.samplings)
    }

    @Test
    fun aGarbledReplyLeavesRowsUncategorised() = runTest {
        val result = categorizer { "I'm sorry, I can't help with that." }
            .categorize(listOf(row("REWE", -10.0)))

        assertNull(result.single().category)
    }

    @Test
    fun incomeIsNeverSentToTheModelAndKeepsNoCategory() = runTest {
        val engine = FakeLlmEngine { "1:GROCERIES" }
        val result = Categorizer { engine }.categorize(listOf(row("Employer", 3000.0), row("REWE", -10.0)))

        // The sign already says income. Asking would only give the model a chance to contradict it.
        assertNull(result[0].category)
        assertEquals(Category.GROCERIES.name, result[1].category)
        assertTrue(engine.contexts.single().contains("REWE"))
        assertTrue(!engine.contexts.single().contains("Employer"))
    }

    @Test
    fun aRepeatedMerchantIsAskedOnceAndLabelledEverywhere() = runTest {
        val engine = FakeLlmEngine { "1:GROCERIES" }
        val result = Categorizer { engine }
            .categorize(listOf(row("REWE", -10.0, day = 1), row("rewe", -25.0, day = 2)))

        // One question, so the same merchant cannot land in two categories and split its own total.
        assertEquals(1, engine.contexts.single().lines().size)
        assertTrue(result.all { it.category == Category.GROCERIES.name })
    }

    @Test
    fun merchantsAreSplitIntoBatches() = runTest {
        val many = (1..30).map { row("Merchant$it", -1.0, day = it) }
        val engine = FakeLlmEngine { "1:OTHER" }
        val progress = mutableListOf<Float>()

        Categorizer { engine }.categorize(many, onProgress = { progress += it })

        // 30 distinct merchants at 12 per batch — three round-trips, each reported.
        assertEquals(3, engine.contexts.size)
        assertEquals(3, progress.size)
        assertEquals(1f, progress.last())
    }
}
