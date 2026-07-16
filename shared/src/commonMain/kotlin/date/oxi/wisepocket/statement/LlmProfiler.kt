package date.oxi.wisepocket.statement

import date.oxi.wisepocket.llm.LlmEngine
import date.oxi.wisepocket.statement.Amounts.SignConvention
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.TimeSource

private const val LOG = "WP-PDF"

/**
 * Asks the on-device model the one thing a statement can leave genuinely undecidable: **how it marks
 * money leaving the account**, when it states no balance to reconcile against and carries no sign markers.
 *
 * Scope is deliberately narrow, for reasons measured on-device rather than assumed:
 *
 * - **It chooses only between [candidates]** — profiles already consistent with the text. Everything the
 *   document states outright (date format, and number format wherever a thousands separator settles it) is
 *   read deterministically. Given free rein, the 1.5B model contradicted plain evidence and inflated a
 *   statement's spending a hundredfold.
 * - **It sees a compressed sample, not the statement.** Inference here is prefill-bound (~13 tok/s), so
 *   cost tracks input length. Raw layout rows are mostly column padding; collapsing that and capping at
 *   [SAMPLE_ROWS] is the difference between a usable prompt and a 96-second one.
 *
 * The answer names a convention; [GenericParser] then applies it to every row deterministically, so the
 * model never has to copy a digit correctly.
 */
class LlmProfiler(private val engine: LlmEngine) {

    /** Picks among [candidates]; returns null if the reply doesn't name one of them. */
    suspend fun profile(lines: List<String>, candidates: List<StatementProfile>): StatementProfile? {
        val options = candidates.map { it.signConvention }.distinct()
        if (options.size < 2) return candidates.firstOrNull()

        val sample = lines.filter { Tokens.looksLikeRow(it) }
            .take(SAMPLE_ROWS)
            .map { it.replace(Regex("""\s{2,}"""), " ").trim() }
        if (sample.isEmpty()) return null

        val started = TimeSource.Monotonic.markNow()
        val reply = StringBuilder()
        engine.generate(system = systemPrompt(options), context = sample.joinToString("\n"), user = USER)
            .collect { reply.append(it) }
        println("$LOG LLM profile took ${started.elapsedNow()}: ${reply.toString().take(120)}")

        val chosen = readSign(reply.toString(), options) ?: return null
        return candidates.first { it.signConvention == chosen }
            .copy(source = StatementProfile.Source.LLM)
    }

    /** Only the conventions still in play are offered — a choice it can't make is a choice it can't botch. */
    private fun systemPrompt(options: List<SignConvention>): String = buildString {
        append("You identify how a bank statement marks money LEAVING the account. /no_think\n")
        append("Reply with ONLY this JSON, no prose: {\"sign\":\"...\"}\n\n")
        append("Choose exactly one of:\n")
        options.forEach { append("  ${it.name} — ${describe(it)}\n") }
    }

    private fun describe(convention: SignConvention): String = when (convention) {
        SignConvention.EXPLICIT_MINUS -> "spending carries a minus sign (-12,34)"
        SignConvention.PLUS_MEANS_INCOME ->
            "income is marked '+', and spending carries no sign at all"
        SignConvention.SOLL_HABEN ->
            "each amount ends in S (Soll = spending) or H (Haben = income)"
    }

    private fun readSign(reply: String, options: List<SignConvention>): SignConvention? {
        val start = reply.indexOf('{')
        val end = reply.lastIndexOf('}')
        val named = if (start >= 0 && end > start) {
            runCatching {
                Json.parseToJsonElement(reply.substring(start, end + 1))
                    .jsonObject["sign"]?.jsonPrimitive?.content
            }.getOrNull()
        } else {
            null
        }
        // Fall back to a plain scan: small models like to wrap JSON in fences or prose.
        val text = (named ?: reply).uppercase()
        return options.firstOrNull { text.contains(it.name) }
    }

    private companion object {
        /** Enough rows to show the pattern, few enough to keep prefill short. */
        const val SAMPLE_ROWS = 12

        const val USER = "How does this statement mark money leaving the account?"
    }
}
