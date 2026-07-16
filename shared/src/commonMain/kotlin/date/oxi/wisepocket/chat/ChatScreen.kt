package date.oxi.wisepocket.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import date.oxi.wisepocket.llm.ModelStatus
import date.oxi.wisepocket.model.Transaction

@Composable
fun ChatScreen(
    transactions: List<Transaction>,
    modifier: Modifier = Modifier,
) {
    val vm: ChatViewModel = viewModel { ChatViewModel() }
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(transactions) { vm.setTransactions(transactions) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeContentPadding()
            .padding(horizontal = 12.dp),
    ) {
        Text(
            text = "WisePocket",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        if (transactions.isEmpty()) {
            NothingToTalkAbout(Modifier.weight(1f))
            return@Column
        }

        when (val status = state.modelStatus) {
            is ModelStatus.Ready -> ChatBody(
                state = state,
                onSend = vm::send,
                modifier = Modifier.weight(1f),
            )
            else -> ModelStatusPanel(
                status = status,
                onDownload = vm::startDownload,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** No transactions means nothing to ground an answer in — better to say so than to invent one. */
@Composable
private fun NothingToTalkAbout(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            "Import a statement first — then ask me anything about your spending.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ChatBody(
    state: ChatUiState,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        val listState = rememberLazyListState()
        LaunchedEffect(state.messages.size) {
            if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.messages.isEmpty()) {
                item {
                    Text(
                        "Ask me about your spending — e.g. \"How much did I spend on groceries?\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            items(state.messages) { msg -> MessageBubble(msg) }
        }

        MessageInput(enabled = !state.isGenerating, onSend = onSend)
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage) {
    val isUser = msg.role == ChatMessage.Role.USER
    val bubbleColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(
                text = msg.text.ifEmpty { "…" },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun MessageInput(enabled: Boolean, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val submit = {
        if (enabled && text.isNotBlank()) {
            onSend(text)
            text = ""
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Ask about your money…") },
            enabled = enabled,
            keyboardActions = KeyboardActions(onSend = { submit() }),
        )
        TextButton(onClick = submit, enabled = enabled && text.isNotBlank()) {
            Text("Send")
        }
    }
}

@Composable
private fun ModelStatusPanel(
    status: ModelStatus,
    onDownload: (url: String, token: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when (status) {
                is ModelStatus.Checking -> {
                    CircularProgressIndicator()
                    Text("Preparing on-device model…", Modifier.padding(top = 12.dp))
                }
                is ModelStatus.Downloading -> {
                    val total = status.totalBytes
                    if (total != null && total > 0) {
                        LinearProgressIndicator(
                            progress = { status.downloadedBytes.toFloat() / total.toFloat() },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                        )
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 32.dp))
                    }
                    Text(
                        "Downloading model… ${status.downloadedBytes / (1024 * 1024)} MB",
                        Modifier.padding(top = 12.dp),
                    )
                }
                is ModelStatus.Failed -> DownloadPanel(message = status.message, onDownload = onDownload)
                is ModelStatus.Ready -> {}
            }
        }
    }
}

/** Shown when no model is present: explains the situation and lets the user fetch one by URL. */
@Composable
private fun DownloadPanel(
    message: String,
    onDownload: (url: String, token: String?) -> Unit,
) {
    var url by remember { mutableStateOf(DEFAULT_MODEL_URL) }
    var token by remember { mutableStateOf("") }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            message,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Model GGUF URL") },
            singleLine = true,
        )
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            label = { Text("Auth token (optional, for gated hosts)") },
            singleLine = true,
        )
        TextButton(
            onClick = { onDownload(url, token) },
            enabled = url.isNotBlank(),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text("Download model")
        }
        Text(
            "The default is Qwen2.5-1.5B-Instruct (Apache-2.0, no login needed) as a GGUF file (~1 GB). " +
                "Or paste any direct GGUF URL. The token field is only needed for license-gated hosts.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

private const val DEFAULT_MODEL_URL =
    "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf"
