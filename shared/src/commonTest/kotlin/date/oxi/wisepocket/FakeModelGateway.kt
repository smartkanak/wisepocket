package date.oxi.wisepocket

import date.oxi.wisepocket.llm.ModelGateway
import date.oxi.wisepocket.llm.ModelStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A [ModelGateway] whose state the test sets directly, standing in for a gigabyte of GGUF. */
class FakeModelGateway(initial: ModelStatus = ModelStatus.Absent("/fake/model.gguf")) : ModelGateway {

    private val _status = MutableStateFlow(initial)
    override val status: StateFlow<ModelStatus> = _status.asStateFlow()

    var ensureCalls = 0
        private set

    override suspend fun ensure(downloadUrl: String?, authToken: String?) {
        ensureCalls++
    }

    /** Simulates the model finishing its download — the moment the app used to ignore. */
    fun becomeReady() {
        _status.value = ModelStatus.Ready("/fake/model.gguf")
    }
}
