package date.oxi.wisepocket.llm

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** The default: Qwen2.5-1.5B-Instruct, Apache-2.0 and ungated, so no token is needed. */
const val DEFAULT_MODEL_URL =
    "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf"

private const val BYTES_PER_MB = 1024 * 1024

/**
 * Getting the on-device model, wherever the user happens to be standing.
 *
 * One panel, shared by first-run setup and the chat, because the model is **app infrastructure** — the chat
 * and statement categorisation both need it. It used to live inside `ChatScreen` behind a
 * "no transactions yet" early return, which meant the only way to obtain the model was gated on something
 * that has nothing to do with it: the app told you to import a statement first, then couldn't categorise
 * the statement you'd imported.
 */
@Composable
fun ModelSetupPanel(
    status: ModelStatus,
    onDownload: (url: String, token: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (status) {
            is ModelStatus.Checking -> {
                CircularProgressIndicator()
                Text("Looking for the model…", Modifier.padding(top = 12.dp))
            }

            is ModelStatus.Downloading -> DownloadProgress(status)

            // Nothing to report on a fresh install: not having the model yet isn't news, it's why you're here.
            is ModelStatus.Absent -> DownloadForm(message = null, onDownload = onDownload)
            is ModelStatus.Failed -> DownloadForm(message = status.message, onDownload = onDownload)

            // Nothing to set up. Callers decide what to show instead — there's no sensible generic answer.
            is ModelStatus.Ready -> Unit
        }
    }
}

@Composable
private fun DownloadProgress(status: ModelStatus.Downloading) {
    val total = status.totalBytes
    if (total != null && total > 0) {
        LinearProgressIndicator(
            progress = { status.downloadedBytes.toFloat() / total.toFloat() },
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    Text(
        "Downloading… ${status.downloadedBytes / BYTES_PER_MB} MB",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 12.dp),
    )
    Text(
        "You can keep using the app while this runs.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun DownloadForm(message: String?, onDownload: (url: String, token: String?) -> Unit) {
    // Saveable: a rotation mid-typing shouldn't discard a pasted URL and token.
    var url by rememberSaveable { mutableStateOf(DEFAULT_MODEL_URL) }
    var token by rememberSaveable { mutableStateOf("") }
    var advanced by rememberSaveable { mutableStateOf(false) }

    // Only a real failure has something to say, and then it's genuinely an error.
    message?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
    }
    Button(onClick = { onDownload(url, token.ifBlank { null }) }, enabled = url.isNotBlank()) {
        Text("Download model (~1 GB)")
    }
    Text(
        "Qwen2.5-1.5B-Instruct, Apache-2.0 — no account needed. It runs entirely on this phone.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    // The URL and token fields are for a different person than the one who just wants the app to work, so
    // they don't get to be the first thing anyone reads.
    Text(
        if (advanced) "Use the default" else "Use a different model",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp).clickable { advanced = !advanced },
    )
    if (advanced) {
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
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Auth token (only for gated hosts)") },
            singleLine = true,
        )
    }
}
