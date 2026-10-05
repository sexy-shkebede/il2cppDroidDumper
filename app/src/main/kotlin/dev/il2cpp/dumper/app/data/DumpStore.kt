package dev.il2cpp.dumper.app.data

import android.content.Context
import dev.il2cpp.dumper.core.extra.DumpIndex
import org.json.JSONObject
import java.io.File

/** A finished dump on disk: a directory with dump.cs, il2cpp.h, script.json, index.i2dx and meta.json. */
class DumpRecord(val dir: File) {
    val meta: JSONObject = runCatching { JSONObject(File(dir, META).readText()) }.getOrDefault(JSONObject())

    val id: String get() = dir.name
    val title: String get() = meta.optString("title", dir.name)
    val packageName: String get() = meta.optString("packageName")
    val unityVersion: String get() = meta.optString("unityVersion")
    val metadataVersion: String get() = meta.optString("metadataVersion")
    val il2cppVersion: String get() = meta.optString("il2cppVersion")
    val arch: String get() = meta.optString("arch")
    val format: String get() = meta.optString("format")
    val createdAt: Long get() = meta.optLong("createdAt")
    val typeCount: Int get() = meta.optInt("typeCount")
    val methodCount: Int get() = meta.optInt("methodCount")
    val stringCount: Int get() = meta.optInt("stringCount")
    val isDumped: Boolean get() = meta.optBoolean("isDumped")
    val maybeProtected: Boolean get() = meta.optBoolean("maybeProtected")

    val dumpCs: File get() = File(dir, "dump.cs")
    val header: File get() = File(dir, "il2cpp.h")
    val scriptJson: File get() = File(dir, "script.json")
    val indexFile: File get() = File(dir, DumpIndex.FILE_NAME)

    fun hasIndex(): Boolean = indexFile.isFile

    fun loadIndex(): DumpIndex = DumpIndex.load(indexFile)

    fun outputFiles(): List<File> = dir.listFiles()
        ?.filter { it.isFile && it.name != META }
        ?.sortedBy { it.name } ?: emptyList()

    fun sizeBytes(): Long = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    companion object {
        const val META = "meta.json"

        fun writeMeta(dir: File, json: JSONObject) {
            File(dir, META).writeText(json.toString(2))
        }
    }
}

class DumpStore(context: Context) {
    val root: File = File(context.filesDir, "dumps").apply { mkdirs() }

    fun newDumpDir(baseName: String): File {
        val safe = baseName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(60).ifEmpty { "dump" }
        var dir = File(root, "${safe}_${System.currentTimeMillis()}")
        var i = 1
        while (dir.exists()) dir = File(root, "${safe}_${System.currentTimeMillis()}_${i++}")
        dir.mkdirs()
        return dir
    }

    fun list(): List<DumpRecord> = root.listFiles()
        ?.filter { it.isDirectory && File(it, DumpRecord.META).isFile }
        ?.map { DumpRecord(it) }
        ?.sortedByDescending { it.createdAt } ?: emptyList()

    fun get(id: String): DumpRecord? {
        val dir = File(root, id)
        return if (dir.isDirectory && File(dir, DumpRecord.META).isFile) DumpRecord(dir) else null
    }

    fun delete(id: String) {
        File(root, id).deleteRecursively()
    }
}
