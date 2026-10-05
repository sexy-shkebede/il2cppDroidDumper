# Core uses reflection-free code; keep the public API and script resources.
-keep class dev.il2cpp.dumper.core.** { *; }
-keepattributes *Annotation*
-dontwarn org.jetbrains.annotations.**
