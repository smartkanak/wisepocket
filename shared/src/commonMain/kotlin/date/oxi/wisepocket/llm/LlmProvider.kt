package date.oxi.wisepocket.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

private const val LOG = "WP-LLM"

/**
 * The app's single on-device model: provisions it once and hands the same engine to everything that needs
 * it — the chat, and statement import's profiling step.
 *
 * Registered as a Koin `single` in [date.oxi.wisepocket.di.appModule], and that scope is not a detail:
 * [com.llamatik.library.platform.LlamaBridge] is a singleton over native llama.cpp state, so a second
 * engine wouldn't be a second model — it would be the same native context being loaded and shut down
 * underneath the first. And loading a ~1 GB GGUF twice is not something to do by accident.
 */
/**
 * What a screen needs from the model: what state it's in, and a way to get it.
 *
 * An interface rather than the concrete [LlmProvider] because [LlmProvider] resolves a real path through
 * `modelsDir()`, which is an `expect` with no filesystem behind it in `commonTest`. Without this seam the
 * catch-up categorisation — the thing that was missing entirely — could only be checked by hand on a device
 * with a gigabyte already downloaded, which is to say: not checked.
 */
interface ModelGateway {
    val status: StateFlow<ModelStatus>

    suspend fun ensure(downloadUrl: String? = null, authToken: String? = null)
}

class LlmProvider(private val repository: ModelRepository) : ModelGateway {

    companion object {
        /** Default model: Qwen2.5-1.5B-Instruct, Apache-2.0 and ungated, so no token is needed. */
        const val DEFAULT_MODEL_FILE = "qwen2.5-1.5b-instruct-q4_k_m.gguf"
    }

    private val _status = MutableStateFlow<ModelStatus>(ModelStatus.Checking)
    override val status: StateFlow<ModelStatus> = _status.asStateFlow()

    // Written under loadLock but read from anywhere (chat send, import), so publication has to be explicit.
    @Volatile
    private var engine: LlmEngine? = null
    private val loadLock = Mutex()

    /** The engine, or null while the model is missing or still loading. */
    fun engineOrNull(): LlmEngine? = engine

    /**
     * Resolves the model and loads the engine. Safe to call repeatedly and from several callers at once —
     * both ViewModels do exactly that on construction.
     *
     * @param downloadUrl direct URL to a GGUF; null means "only check what's already on disk".
     */
    override suspend fun ensure(downloadUrl: String?, authToken: String?) {
        // Already loaded: nothing to do, and re-running the check would only republish the same status.
        if (engine != null) return

        // A plain "is it on disk?" check must not run while a download is in flight, or it finds nothing,
        // reports Failed, and throws the UI back to the download panel mid-download.
        if (downloadUrl == null && _status.value is ModelStatus.Downloading) return

        repository.ensureModel(downloadUrl, authToken).collect { status ->
            // Hold back Ready until the engine has actually loaded, or callers ask for an engine that
            // isn't there yet.
            if (status is ModelStatus.Ready) load(status.path) else _status.value = status
        }
    }

    private suspend fun load(modelPath: String) = loadLock.withLock {
        if (engine != null) {
            _status.value = ModelStatus.Ready(modelPath)
            return@withLock
        }
        val created = createLlmEngine(modelPath)
        runCatching { created.initialize() }
            .onSuccess {
                engine = SerializedLlmEngine(created)
                _status.value = ModelStatus.Ready(modelPath)
            }
            .onFailure {
                println("$LOG engine init failed: ${it.message}")
                _status.value = ModelStatus.Failed(it.message ?: "Engine init failed")
            }
    }
}

/**
 * Serialises access to the underlying engine.
 *
 * llama.cpp holds one generation context, so two overlapping `generate` calls — a chat reply while a
 * statement is being profiled, say — would interleave into each other's output. Waiting is the correct
 * behaviour here; the alternative is corrupt text from both.
 */
private class SerializedLlmEngine(private val delegate: LlmEngine) : LlmEngine {
    private val generateLock = Mutex()

    override suspend fun initialize() = delegate.initialize()

    override fun generate(
        system: String,
        context: String,
        user: String,
        sampling: Sampling,
    ): Flow<String> = flow {
        generateLock.withLock {
            delegate.generate(system, context, user, sampling).collect { emit(it) }
        }
    }
}
