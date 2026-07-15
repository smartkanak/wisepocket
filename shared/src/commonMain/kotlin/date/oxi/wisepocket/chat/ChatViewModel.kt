package date.oxi.wisepocket.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import date.oxi.wisepocket.data.MockTransactions
import date.oxi.wisepocket.llm.LlmEngine
import date.oxi.wisepocket.llm.ModelRepository
import date.oxi.wisepocket.llm.ModelStatus
import date.oxi.wisepocket.llm.createLlmEngine
import date.oxi.wisepocket.model.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class ChatUiState(
    val modelStatus: ModelStatus = ModelStatus.Checking,
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
)

/**
 * Drives the chat slice: provisions the on-device GGUF model, then runs the streaming
 * prompt → response loop grounded in the (mock) transactions via [LlmEngine] (llama.cpp / Llamatik).
 */
class ChatViewModel(
    private val transactions: List<Transaction> = MockTransactions.sample,
    private val modelRepository: ModelRepository = ModelRepository(fileName = DEFAULT_MODEL_FILE),
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var engine: LlmEngine? = null

    init {
        collectProvisioning(modelRepository.ensureModel())
    }

    /** Retry provisioning by downloading the model from [url] (with optional [authToken] for gated hosts). */
    fun startDownload(url: String, authToken: String? = null) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return
        collectProvisioning(modelRepository.ensureModel(trimmed, authToken?.ifBlank { null }))
    }

    private fun collectProvisioning(flow: Flow<ModelStatus>) {
        viewModelScope.launch {
            flow.collect { status ->
                // Defer surfacing Ready until the engine has actually initialized (see initEngine).
                if (status is ModelStatus.Ready) initEngine(status.path)
                else _state.value = _state.value.copy(modelStatus = status)
            }
        }
    }

    private fun initEngine(modelPath: String) {
        viewModelScope.launch {
            val e = createLlmEngine(modelPath)
            runCatching { e.initialize() }
                .onSuccess {
                    engine = e
                    _state.value = _state.value.copy(modelStatus = ModelStatus.Ready(modelPath))
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        modelStatus = ModelStatus.Failed(it.message ?: "Engine init failed"),
                    )
                }
        }
    }

    fun send(userText: String) {
        val text = userText.trim()
        if (text.isEmpty() || _state.value.isGenerating) return
        val activeEngine = engine ?: return

        val history = _state.value.messages
        val userMsg = ChatMessage(ChatMessage.Role.USER, text)
        val assistantMsg = ChatMessage(ChatMessage.Role.ASSISTANT, "")
        _state.value = _state.value.copy(
            messages = history + userMsg + assistantMsg,
            isGenerating = true,
        )

        val context = PromptBuilder.buildContext(transactions, history)
        viewModelScope.launch {
            val builder = StringBuilder()
            activeEngine.generate(system = PromptBuilder.SYSTEM, context = context, user = text)
                .catch { e -> builder.append("\n[error: ${e.message}]") }
                .collect { chunk ->
                    builder.append(chunk)
                    updateLastAssistant(visibleAnswer(builder.toString()))
                }
            _state.value = _state.value.copy(isGenerating = false)
        }
    }

    /**
     * Hides `<think>…</think>` reasoning blocks from the displayed answer — a no-op for the default
     * Qwen2.5 model, but keeps the UI clean if a "thinking" model (e.g. Qwen3) is swapped in. Handles
     * the streaming case where the closing tag hasn't arrived yet.
     */
    private fun visibleAnswer(raw: String): String {
        var s = Regex("(?s)<think>.*?</think>").replace(raw, "")
        val open = s.indexOf("<think>")
        if (open >= 0) s = s.substring(0, open)
        return s.trimStart()
    }

    private fun updateLastAssistant(text: String) {
        val msgs = _state.value.messages.toMutableList()
        val lastIndex = msgs.indexOfLast { it.role == ChatMessage.Role.ASSISTANT }
        if (lastIndex >= 0) {
            msgs[lastIndex] = msgs[lastIndex].copy(text = text)
            _state.value = _state.value.copy(messages = msgs)
        }
    }

    override fun onCleared() {
        engine?.close()
        engine = null
    }

    private companion object {
        const val DEFAULT_MODEL_FILE = "qwen2.5-1.5b-instruct-q4_k_m.gguf"
    }
}
