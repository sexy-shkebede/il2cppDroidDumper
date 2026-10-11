package dev.il2cpp.dumper.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.il2cpp.dumper.app.DumperApp
import dev.il2cpp.dumper.app.data.AppSettings
import dev.il2cpp.dumper.app.data.DumpRecord
import dev.il2cpp.dumper.app.data.InstalledApp
import dev.il2cpp.dumper.app.data.SettingsRepository
import dev.il2cpp.dumper.app.data.toDumpConfig
import dev.il2cpp.dumper.app.data.DumpInput
import dev.il2cpp.dumper.app.work.DumpController
import dev.il2cpp.dumper.app.work.DumpService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Navigation destinations (a simple saveable back stack avoids a navigation dependency). */
sealed class Screen {
    data object Home : Screen()
    data object Apps : Screen()
    data object Running : Screen()
    data object Settings : Screen()
    data object DiffPicker : Screen()
    data class DiffResult(val oldId: String, val newId: String) : Screen()
    data class DumpDetail(val id: String) : Screen()
    data class ClassBrowser(val id: String) : Screen()
    data class TypeDetail(val id: String, val typeDefIndex: Int) : Screen()
    data class Viewer(val id: String, val fileName: String) : Screen()

    /** Metadata Doctor. [metaUri] string is a content:// URI to a .dat, or null to pick one. */
    data class Doctor(val metaUri: String? = null) : Screen()
}

data class AppsUiState(
    val loading: Boolean = false,
    val apps: List<InstalledApp> = emptyList(),
    val il2cppOnly: Boolean = true,
    val includeSystem: Boolean = false,
    val query: String = "",
)

class MainViewModel : ViewModel() {
    private val app = DumperApp.instance
    private val settingsRepo: SettingsRepository = app.settings
    val store = app.store
    val appsRepo = app.apps

    val settings: StateFlow<AppSettings> =
        settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _backStack = MutableStateFlow<List<Screen>>(listOf(Screen.Home))
    val backStack: StateFlow<List<Screen>> = _backStack.asStateFlow()
    val current: Screen get() = _backStack.value.last()

    private val _dumps = MutableStateFlow<List<DumpRecord>>(emptyList())
    val dumps: StateFlow<List<DumpRecord>> = _dumps.asStateFlow()

    private val _apps = MutableStateFlow(AppsUiState())
    val apps: StateFlow<AppsUiState> = _apps.asStateFlow()

    /** Cache of which installed apps are il2cpp (full scan result), keyed by package. */
    private val scanCache = HashMap<String, dev.il2cpp.dumper.core.extra.ApkScanResult?>()

    val dumpState: StateFlow<DumpController.State> = DumpController.state

    init {
        refreshDumps()
    }

    fun navigate(screen: Screen) {
        _backStack.value = _backStack.value + screen
    }

    fun popTo(screen: Screen) {
        _backStack.value = listOf(screen)
    }

    /** Returns false when nothing was popped (caller should finish the Activity). */
    fun pop(): Boolean {
        if (_backStack.value.size <= 1) return false
        _backStack.value = _backStack.value.dropLast(1)
        return true
    }

    fun refreshDumps() {
        viewModelScope.launch {
            _dumps.value = withContext(Dispatchers.IO) { store.list() }
        }
    }

    fun deleteDump(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.delete(id) }
            refreshDumps()
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepo.update(transform) }
    }

    fun loadApps(includeSystem: Boolean = _apps.value.includeSystem, il2cppOnly: Boolean = _apps.value.il2cppOnly) {
        _apps.value = _apps.value.copy(loading = true, includeSystem = includeSystem, il2cppOnly = il2cppOnly)
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                val all = appsRepo.list(includeSystem)
                if (!il2cppOnly) all
                else all.filter { a ->
                    val cached = scanCache[a.packageName]
                    if (cached != null) cached.isIl2Cpp
                    else {
                        val cheap = appsRepo.isLikelyIl2Cpp(a)
                        cheap ?: run {
                            val s = appsRepo.scan(a)
                            scanCache[a.packageName] = s
                            s?.isIl2Cpp == true
                        }
                    }
                }
            }
            _apps.value = _apps.value.copy(loading = false, apps = list)
        }
    }

    fun setAppsQuery(q: String) { _apps.value = _apps.value.copy(query = q) }

    /** Scans a chosen app and starts the dump; onNotIl2Cpp is called with a reason when unsupported. */
    fun startAppDump(appInfo: InstalledApp, onError: (String) -> Unit) {
        viewModelScope.launch {
            val scan = withContext(Dispatchers.IO) { scanCache[appInfo.packageName] ?: appsRepo.scan(appInfo).also { scanCache[appInfo.packageName] = it } }
            if (scan == null || !scan.isIl2Cpp) {
                onError(if (scan?.isMono == true) "${appInfo.label} is a Mono Unity app (no IL2CPP)." else "No IL2CPP content found in ${appInfo.label}.")
                return@launch
            }
            DumpService.start(app, DumpInput.App(appInfo, scan), settings.value.toDumpConfig())
            navigate(Screen.Running)
        }
    }

    fun startInputDump(input: DumpInput) {
        DumpService.start(app, input, settings.value.toDumpConfig())
        navigate(Screen.Running)
    }

    fun cancelDump() = DumpController.cancel()

    fun clearDumpState() = DumpController.reset()
}
