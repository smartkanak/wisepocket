package date.oxi.wisepocket.statement

import date.oxi.wisepocket.llm.LlmEngine
import date.oxi.wisepocket.pdf.LayoutText
import date.oxi.wisepocket.pdf.PdfExtractionException
import date.oxi.wisepocket.pdf.extractPdfText
import kotlin.time.TimeSource

private const val LOG = "WP-PDF"

sealed interface ImportResult {
    data class Success(val result: ParseResult) : ImportResult

    /** The PDF was read but no row in it looked like a transaction. */
    data object NoTransactions : ImportResult

    data class Failed(val message: String) : ImportResult
}

/**
 * PDF bytes in, transactions out — with no knowledge of any specific bank.
 *
 * 1. **pdfium** gives every character and its position (~100–700 ms for a whole statement).
 * 2. **[LayoutText]** rebuilds the visual columns from those coordinates.
 * 3. **[ProfileDetector]** works out the document's conventions and, where the statement prints its own
 *    balances, *proves* the profile by reproducing them to the cent.
 * 4. **[LlmProfiler]** takes over only when step 3 couldn't verify anything — one short prompt on a
 *    sample of rows, not the whole statement.
 * 5. **[GenericParser]** applies the profile to every row, deterministically.
 *
 * The expensive, judgement-heavy step runs once on ~15 rows; the mechanical step runs on all of them.
 */
class StatementImporter(
    /**
     * Supplies the on-device engine for the profiling step. A lambda rather than the engine itself
     * because the model loads asynchronously — resolving it at import time means an import started after
     * the model is ready gets the LLM even though this importer was built before it.
     */
    private val engineProvider: () -> LlmEngine? = { null },
) {
    // The outermost boundary of the import: whatever a PDF engine, a regex or the model throws, the user
    // gets a message and the app stays up. Enumerating the types would only mean the next unlisted one
    // crashes them.
    @Suppress("TooGenericExceptionCaught")
    suspend fun import(bytes: ByteArray): ImportResult {
        val started = TimeSource.Monotonic.markNow()
        return try {
            val doc = extractPdfText(bytes)
            val lines = LayoutText.linesOf(doc)
            println("$LOG layout: ${lines.size} lines from ${doc.pages.size} pages in ${started.elapsedNow()}")

            val candidateRows = lines.count { Tokens.looksLikeRow(it) }
            if (candidateRows == 0) return ImportResult.NoTransactions

            val profile = resolveProfile(lines) ?: return ImportResult.NoTransactions

            val result = GenericParser.parse(lines, profile)
            println(
                "$LOG parsed ${result.transactions.size}/$candidateRows rows via ${profile.source} profile " +
                    "(${result.needsReviewCount} need review) in ${started.elapsedNow()}",
            )
            ImportResult.Success(result)
        } catch (e: PdfExtractionException) {
            println("$LOG extraction failed: ${e.message}")
            ImportResult.Failed(e.message ?: "Could not read this PDF")
        } catch (e: Exception) {
            println("$LOG import failed: ${e.message}")
            ImportResult.Failed(e.message ?: "Import failed")
        }
    }

    /**
     * Proof first, then the text itself, and only then the model.
     *
     * The model is asked **only what the document genuinely leaves open**, and this ordering was learned
     * the hard way. Asked to profile a statement whose conventions were in fact decidable — it prints
     * explicit minus signs and unambiguous `1,091.24` grouping — the 1.5B model answered
     * "GERMAN/PLUS_MEANS_INCOME" and turned €9.5k of income into €600k of spending, overriding a heuristic
     * that had been right, after 96 seconds of inference. A small model's opinion must never outrank
     * something the text states outright.
     *
     * What's left for the model is the real ambiguity: a statement with no stated balance to reconcile
     * against *and* no sign markers anywhere, where "unmarked means spending" is an assumption some other
     * bank will break.
     */
    private suspend fun resolveProfile(lines: List<String>): StatementProfile? {
        val detection = ProfileDetector.detect(lines)
        if (detection.profile?.isVerified == true) return detection.profile

        if (!detection.isAmbiguous) {
            println("$LOG conventions are decidable from the text — no LLM needed")
            return detection.profile
        }

        val engine = engineProvider()
        if (engine == null) {
            println("$LOG no engine available — falling back to the heuristic profile")
            return detection.profile
        }

        val llmProfile = runCatching { LlmProfiler(engine).profile(lines, detection.candidates) }
            .onFailure { e -> println("$LOG LLM profiling failed: ${e.message}") }
            .getOrNull()
            ?: return detection.profile

        // If the model's answer happens to reproduce a stated balance, it's no longer a judgement call —
        // it's verified, and the user shouldn't be asked to double-check arithmetic that already proves out.
        val expected = detection.hints.expectedNet(llmProfile.numberFormat)
        val net = GenericParser.netOf(lines, llmProfile)
        if (expected != null && net != null && GenericParser.matches(net, expected)) {
            println("$LOG LLM profile also reconciles — promoting to verified")
            return llmProfile.copy(source = StatementProfile.Source.RECONCILED)
        }
        return llmProfile
    }
}
