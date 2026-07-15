package date.oxi.wisepocket.llm

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

/** Progress of resolving the on-device model. */
sealed interface ModelStatus {
    data object Checking : ModelStatus
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long?) : ModelStatus
    data class Ready(val path: String) : ModelStatus
    data class Failed(val message: String) : ModelStatus
}

/**
 * Resolves the GGUF model on device: if already present under [modelsDir] it's used as-is,
 * otherwise it's streamed from [downloadUrl]. During development you can side-load the model to the
 * returned path to skip the download entirely.
 *
 * @param fileName target file name, e.g. "qwen2.5-1.5b-instruct-q4_k_m.gguf".
 */
class ModelRepository(
    private val fileName: String,
    private val httpClientFactory: () -> HttpClient = { HttpClient() },
) {
    val modelPath: String get() = "${modelsDir()}/$fileName"

    private fun exists(path: String): Boolean = SystemFileSystem.metadataOrNull(Path(path))?.isRegularFile == true

    /** True if the model file is already present on disk. */
    fun isPresent(): Boolean = exists(modelPath)

    /**
     * Checks for the model and, when [downloadUrl] is non-null, downloads it if missing.
     * Emits status updates; terminates with [ModelStatus.Ready] or [ModelStatus.Failed].
     *
     * @param downloadUrl direct URL to the GGUF file; null means "only check, don't download".
     * @param authToken optional bearer token for license-gated hosts.
     */
    fun ensureModel(downloadUrl: String? = null, authToken: String? = null): Flow<ModelStatus> = flow {
        emit(ModelStatus.Checking)
        val target = modelPath
        if (exists(target)) {
            emit(ModelStatus.Ready(target))
            return@flow
        }
        if (downloadUrl == null) {
            emit(ModelStatus.Failed("Model not found at $target. Side-load a GGUF model or paste a download URL below."))
            return@flow
        }
        val client = httpClientFactory()
        try {
            val tmp = "$target.part"
            run {
                client.prepareGet(downloadUrl) {
                    if (authToken != null) header("Authorization", "Bearer $authToken")
                }.execute { response ->
                    val total = response.headers["Content-Length"]?.toLongOrNull()
                    val channel = response.bodyAsChannel()
                    var downloaded = 0L
                    SystemFileSystem.sink(Path(tmp)).buffered().use { sink ->
                        while (!channel.isClosedForRead) {
                            val packet = channel.readRemaining(CHUNK)
                            while (!packet.exhausted()) {
                                val bytes = packet.readByteArray()
                                sink.write(bytes)
                                downloaded += bytes.size
                                emit(ModelStatus.Downloading(downloaded, total))
                            }
                        }
                    }
                }
            }
            SystemFileSystem.atomicMove(Path(tmp), Path(target))
            emit(ModelStatus.Ready(target))
        } catch (e: Exception) {
            emit(ModelStatus.Failed(e.message ?: "Download failed"))
        } finally {
            client.close()
        }
    }

    private companion object {
        const val CHUNK = 64L * 1024L
    }
}
