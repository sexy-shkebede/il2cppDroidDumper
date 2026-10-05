package dev.il2cpp.dumper.app.work

import dev.il2cpp.dumper.app.data.DumpInput
import dev.il2cpp.dumper.app.data.DumpRecord
import dev.il2cpp.dumper.app.data.LogLine
import dev.il2cpp.dumper.core.Config
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared, process-wide state of the single in-flight dump so the UI and the foreground service
 * observe the same thing and the dump survives configuration changes.
 */
object DumpController {
    sealed class State {
        data object Idle : State()
        data class Running(
            val title: String,
            val stage: String,
            val fraction: Float,
            val log: List<LogLine>,
        ) : State()
        data class Done(val recordId: String, val title: String) : State()
        data class Failed(val title: String, val error: String, val log: List<LogLine>) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile
    var cancelRequested = false
        private set

    // request to start, picked up by the ViewModel which owns the coroutine scope
    @Volatile
    var pending: Pair<DumpInput, Config>? = null

    fun requestStart(input: DumpInput, config: Config) {
        cancelRequested = false
        pending = input to config
    }

    fun cancel() {
        cancelRequested = true
    }

    fun setRunning(title: String, stage: String, fraction: Float, log: List<LogLine>) {
        _state.value = State.Running(title, stage, fraction, log)
    }

    fun setDone(record: DumpRecord) {
        _state.value = State.Done(record.id, record.title)
    }

    fun setFailed(title: String, error: String, log: List<LogLine>) {
        _state.value = State.Failed(title, error, log)
    }

    fun reset() {
        cancelRequested = false
        _state.value = State.Idle
    }
}
