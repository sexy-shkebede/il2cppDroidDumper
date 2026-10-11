package dev.il2cpp.dumper.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.il2cpp.dumper.app.ui.MainViewModel
import dev.il2cpp.dumper.app.ui.Screen

@Composable
fun AppShell(vm: MainViewModel) {
    val backStack by vm.backStack.collectAsStateWithLifecycle()
    val current = backStack.last()
    val showBottomBar = current is Screen.Home || current is Screen.Apps || current is Screen.Settings

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = current,
                transitionSpec = {
                    val forward = targetState.depth() >= initialState.depth()
                    val dir = if (forward) 1 else -1
                    (slideInHorizontally { it / 6 * dir } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 6 * dir } + fadeOut()) using SizeTransform(clip = false)
                },
                label = "screen",
            ) { screen ->
                when (screen) {
                    is Screen.Home -> HomeScreen(vm)
                    is Screen.Apps -> AppsScreen(vm)
                    is Screen.Running -> RunningScreen(vm)
                    is Screen.Settings -> SettingsScreen(vm)
                    is Screen.DumpDetail -> DumpDetailScreen(vm, screen.id)
                    is Screen.ClassBrowser -> ClassBrowserScreen(vm, screen.id)
                    is Screen.TypeDetail -> TypeDetailScreen(vm, screen.id, screen.typeDefIndex)
                    is Screen.Viewer -> ViewerScreen(vm, screen.id, screen.fileName)
                    is Screen.DiffPicker -> DiffPickerScreen(vm)
                    is Screen.DiffResult -> DiffResultScreen(vm, screen.oldId, screen.newId)
                    is Screen.Doctor -> MetadataDoctorScreen(vm, screen.metaUri)
                }
            }

            if (showBottomBar) {
                androidx.compose.foundation.layout.Column(Modifier.align(androidx.compose.ui.Alignment.BottomCenter)) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = current is Screen.Home,
                            onClick = { vm.popTo(Screen.Home) },
                            icon = { Icon(Icons.Rounded.Home, null) },
                            label = { Text("Home") },
                        )
                        NavigationBarItem(
                            selected = current is Screen.Apps,
                            onClick = { if (current !is Screen.Apps) { vm.popTo(Screen.Home); vm.navigate(Screen.Apps) } },
                            icon = { Icon(Icons.Rounded.Apps, null) },
                            label = { Text("Apps") },
                        )
                        NavigationBarItem(
                            selected = current is Screen.Settings,
                            onClick = { if (current !is Screen.Settings) { vm.popTo(Screen.Home); vm.navigate(Screen.Settings) } },
                            icon = { Icon(Icons.Rounded.Settings, null) },
                            label = { Text("Settings") },
                        )
                    }
                }
            }
        }
    }
}

private fun Screen.depth(): Int = when (this) {
    is Screen.Home -> 0
    is Screen.Apps, is Screen.Settings -> 1
    is Screen.Running -> 1
    is Screen.DumpDetail -> 2
    is Screen.DiffPicker -> 2
    is Screen.ClassBrowser, is Screen.Viewer, is Screen.DiffResult -> 3
    is Screen.Doctor -> 3
    is Screen.TypeDetail -> 4
}
