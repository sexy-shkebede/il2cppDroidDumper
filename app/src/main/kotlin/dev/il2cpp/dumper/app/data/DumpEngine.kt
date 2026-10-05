package dev.il2cpp.dumper.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dev.il2cpp.dumper.core.Config
import dev.il2cpp.dumper.core.DumpException
import dev.il2cpp.dumper.core.DumpLogger
import dev.il2cpp.dumper.core.DumpProgress
import dev.il2cpp.dumper.core.DumpResult
import dev.il2cpp.dumper.core.Dumper
import dev.il2cpp.dumper.core.SourceInfo
import dev.il2cpp.dumper.core.extra.ApkInspector
import dev.il2cpp.dumper.core.extra.ApkScanResult
import dev.il2cpp.dumper.core.extra.EntryPath
import dev.il2cpp.dumper.core.extra.SeekableSource
import dev.il2cpp.dumper.core.extra.ZipReader
import org.json.JSONObject
import java.io.File

/** A log line with a severity, surfaced in the running-dump UI. */
data class LogLine(val message: String, val level: Level) {
    enum class Level { INFO, WARN, ERROR, STEP }
}

/** Progress callback for the UI. */
interface DumpUi {
    fun stage(text: String, fraction: Float)
    fun log(line: LogLine)

    /** Checked from inside the long-running dump; return true to abort. */
    fun isCancelled(): Boolean = false
}

sealed class DumpInput {
    /** A content Uri pointing at an .apk/.xapk/.so or .dat. */
    data class Content(val uri: Uri) : DumpInput()

    /** An installed app. */
    data class App(val app: InstalledApp, val scan: ApkScanResult) : DumpInput()

    /** Explicit files already on disk (e.g. a manual pair, or a root memory dump). */
    data class Files(
        val binary: File,
        val metadata: File,
        val title: String = "",
        val packageName: String = "",
        val unityVersion: String = "",
        val dumpBaseAddress: Long = 0L,
    ) : DumpInput()
}

class DumpEngine(private val context: Context, private val store: DumpStore) {

    suspend fun run(input: DumpInput, config: Config, ui: DumpUi): DumpRecord {
        val work = File(context.cacheDir, "work_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val prepared = prepare(input, work, ui)
            ui.log(LogLine("il2cpp: ${prepared.binary.name} (${prepared.binary.length() / 1024} KB)", LogLine.Level.INFO))
            ui.log(LogLine("metadata: ${prepared.metadata.name} (${prepared.metadata.length() / 1024} KB)", LogLine.Level.INFO))

            val outDir = store.newDumpDir(prepared.title.ifEmpty { prepared.binary.nameWithoutExtension })
            val logger = DumpLogger { msg -> ui.log(LogLine(msg, classify(msg))) }
            val progress = object : DumpProgress {
                override fun onProgress(stage: String, fraction: Float) = ui.stage(stage, fraction)
                override fun checkCancelled() {
                    if (ui.isCancelled()) throw java.util.concurrent.CancellationException("Cancelled by user")
                }
            }
            val interaction = object : dev.il2cpp.dumper.core.DumpInteraction {
                override fun askDumpAddress() = prepared.dumpBaseAddress
            }
            val result: DumpResult = try {
                Dumper(config, logger, progress, interaction).dump(
                    prepared.binary, prepared.metadata, outDir,
                    SourceInfo(prepared.title, prepared.packageName, prepared.unityVersion),
                )
            } catch (e: DumpException) {
                outDir.deleteRecursively()
                throw e
            }

            val meta = JSONObject().apply {
                put("title", prepared.title.ifEmpty { prepared.binary.nameWithoutExtension })
                put("packageName", prepared.packageName)
                put("unityVersion", prepared.unityVersion)
                put("metadataVersion", dev.il2cpp.dumper.core.il2cpp.Il2Cpp.fmtVersion(result.metadataVersion))
                put("il2cppVersion", dev.il2cpp.dumper.core.il2cpp.Il2Cpp.fmtVersion(result.il2cppVersion))
                put("arch", result.arch)
                put("format", result.formatName)
                put("createdAt", System.currentTimeMillis())
                put("typeCount", result.typeCount)
                put("methodCount", result.methodCount)
                put("stringCount", result.stringLiteralCount)
                put("imageCount", result.imageCount)
                put("isDumped", result.isDumped)
                put("maybeProtected", result.maybeProtected)
                put("codeRegistration", java.lang.Long.toHexString(result.codeRegistration))
                put("metadataRegistration", java.lang.Long.toHexString(result.metadataRegistration))
                put("elapsedMs", result.elapsedMs)
            }
            DumpRecord.writeMeta(outDir, meta)
            ui.log(LogLine("Done: ${result.typeCount} types, ${result.methodCount} methods in ${result.elapsedMs} ms", LogLine.Level.STEP))
            return DumpRecord(outDir)
        } finally {
            work.deleteRecursively()
        }
    }

    private data class Prepared(
        val binary: File,
        val metadata: File,
        val title: String,
        val packageName: String,
        val unityVersion: String,
        val dumpBaseAddress: Long,
    )

    private suspend fun prepare(input: DumpInput, work: File, ui: DumpUi): Prepared {
        when (input) {
            is DumpInput.Files -> return Prepared(input.binary, input.metadata, input.title, input.packageName, input.unityVersion, input.dumpBaseAddress)

            is DumpInput.App -> {
                ui.stage("Extracting from ${input.app.label}", -1f)
                return extractFromApks(input.app.allApks, input.scan, work, input.app.label, input.app.packageName)
            }

            is DumpInput.Content -> {
                val name = queryName(input.uri)
                ui.stage("Reading $name", -1f)
                val lower = name.lowercase()
                return when {
                    lower.endsWith(".dat") || lower.endsWith(".bin") -> {
                        // a lone metadata file: caller must pair it; we copy and ask for the binary separately
                        throw DumpException("Select an APK or a libil2cpp.so + global-metadata.dat pair.")
                    }
                    lower.endsWith(".so") -> {
                        throw DumpException("A lone libil2cpp.so needs a global-metadata.dat. Use the APK or pick both files.")
                    }
                    else -> {
                        val apk = copyUriToFile(input.uri, File(work, if (lower.endsWith(".xapk") || lower.endsWith(".apks") || lower.endsWith(".apkm")) "input.xapk" else "input.apk"), ui)
                        ui.log(LogLine("Scanning archive…", LogLine.Level.STEP))
                        ZipReader(SeekableSource.of(apk)).use { zr ->
                            val scan = ApkInspector.scan(zr)
                            if (!scan.isIl2Cpp) {
                                if (scan.isMono) throw DumpException("This is a Mono (not IL2CPP) Unity app — nothing to dump.")
                                throw DumpException("No libil2cpp.so / global-metadata.dat found in this archive.")
                            }
                            extractFromReader(zr, scan, work, name.substringBeforeLast('.'), "")
                        }
                    }
                }
            }
        }
    }

    private suspend fun extractFromApks(apks: List<File>, scan: ApkScanResult, work: File, title: String, pkg: String): Prepared {
        // A scan result may span several split APKs; resolve each entry against the APK that holds it.
        val lib = scan.bestLib(ApkInspector.ABI_ORDER) ?: throw DumpException("No libil2cpp.so in the app.")
        val meta = scan.bestMetadata() ?: throw DumpException("No global-metadata.dat in the app.")
        val binFile = File(work, "libil2cpp.so")
        val metaFile = File(work, "global-metadata.dat")
        copyEntryFromApks(apks, lib.path, binFile)
        copyEntryFromApks(apks, meta.path, metaFile)
        return Prepared(binFile, metaFile, title, pkg, scan.unityVersion ?: "", 0L)
    }

    private fun extractFromReader(zr: ZipReader, scan: ApkScanResult, work: File, title: String, pkg: String): Prepared {
        val lib = scan.bestLib(ApkInspector.ABI_ORDER) ?: throw DumpException("No libil2cpp.so in the archive.")
        val meta = scan.bestMetadata() ?: throw DumpException("No global-metadata.dat in the archive.")
        val binFile = File(work, "libil2cpp.so")
        val metaFile = File(work, "global-metadata.dat")
        ApkInspector.extract(zr, lib.path, binFile)
        ApkInspector.extract(zr, meta.path, metaFile)
        return Prepared(binFile, metaFile, title, pkg, scan.unityVersion ?: "", 0L)
    }

    private fun copyEntryFromApks(apks: List<File>, path: EntryPath, target: File) {
        for (apk in apks) {
            ZipReader(SeekableSource.of(apk)).use { zr ->
                val entry = runCatching { ApkInspector.resolve(zr, path) }.getOrNull() ?: return@use
                entry.first.extract(entry.second, target)
            }
            if (target.exists() && target.length() > 0) return
        }
        if (!target.exists()) error("Entry not found in any APK: $path")
    }

    private fun queryName(uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) return c.getString(idx)
        }
        return uri.lastPathSegment ?: "input"
    }

    private fun copyUriToFile(uri: Uri, target: File, ui: DumpUi): File {
        val total = runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && c.moveToFirst()) c.getLong(idx) else -1L
            } ?: -1L
        }.getOrDefault(-1L)
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().buffered(1 shl 16).use { out ->
                val buf = ByteArray(1 shl 16)
                var copied = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    copied += n
                    if (total > 0) ui.stage("Copying ${copied / 1024 / 1024} / ${total / 1024 / 1024} MB", copied.toFloat() / total)
                }
            }
        } ?: throw DumpException("Cannot open the selected file.")
        return target
    }

    private fun classify(msg: String): LogLine.Level = when {
        msg.startsWith("ERROR") -> LogLine.Level.ERROR
        msg.startsWith("WARNING") -> LogLine.Level.WARN
        msg.startsWith("Done") || msg.startsWith("Dumping") || msg.startsWith("Searching") ||
            msg.startsWith("Generate") || msg.startsWith("Initializing") -> LogLine.Level.STEP
        else -> LogLine.Level.INFO
    }
}
