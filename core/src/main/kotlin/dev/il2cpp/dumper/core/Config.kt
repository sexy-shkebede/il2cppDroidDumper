package dev.il2cpp.dumper.core

/** Port of Config.cs; defaults follow the config.json shipped with Il2CppDumper. */
data class Config(
    val dumpMethod: Boolean = true,
    val dumpField: Boolean = true,
    val dumpProperty: Boolean = true,
    val dumpAttribute: Boolean = true,
    val dumpFieldOffset: Boolean = true,
    val dumpMethodOffset: Boolean = true,
    val dumpTypeDefIndex: Boolean = true,
    val generateStruct: Boolean = true,
    val forceIl2CppVersion: Boolean = false,
    val forceVersion: Double = 24.3,
    val forceDump: Boolean = false,
    val noRedirectedPointer: Boolean = false,
    // ---- extensions ----
    /** Build the structured index used by the class browser / diff / code generators. */
    val generateIndex: Boolean = true,
    /** Copy IDA / Ghidra / Binary Ninja helper scripts next to script.json. */
    val copyScripts: Boolean = true,
    /** Generate frida-il2cpp offsets + C++ offsets header for all methods. */
    val generateOffsetsHeader: Boolean = true,
)
