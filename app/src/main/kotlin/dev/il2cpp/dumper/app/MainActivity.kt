package dev.il2cpp.dumper.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.il2cpp.dumper.app.data.ThemeMode
import dev.il2cpp.dumper.app.ui.MainViewModel
import dev.il2cpp.dumper.app.ui.Screen
import dev.il2cpp.dumper.app.ui.screens.AppShell
import dev.il2cpp.dumper.app.ui.theme.Il2CppDumperTheme
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        val openDump = intent?.getStringExtra("openDump")

        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            LaunchedEffect(openDump) {
                if (!openDump.isNullOrEmpty() && vm.store.get(openDump) != null) {
                    vm.navigate(Screen.DumpDetail(openDump))
                }
            }
            Il2CppDumperTheme(darkTheme = dark, dynamicColor = settings.dynamicColor) {
                BackHandler(enabled = true) {
                    if (!vm.pop()) finish()
                }
                AppShell(vm)
            }
        }
    }
}
