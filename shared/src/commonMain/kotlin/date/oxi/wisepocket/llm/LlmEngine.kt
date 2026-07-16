package date.oxi.wisepocket.llm

import com.llamatik.library.platform.LlamaBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.TimeSource

/** Tag for latency logs — grep `WP-LLM` in logcat (Android) or the Xcode/simctl console (iOS). */
private const val LOG = "WP-LLM"

/**
 * How the next token gets picked. Not a tuning knob — the difference between a working feature and a
 * broken one, measured on-device.
 *
 * The engine was built for chat and sampled like it: `temperature 0.7`, `topP 0.95`, `topK 40`. Those are
 * right for prose and wrong for a question with one correct answer. Asked to categorise eleven real
 * merchants at 0.7, the model answered FEES five times — Amazon, Shell and a pharmacy among them. The same
 * model, same prompt, at temperature 0 got six of them right immediately, Amazon included. It always knew;
 * we were rolling dice on its output.
 */
enum class Sampling {
    /** One right answer wanted: greedy. Categorisation, profiling — anything the code then acts on. */
    PRECISE,

    /** Prose wanted. Greedy chat is stilted and repetitive, so the chat keeps its randomness. */
    CONVERSATIONAL,
}

/**
 * Streaming on-device LLM over a GGUF model via llama.cpp (Llamatik). One implementation for all
 * platforms — Android (JNI) and iOS (Kotlin/Native cinterop) both link llama.cpp natively, so no
 * per-platform engine code is needed.
 */
interface LlmEngine {
    /** Loads the GGUF model. Expensive — runs off the main thread. */
    suspend fun initialize()

    /**
     * Streams the reply token-by-token. [system] + [context] are the grounding (instructions +
     * serialized transactions + history); [user] is the raw question. Llamatik applies the model's
     * chat template.
     */
    fun generate(
        system: String,
        context: String,
        user: String,
        sampling: Sampling = Sampling.CONVERSATIONAL,
    ): Flow<String>
}

private class LlamatikLlmEngine(private val modelPath: String) : LlmEngine {

    /** Whatever the load settled on, so re-applying params for a call can't silently unload the GPU. */
    private var loadedGpuLayers = 0

    /**
     * Applies the sampling settings. Safe to call between generations — of these, only `gpuLayers` is
     * read at (re)load, so it's passed back unchanged.
     */
    private fun setParams(sampling: Sampling, gpuLayers: Int) = runCatching {
        LlamaBridge.updateGenerateParams(
            temperature = if (sampling == Sampling.PRECISE) 0.0f else 0.7f,
            maxTokens = 256,          // cap the answer so it can't run away
            // Greedy means these two are moot; setting them tight is belt and braces against a build that
            // applies top-k/top-p before temperature.
            topP = if (sampling == Sampling.PRECISE) 1.0f else 0.95f,
            topK = if (sampling == Sampling.PRECISE) 1 else 40,
            // No repeat penalty when precise: the answer to eleven merchants is *supposed* to repeat, and
            // penalising that is a direct push towards a wrong category for the eleventh.
            repeatPenalty = if (sampling == Sampling.PRECISE) 1.0f else 1.1f,
            contextLength = 4096,
            numThreads = -1,          // let the platform pick a sensible core count
            useMmap = true,
            flashAttention = false,
            batchSize = 512,          // bigger prefill batch → faster time-to-first-token
            gpuLayers = gpuLayers,
        )
    }.onFailure { println("$LOG updateGenerateParams failed: ${it.message}") }

    override suspend fun initialize() {
        withContext(Dispatchers.Default) {
            // Params must be set BEFORE load: gpuLayers only takes effect on (re)load. Prefill
            // (time-to-first-token) dominates on-device, so offload to GPU when the build supports it.
            val start = TimeSource.Monotonic.markNow()
            setParams(Sampling.CONVERSATIONAL, gpuLayers = -1)   // -1 = offload all layers to GPU
            var loaded = LlamaBridge.initGenerateModel(modelPath)
            var mode = "GPU"
            loadedGpuLayers = -1
            if (!loaded) {
                // GPU offload unsupported on this build → fall back to CPU.
                println("$LOG GPU load failed after ${start.elapsedNow()}, retrying CPU-only")
                setParams(Sampling.CONVERSATIONAL, gpuLayers = 0)
                loaded = LlamaBridge.initGenerateModel(modelPath)
                mode = "CPU"
                loadedGpuLayers = 0
            }
            check(loaded) { "Failed to load model at $modelPath" }
            println("$LOG model loaded in ${start.elapsedNow()} [$mode] — ${modelPath.substringAfterLast('/')}")
        }
    }

    override fun generate(
        system: String,
        context: String,
        user: String,
        sampling: Sampling,
    ): Flow<String> = callbackFlow {
        val start = TimeSource.Monotonic.markNow()
        var firstTokenAt: Duration? = null
        var chunks = 0
        var chars = 0
        // Set per call, and safe because SerializedLlmEngine holds the lock across the whole generation —
        // LlamaBridge's params are process-global, so an overlapping call would otherwise resample this one.
        setParams(sampling, loadedGpuLayers)
        println("$LOG generation start ($sampling, context ${context.length} chars)")

        // generateWithContextStream blocks until generation completes; run it off the collector's thread.
        val worker = launch(Dispatchers.Default) {
            LlamaBridge.generateWithContextStream(
                system = system,
                context = context,
                user = user,
                onDelta = { token ->
                    if (firstTokenAt == null) {
                        firstTokenAt = start.elapsedNow()
                        println("$LOG time-to-first-token: $firstTokenAt")
                    }
                    chunks++
                    chars += token.length
                    trySend(token)
                },
                onDone = {
                    val total = start.elapsedNow()
                    val ttft = firstTokenAt ?: total
                    val genSeconds = (total - ttft).inWholeMilliseconds / 1000.0
                    val tps = if (genSeconds > 0) chunks / genSeconds else 0.0
                    println(
                        "$LOG done: total=$total ttft=$ttft chunks=$chunks chars=$chars " +
                            "~${tps.toInt()} chunks/s",
                    )
                    close()
                },
                onError = {
                    println("$LOG error after ${start.elapsedNow()}: $it")
                    close(RuntimeException(it))
                },
            )
        }
        awaitClose { worker.cancel() }
    }

}

fun createLlmEngine(modelPath: String): LlmEngine = LlamatikLlmEngine(modelPath)
