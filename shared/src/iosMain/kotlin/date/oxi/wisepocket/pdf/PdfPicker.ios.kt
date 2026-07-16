package date.oxi.wisepocket.pdf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypePDF
import platform.darwin.NSObject
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.posix.memcpy

/**
 * Presents the system document picker. Like Android's SAF, this grants access to exactly the one file the
 * user chose — no photo/files permission is requested, which matches the privacy story: nothing is
 * scanned, nothing is uploaded.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberPdfPicker(onPicked: (ByteArray?) -> Unit): () -> Unit {
    // The delegate is retained by us, not by the picker: UIKit holds delegates weakly, so a delegate that
    // only lived for the duration of the call would be collected before the user picks anything and the
    // callback would never fire.
    val delegate = remember { PdfPickerDelegate() }

    return {
        delegate.onPicked = onPicked
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypePDF))
        picker.delegate = delegate
        UIApplication.sharedApplication.keyWindow?.rootViewController
            ?.presentViewController(picker, animated = true, completion = null)
            ?: onPicked(null)
    }
}

@OptIn(ExperimentalForeignApi::class)
private class PdfPickerDelegate : NSObject(), UIDocumentPickerDelegateProtocol {

    var onPicked: (ByteArray?) -> Unit = {}

    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        if (url == null) {
            onPicked(null)
            return
        }
        // Files outside the app's container are security-scoped; without this the read silently returns
        // nothing.
        val scoped = url.startAccessingSecurityScopedResource()
        try {
            onPicked(NSData.dataWithContentsOfURL(url)?.toByteArray())
        } finally {
            if (scoped) url.stopAccessingSecurityScopedResource()
        }
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onPicked(null)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply {
        usePinned { memcpy(it.addressOf(0), bytes, length) }
    }
}
