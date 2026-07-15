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
    fun generate(system: String, context: String, user: String): Flow<String>

    /** Releases native resources. */
    fun close()
}

private class LlamatikLlmEngine(private val modelPath: String) : LlmEngine {

    override suspend fun initialize() {
        withContext(Dispatchers.Default) {
            // Params must be set BEFORE load: gpuLayers only takes effect on (re)load. Prefill
            // (time-to-first-token) dominates on-device, so offload to GPU when the build supports it.
            fun setParams(gpuLayers: Int) = runCatching {
                LlamaBridge.updateGenerateParams(
                    temperature = 0.7f,
                    maxTokens = 256,          // cap the answer so it can't run away
                    topP = 0.95f,
                    topK = 40,
                    repeatPenalty = 1.1f,
                    contextLength = 4096,
                    numThreads = -1,          // let the platform pick a sensible core count
                    useMmap = true,
                    flashAttention = false,
                    batchSize = 512,          // bigger prefill batch → faster time-to-first-token
                    gpuLayers = gpuLayers,
                )
            }.onFailure { println("$LOG updateGenerateParams failed: ${it.message}") }

            val start = TimeSource.Monotonic.markNow()
            setParams(gpuLayers = -1)         // -1 = offload all layers to GPU
            var loaded = LlamaBridge.initGenerateModel(modelPath)
            var mode = "GPU"
            if (!loaded) {
                // GPU offload unsupported on this build → fall back to CPU.
                println("$LOG GPU load failed after ${start.elapsedNow()}, retrying CPU-only")
                setParams(gpuLayers = 0)
                loaded = LlamaBridge.initGenerateModel(modelPath)
                mode = "CPU"
            }
            check(loaded) { "Failed to load model at $modelPath" }
            println("$LOG model loaded in ${start.elapsedNow()} [$mode] — ${modelPath.substringAfterLast('/')}")
        }
    }

    override fun generate(system: String, context: String, user: String): Flow<String> = callbackFlow {
        val start = TimeSource.Monotonic.markNow()
        var firstTokenAt: Duration? = null
        var chunks = 0
        var chars = 0
        println("$LOG generation start (context ${context.length} chars)")

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

    override fun close() {
        LlamaBridge.shutdown()
    }
}

fun createLlmEngine(modelPath: String): LlmEngine = LlamatikLlmEngine(modelPath)
