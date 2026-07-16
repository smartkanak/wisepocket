package date.oxi.wisepocket.pdf

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Uses the Storage Access Framework, so the app reads one user-chosen file and needs no storage
 * permission at all — which matches the privacy story: nothing is scanned, nothing is uploaded.
 */
@Composable
actual fun rememberPdfPicker(onPicked: (ByteArray?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) {
            onPicked(null)
            return@rememberLauncherForActivityResult
        }
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
        onPicked(bytes)
    }
    return { launcher.launch("application/pdf") }
}
