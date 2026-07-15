package date.oxi.wisepocket.llm

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
actual fun modelsDir(): String {
    val docs = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    val dir = docs?.URLByAppendingPathComponent("models", isDirectory = true)
    if (dir != null) {
        NSFileManager.defaultManager.createDirectoryAtURL(dir, true, null, null)
    }
    return dir?.path ?: error("Unable to resolve iOS models directory")
}
