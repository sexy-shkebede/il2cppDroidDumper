# Il2Cpp Dumper for Android

A native Android app that dumps Unity **IL2CPP** games right on the phone, with a
**Material 3 Expressive** interface. The dumping engine is a complete, byte-for-byte
Kotlin port of [Perfare's Il2CppDumper](https://github.com/Perfare/Il2CppDumper) `6.7.46`,
so the output is 100 % compatible with the desktop tool and its IDA / Ghidra / Binary Ninja
scripts — no PC required.

Open the project in **Android Studio** (Narwhal / AGP 9+) and press Run, or build from the
command line (see below).

---

## What it does

* **Pick a game three ways**
  * **Installed apps** – scans your installed apps, auto-detects IL2CPP (incl. split / OBB APKs)
    and shows only the ones that can be dumped.
  * **APK / XAPK / APKS / APKM / ZIP** – opens the archive in place (HTTP-style random access,
    no full extraction) and finds `libil2cpp.so` + `global-metadata.dat`, even across split APKs
    and even when the metadata was **renamed** (located by magic).
  * **Raw files** – a `libil2cpp.so` + `global-metadata.dat` pair, including memory dumps.
* **Everything the desktop tool outputs**, identical bytes: `dump.cs`, `il2cpp.h`,
  `script.json`, `stringliteral.json`, plus the `ida*.py`, `ghidra*.py` and `Il2CppBinaryNinja`
  scripts.
* Runs in a **foreground service** so large games keep dumping when you leave the app, with a
  live progress + log screen using the expressive **loading indicator**.

## Features that go beyond the original

| Feature | Why it helps |
|---|---|
| **In-app class browser & search** | Browse namespaces/types, search methods, fields, string literals, by name, `0xRVA`, or `/regex/` — no need to open `dump.cs` in another app. |
| **One-tap code generators** | For any method: **Frida** (raw) and **frida-il2cpp-bridge** hook scripts, and a **C++ hook template** (Dobby / ShadowHook / And64InlineHook). For any type: C# source, C++ field-offset layout, and an **`offsets.h`** header. |
| **Dump diff** | Compare two dumps of the same game (e.g. after an update) — added/removed types, **moved method RVAs** and changed field offsets, with a shareable report. Perfect for porting cheats/mods across versions. |
| **Android packed relocations (APS2)** | Applies `DT_ANDROID_REL(A)` packed relocations that many modern `libil2cpp.so` builds use — files the original tool mis-reads are handled correctly. |
| **Structured index** | Every dump also produces a compact `index.i2dx` that powers the browser, search, diff and code-gen instantly without re-parsing. |
| **Metadata inspector** | Validates `global-metadata.dat`, reports version, section sizes and truncation before you dump. |
| **Memory-dump mode** | Load a dumped `libil2cpp.so` with a base address to bypass simple protection (same approach as GameGuardian dumps). |
| **Material 3 Expressive UI** | Dynamic color (Android 12+), expressive motion & shapes, edge-to-edge, light/dark. |

## Project layout

```
Il2CppDumperDroid/
├── core/                    Pure Kotlin/JVM port of Il2CppDumper (no Android deps)
│   ├── …/core/io            Little-endian BinaryStream, UTF-8 writer matching .NET
│   ├── …/core/il2cpp        Metadata + Il2Cpp readers (v16–v31)
│   ├── …/core/formats       ELF32/64, Mach-O (+FAT), PE, NSO (+LZ4), WebAssembly
│   ├── …/core/outputs       dump.cs / il2cpp.h / script.json writers
│   ├── …/core/extra         APK/ZIP reader, code generators, diff, search, index
│   └── …/core/cli           Desktop CLI (`./gradlew :core:run`)
└── app/                     Android app (Jetpack Compose, Material 3 Expressive)
```

The `core` module has **zero Android dependencies** and is covered by JUnit tests that compare
its output byte-for-byte against the original C# tool.

## Building

Requirements: JDK 17, Android SDK with platform 37 & build-tools 37 (Android Studio installs
these automatically).

```bash
# Debug APK  ->  app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleDebug

# Release APK. Without a keystore it is debug-signed (still installable).
# For a real signing key, generate one and the build auto-detects it:
#   keytool -genkeypair -v -keystore app/dumper-release.jks -storepass dumper123 \
#     -keypass dumper123 -alias dumper -keyalg RSA -keysize 2048 -validity 10000 \
#     -dname "CN=Il2Cpp Dumper"
./gradlew :app:assembleRelease

# Run the dumper on a desktop (ELF/PE/Mach-O/NSO/WASM)
./gradlew :core:run --args="libil2cpp.so global-metadata.dat out/"

# Verify the port against the original tool's output
IL2CPP_SAMPLES=/path/to/samples ./gradlew :core:test
```

`compileSdk`/`targetSdk` is 37, `minSdk` is 26 (Android 8.0).

## Verification

The Kotlin core was checked against the C# `Il2CppDumper 6.7.46` on real games spanning
metadata **v24, v24.2, v27.1, v29 and v31**, both `arm64-v8a` and `armeabi-v7a`, in both
file and simulated memory-dump modes. In every case `dump.cs`, `il2cpp.h`, `script.json` and
`stringliteral.json` are **byte-identical**. See `core/src/test`.

## Credits & license

Dumping logic ported from **Perfare/Il2CppDumper** (MIT) — original license bundled at
`core/src/main/resources/scripts/LICENSE-Il2CppDumper.txt`. This project is provided for
reverse-engineering your own apps, security research, CTFs and interoperability. Respect the
license and terms of any software you analyze.
