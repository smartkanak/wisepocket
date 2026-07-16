package date.oxi.wisepocket

import date.oxi.wisepocket.llm.LlmEngine
import date.oxi.wisepocket.llm.Sampling
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * An [LlmEngine] that replies with whatever the test tells it to.
 *
 * The model's phrasing is the one part of categorisation that can't be pinned down, so the tests pin
 * everything around it: they script a reply — tidy, fenced, garbled, hostile — and assert the code either
 * extracts the right labels or none at all. It also records what it was asked, which is how batching and
 * merchant de-duplication get checked without a device.
 */
class FakeLlmEngine(private val reply: (context: String) -> String) : LlmEngine {

    /** Every context block handed to [generate], in order. */
    val contexts = mutableListOf<String>()

    /** How each call asked to be sampled — greedy or not is a correctness property here, not a preference. */
    val samplings = mutableListOf<Sampling>()

    override suspend fun initialize() = Unit

    override fun generate(
        system: String,
        context: String,
        user: String,
        sampling: Sampling,
    ): Flow<String> = flow {
        contexts += context
        samplings += sampling
        // Streamed in pieces, like the real one: nothing downstream may assume a whole reply per emission.
        reply(context).chunked(CHUNK).forEach { emit(it) }
    }

    private companion object {
        const val CHUNK = 7
    }
}
