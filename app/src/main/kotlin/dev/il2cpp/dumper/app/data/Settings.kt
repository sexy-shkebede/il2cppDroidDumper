package dev.il2cpp.dumper.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val dumpMethod: Boolean = true,
    val dumpField: Boolean = true,
    val dumpProperty: Boolean = true,
    val dumpAttribute: Boolean = true,
    val dumpFieldOffset: Boolean = true,
    val dumpMethodOffset: Boolean = true,
    val dumpTypeDefIndex: Boolean = true,
    val generateStruct: Boolean = true,
    val generateScripts: Boolean = true,
    val generateOffsets: Boolean = true,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val theme = intPreferencesKey("theme_mode")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val dumpMethod = booleanPreferencesKey("dump_method")
        val dumpField = booleanPreferencesKey("dump_field")
        val dumpProperty = booleanPreferencesKey("dump_property")
        val dumpAttribute = booleanPreferencesKey("dump_attribute")
        val dumpFieldOffset = booleanPreferencesKey("dump_field_offset")
        val dumpMethodOffset = booleanPreferencesKey("dump_method_offset")
        val dumpTypeDefIndex = booleanPreferencesKey("dump_typedef_index")
        val generateStruct = booleanPreferencesKey("generate_struct")
        val generateScripts = booleanPreferencesKey("generate_scripts")
        val generateOffsets = booleanPreferencesKey("generate_offsets")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeMode = ThemeMode.entries[p[Keys.theme] ?: 0],
            dynamicColor = p[Keys.dynamic] ?: true,
            dumpMethod = p[Keys.dumpMethod] ?: true,
            dumpField = p[Keys.dumpField] ?: true,
            dumpProperty = p[Keys.dumpProperty] ?: true,
            dumpAttribute = p[Keys.dumpAttribute] ?: true,
            dumpFieldOffset = p[Keys.dumpFieldOffset] ?: true,
            dumpMethodOffset = p[Keys.dumpMethodOffset] ?: true,
            dumpTypeDefIndex = p[Keys.dumpTypeDefIndex] ?: true,
            generateStruct = p[Keys.generateStruct] ?: true,
            generateScripts = p[Keys.generateScripts] ?: true,
            generateOffsets = p[Keys.generateOffsets] ?: true,
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { p ->
            val cur = AppSettings(
                themeMode = ThemeMode.entries[p[Keys.theme] ?: 0],
                dynamicColor = p[Keys.dynamic] ?: true,
                dumpMethod = p[Keys.dumpMethod] ?: true,
                dumpField = p[Keys.dumpField] ?: true,
                dumpProperty = p[Keys.dumpProperty] ?: true,
                dumpAttribute = p[Keys.dumpAttribute] ?: true,
                dumpFieldOffset = p[Keys.dumpFieldOffset] ?: true,
                dumpMethodOffset = p[Keys.dumpMethodOffset] ?: true,
                dumpTypeDefIndex = p[Keys.dumpTypeDefIndex] ?: true,
                generateStruct = p[Keys.generateStruct] ?: true,
                generateScripts = p[Keys.generateScripts] ?: true,
                generateOffsets = p[Keys.generateOffsets] ?: true,
            )
            val n = transform(cur)
            p[Keys.theme] = n.themeMode.ordinal
            p[Keys.dynamic] = n.dynamicColor
            p[Keys.dumpMethod] = n.dumpMethod
            p[Keys.dumpField] = n.dumpField
            p[Keys.dumpProperty] = n.dumpProperty
            p[Keys.dumpAttribute] = n.dumpAttribute
            p[Keys.dumpFieldOffset] = n.dumpFieldOffset
            p[Keys.dumpMethodOffset] = n.dumpMethodOffset
            p[Keys.dumpTypeDefIndex] = n.dumpTypeDefIndex
            p[Keys.generateStruct] = n.generateStruct
            p[Keys.generateScripts] = n.generateScripts
            p[Keys.generateOffsets] = n.generateOffsets
        }
    }
}

fun AppSettings.toDumpConfig() = dev.il2cpp.dumper.core.Config(
    dumpMethod = dumpMethod,
    dumpField = dumpField,
    dumpProperty = dumpProperty,
    dumpAttribute = dumpAttribute,
    dumpFieldOffset = dumpFieldOffset,
    dumpMethodOffset = dumpMethodOffset,
    dumpTypeDefIndex = dumpTypeDefIndex,
    generateStruct = generateStruct,
    generateIndex = true,
    copyScripts = generateScripts,
    generateOffsetsHeader = generateOffsets,
)
