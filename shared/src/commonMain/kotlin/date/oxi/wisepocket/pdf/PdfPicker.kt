package date.oxi.wisepocket.pdf

import androidx.compose.runtime.Composable

/**
 * Platform file picker for a statement PDF. Returns the file's bytes, or null if the user backed out.
 *
 * Returns a launcher rather than taking a callback directly because both platforms need to register the
 * picker with the host (Android's Activity Result API) before it can be shown.
 */
@Composable
expect fun rememberPdfPicker(onPicked: (ByteArray?) -> Unit): () -> Unit
