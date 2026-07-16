package date.oxi.wisepocket.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
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
            .padding(horizontal = 20.dp),
    ) {
        Text(
            text = "AI Assistant",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(vertical = 12.dp),
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

        // ime ∪ navigationBars, not just imePadding: the frame no longer pads the bottom, so the input has to
        // clear the navigation bar when the keyboard is down and the keyboard when it's up. union takes the
        // larger of the two, so the keyboard (which already spans the nav-bar area) doesn't get it added twice.
        val bottomInset = Modifier.windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
        MessageInput(enabled = !state.isGenerating, onSend = onSend, modifier = bottomInset)
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
    val bubbleShape = if (isUser) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) scheme.primary else scheme.surfaceContainer,
                contentColor = if (isUser) scheme.onPrimary else scheme.onSurface,
            ),
            shape = bubbleShape,
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            // No text yet means the model is still thinking — animate three dots rather than sit on a static
            // "…", so it's clear the answer is on its way and nothing has stalled.
            if (msg.text.isEmpty()) {
                TypingIndicator(Modifier.padding(horizontal = 14.dp, vertical = 14.dp))
            } else {
                Text(
                    text = msg.text,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

/**
 * Three dots that fade in and out in sequence — the "assistant is typing" convention. Each dot lags the last
 * by a beat, so the group reads as a wave rather than three lights blinking in unison.
 */
@Composable
private fun TypingIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 500, delayMillis = index * 160, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$index",
            )
            Box(Modifier.size(7.dp).clip(CircleShape).background(LocalContentColor.current.copy(alpha = alpha)))
        }
    }
}

@Composable
private fun MessageInput(enabled: Boolean, onSend: (String) -> Unit, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf("") }
    val submit = {
        if (enabled && text.isNotBlank()) {
            onSend(text)
            text = ""
        }
    }
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Ask about your spending…") },
            enabled = enabled,
            shape = MaterialTheme.shapes.large,
            singleLine = true,
            keyboardActions = KeyboardActions(onSend = { submit() }),
        )
        Button(
            onClick = submit,
            enabled = enabled && text.isNotBlank(),
            shape = CircleShape,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(48.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
        }
    }
}
