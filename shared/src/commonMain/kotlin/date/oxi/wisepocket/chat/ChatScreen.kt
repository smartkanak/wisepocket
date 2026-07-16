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
import androidx.compose.material3.Button
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
import date.oxi.wisepocket.llm.ModelStatus
import org.koin.compose.viewmodel.koinViewModel

/**
 * Chat needs two things: a model to answer with, and transactions to answer about.
 *
 * The order of those checks used to be the wrong way round, and it made the app incoherent. The screen
 * returned early on "no transactions yet" — so the model download panel, which lived below, was
 * unreachable until you had imported something, while importing without the model produced no categories.
 * The way out of the model-less state was locked behind the state that needed the model.
 *
 * Now it reports whichever thing is actually missing, and the model isn't obtained here at all: it's app
 * infrastructure, so setup is [onSetUpModel]'s job and this screen just points at it.
 */
@Composable
fun ChatScreen(onSetUpModel: () -> Unit, modifier: Modifier = Modifier) {
    val vm: ChatViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()

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

        when {
            state.modelStatus !is ModelStatus.Ready -> Missing(
                text = "Chat runs on an on-device AI, and it isn't set up yet.",
                action = "Set up" to onSetUpModel,
                modifier = Modifier.weight(1f),
            )

            // Nothing to ground an answer in — better to say so than to invent one.
            !state.hasTransactions -> Missing(
                text = "Import a statement first — then ask me anything about your spending.",
                action = null,
                modifier = Modifier.weight(1f),
            )

            else -> ChatBody(state = state, onSend = vm::send, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun Missing(text: String, action: Pair<String, () -> Unit>?, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            action?.let { (label, onClick) ->
                Button(onClick = onClick, modifier = Modifier.padding(top = 12.dp)) { Text(label) }
            }
        }
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

/**
 * One message.
 *
 * Both colours are stated rather than left to `contentColorFor`: several roles deliberately share a colour
 * in this scheme (white is `primary` *and* `primaryContainer`), so resolving a content colour from a
 * container colour alone is ambiguous by construction. The user is white-on-blue, the assistant is
 * white-on-raised-blue — the two speakers are told apart by weight of surface, not by hue.
 */
@Composable
private fun MessageBubble(msg: ChatMessage) {
    val isUser = msg.role == ChatMessage.Role.USER
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) scheme.primaryContainer else scheme.surfaceContainerHigh,
                contentColor = if (isUser) scheme.onPrimaryContainer else scheme.onSurface,
            ),
            shape = MaterialTheme.shapes.medium,
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
