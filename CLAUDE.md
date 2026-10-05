# Il2CppDumperDroid — notes for future work

Two Gradle modules:

- **`:core`** — pure Kotlin/JVM port of Perfare's Il2CppDumper 6.7.46. No Android APIs.
  The port is **byte-for-byte faithful** to the C# tool; when changing anything that can
  affect `dump.cs` / `il2cpp.h` / `script.json` / `stringliteral.json`, re-run the reference
  tests and keep them byte-identical:
  `IL2CPP_SAMPLES=<dir> ./gradlew :core:test`
  (each `<dir>/<name>/` has `libil2cpp.so`, `global-metadata.dat`, a `reference/` folder with
  the original tool's output, and optional `dumpaddr.txt` for memory dumps).
  `:core` also has Android-free extras in `core/.../extra`: `ZipReader`/`ApkInspector`
  (random-access APK/XAPK reader), `CodeGen` (Frida/C++/offsets), `DumpDiff`, `SearchEngine`,
  `DumpIndex` (the `index.i2dx` serialized model), `MetadataTools`.
- **`:app`** — Jetpack Compose, Material 3 Expressive. Uses AGP 9 built-in Kotlin (do NOT add
  the `kotlin-android` plugin). Navigation is a saveable back-stack in `MainViewModel`
  (no navigation-compose dependency). The dump runs in `DumpService` (foreground) and state is
  shared through `DumpController`.

Key compatibility details that must not regress:
- `.NET` float/double `ToString` and `System.Text.Json` escaping are reimplemented in
  `core/.../utils/CSharp.kt` and `outputs/JsonWriter.kt`; `FormattingTest` guards them.
- All pointer-sized reads go through `readIntPtr`/`readUIntPtr`; unsigned comparisons use the
  helpers in `utils/SearchHelpers.kt`.
- The il2cpp binary is mapped copy-on-write; relocations are applied in place but the source
  file is never modified.

Toolchain: JDK 17, Android SDK platform/build-tools 37, AGP 9.4.1, Kotlin 2.4.20,
Compose BOM 2026.09.00, Material3 1.5.0-alpha29 (expressive APIs), Gradle 9.8.
