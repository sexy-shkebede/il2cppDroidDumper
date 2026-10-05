package dev.il2cpp.dumper.app.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import dev.il2cpp.dumper.core.extra.ApkInspector
import java.io.File

data class InstalledApp(
    val label: String,
    val packageName: String,
    val versionName: String,
    val baseApk: File,
    val splitApks: List<File>,
    val nativeLibDir: File?,
    val isGame: Boolean,
    val isSystem: Boolean,
    val lastUpdate: Long,
) {
    val allApks: List<File> get() = listOf(baseApk) + splitApks
}

class InstalledAppsRepository(private val context: Context) {
    private val pm: PackageManager = context.packageManager

    fun icon(packageName: String): Drawable? = runCatching { pm.getApplicationIcon(packageName) }.getOrNull()

    /** Lists installed apps; the heavy il2cpp detection is done lazily by [isLikelyIl2Cpp]. */
    fun list(includeSystem: Boolean): List<InstalledApp> {
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA or PackageManager.GET_SHARED_LIBRARY_FILES)
        val result = ArrayList<InstalledApp>(packages.size)
        for (info in packages) {
            val isSystem = info.flags and ApplicationInfo.FLAG_SYSTEM != 0
            if (!includeSystem && isSystem) continue
            if (info.packageName == context.packageName) continue
            val base = File(info.sourceDir)
            if (!base.exists()) continue
            val splits = info.splitSourceDirs?.map { File(it) }?.filter { it.exists() } ?: emptyList()
            val pkgInfo = runCatching { pm.getPackageInfo(info.packageName, 0) }.getOrNull()
            val isGame = info.category == ApplicationInfo.CATEGORY_GAME ||
                (info.flags and ApplicationInfo.FLAG_IS_GAME != 0)
            result.add(
                InstalledApp(
                    label = runCatching { pm.getApplicationLabel(info).toString() }.getOrDefault(info.packageName),
                    packageName = info.packageName,
                    versionName = pkgInfo?.versionName ?: "",
                    baseApk = base,
                    splitApks = splits,
                    nativeLibDir = info.nativeLibraryDir?.let { File(it) }?.takeIf { it.isDirectory },
                    isGame = isGame,
                    isSystem = isSystem,
                    lastUpdate = pkgInfo?.lastUpdateTime ?: 0L,
                )
            )
        }
        return result.sortedBy { it.label.lowercase() }
    }

    /**
     * Cheap check: an extracted native lib dir with libil2cpp.so, or il2cpp content inside the base APK.
     * Returns null when it cannot tell cheaply (caller may do a full scan).
     */
    fun isLikelyIl2Cpp(app: InstalledApp): Boolean? {
        app.nativeLibDir?.let { dir ->
            if (File(dir, "libil2cpp.so").exists()) return true
            if (dir.listFiles()?.any { it.name == "libunity.so" } == true &&
                dir.listFiles()?.none { it.name == "libil2cpp.so" } == true) {
                // Unity present; il2cpp lib may be inside the APK (uncompressed) — needs a scan
                return null
            }
        }
        return null
    }

    fun scan(app: InstalledApp): dev.il2cpp.dumper.core.extra.ApkScanResult? {
        for (apk in app.allApks) {
            val r = runCatching { ApkInspector.scan(apk) }.getOrNull() ?: continue
            if (r.isIl2Cpp) return r
        }
        // fall back to extracted native libs
        val lib = app.nativeLibDir?.let { File(it, "libil2cpp.so") }
        if (lib?.exists() == true) {
            return runCatching { ApkInspector.scan(app.baseApk) }.getOrNull()
        }
        return null
    }
}
