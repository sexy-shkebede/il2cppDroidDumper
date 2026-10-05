package dev.il2cpp.dumper.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.il2cpp.dumper.app.BuildConfig
import dev.il2cpp.dumper.app.data.ThemeMode
import dev.il2cpp.dumper.app.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { androidx.compose.material3.TopAppBar(title = { Text("Settings") }) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(), bottom = 120.dp)) {
            item { Group("Appearance") }
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    val modes = listOf(ThemeMode.SYSTEM to Icons.Rounded.SettingsBrightness, ThemeMode.LIGHT to Icons.Rounded.LightMode, ThemeMode.DARK to Icons.Rounded.DarkMode)
                    modes.forEachIndexed { i, (mode, icon) ->
                        SegmentedButton(
                            selected = s.themeMode == mode,
                            onClick = { vm.updateSettings { it.copy(themeMode = mode) } },
                            shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                            icon = { Icon(icon, null, Modifier.height(18.dp)) },
                        ) { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    }
                }
            }
            item {
                Toggle("Dynamic color", "Use the wallpaper palette (Android 12+)", s.dynamicColor, Icons.Rounded.Palette) {
                    vm.updateSettings { it.copy(dynamicColor = !it.dynamicColor) }
                }
            }

            item { Group("What to dump") }
            item { Toggle("Methods", null, s.dumpMethod) { vm.updateSettings { it.copy(dumpMethod = !it.dumpMethod) } } }
            item { Toggle("Fields", null, s.dumpField) { vm.updateSettings { it.copy(dumpField = !it.dumpField) } } }
            item { Toggle("Properties", null, s.dumpProperty) { vm.updateSettings { it.copy(dumpProperty = !it.dumpProperty) } } }
            item { Toggle("Attributes", null, s.dumpAttribute) { vm.updateSettings { it.copy(dumpAttribute = !it.dumpAttribute) } } }
            item { Toggle("Field offsets", null, s.dumpFieldOffset) { vm.updateSettings { it.copy(dumpFieldOffset = !it.dumpFieldOffset) } } }
            item { Toggle("Method offsets (RVA)", null, s.dumpMethodOffset) { vm.updateSettings { it.copy(dumpMethodOffset = !it.dumpMethodOffset) } } }
            item { Toggle("TypeDefIndex comments", null, s.dumpTypeDefIndex) { vm.updateSettings { it.copy(dumpTypeDefIndex = !it.dumpTypeDefIndex) } } }

            item { Group("Extra outputs") }
            item { Toggle("Generate il2cpp.h + script.json", "structs for IDA / Ghidra / Binary Ninja", s.generateStruct) { vm.updateSettings { it.copy(generateStruct = !it.generateStruct) } } }
            item { Toggle("Copy disassembler scripts", "ida.py, ghidra.py, Il2CppBinaryNinja…", s.generateScripts) { vm.updateSettings { it.copy(generateScripts = !it.generateScripts) } } }
            item { Toggle("Generate offsets.h", "C++ offsets header for all methods", s.generateOffsets) { vm.updateSettings { it.copy(generateOffsets = !it.generateOffsets) } } }

            item { Group("About") }
            item {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text("Il2Cpp Dumper for Android", style = MaterialTheme.typography.titleSmall)
                    Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Text("Full Kotlin port of Perfare's Il2CppDumper 6.7.46, verified byte-for-byte against the original across metadata v24–v31. Output is 100% compatible with the desktop scripts.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Group(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
}

@Composable
private fun Toggle(title: String, subtitle: String?, checked: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}
