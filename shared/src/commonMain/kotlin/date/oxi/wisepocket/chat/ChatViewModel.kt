package date.oxi.wisepocket.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import date.oxi.wisepocket.llm.LlmProvider
import date.oxi.wisepocket.llm.ModelStatus
import date.oxi.wisepocket.model.Transaction
import date.oxi.wisepocket.transactions.TransactionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class ChatUiState(
    val modelStatus: ModelStatus = ModelStatus.Checking,
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    /** Whether there is anything to ground an answer in. The chat has nothing to say without it. */
    val hasTransactions: Boolean = false,
)

/**
 * Drives the chat slice: provisions the on-device GGUF model, then runs the streaming
 * prompt → response loop grounded in the user's transactions via the on-device engine.
 */
class ChatViewModel(
    private val llm: LlmProvider,
    transactionStore: TransactionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    /**
     * What the chat is allowed to talk about — read from the store rather than handed down from the UI, so
     * a deleted row stops being answerable without a screen having to remember to say so.
     */
    private var transactions: List<Transaction> = emptyList()

    init {
        viewModelScope.launch {
            transactionStore.transactions.collect {
                transactions = it
                _state.value = _state.value.copy(hasTransactions = it.isNotEmpty())
            }
        }
        viewModelScope.launch {
            llm.status.collect { _state.value = _state.value.copy(modelStatus = it) }
        }
        viewModelScope.launch { llm.ensure() }
    }

    /** Retry provisioning by downloading the model from [url] (with optional [authToken] for gated hosts). */
    fun startDownload(url: String, authToken: String? = null) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { llm.ensure(trimmed, authToken?.ifBlank { null }) }
    }

    fun send(userText: String) {
        val text = userText.trim()
        if (text.isEmpty() || _state.value.isGenerating) return
        val activeEngine = llm.engineOrNull() ?: return

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
                .catch { e ->
                    // catch() emits nothing downstream, so collect never runs again — the error has to be
                    // written to the UI here, or the user watches an empty bubble forever.
                    builder.append("\n[error: ${e.message}]")
                    updateLastAssistant(visibleAnswer(builder.toString()))
                }
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

}
