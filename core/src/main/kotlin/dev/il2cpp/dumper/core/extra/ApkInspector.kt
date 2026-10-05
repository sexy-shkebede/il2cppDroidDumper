package dev.il2cpp.dumper.core.extra

import java.io.File

/** Path to an entry, possibly nested: ["config.arm64_v8a.apk", "lib/arm64-v8a/libil2cpp.so"]. */
data class EntryPath(val parts: List<String>) {
    val name: String get() = parts.last()
    override fun toString(): String = parts.joinToString(" › ")
}

data class LibCandidate(val abi: String, val path: EntryPath, val size: Long)

data class MetadataCandidate(
    val path: EntryPath,
    val size: Long,
    /** Version from the header, or null when the header is not a valid il2cpp metadata header. */
    val version: Int?,
    val renamed: Boolean,
) {
    val looksValid: Boolean get() = version != null
}

data class ApkScanResult(
    val libs: List<LibCandidate>,
    val metadata: List<MetadataCandidate>,
    val unityVersion: String?,
    val isMono: Boolean,
    val nestedApks: List<String>,
    val gameAssemblies: List<EntryPath>,
) {
    val isIl2Cpp: Boolean get() = libs.isNotEmpty() || gameAssemblies.isNotEmpty() || metadata.isNotEmpty()

    fun bestLib(preferredAbis: List<String>): LibCandidate? {
        for (abi in preferredAbis) libs.firstOrNull { it.abi == abi }?.let { return it }
        return libs.firstOrNull()
    }

    fun bestMetadata(): MetadataCandidate? =
        metadata.firstOrNull { it.looksValid && !it.renamed } ?: metadata.firstOrNull { it.looksValid } ?: metadata.firstOrNull()
}

/**
 * Finds the il2cpp binary and global-metadata.dat inside APK / XAPK / APKS / APKM / ZIP bundles,
 * including split APKs and renamed metadata files (located by magic).
 */
object ApkInspector {
    private val libRegex = Regex("^lib/([^/]+)/libil2cpp\\.so$")
    private val unityRegex = Regex("(?<![0-9])((?:20[1-9][0-9]|[56]|6000)\\.[0-9]{1,2}\\.[0-9]{1,3}[abfpcx][0-9]{1,3})")
    private val unityVersionFiles = listOf(
        "assets/bin/Data/globalgamemanagers", "assets/bin/Data/data.unity3d", "assets/bin/Data/unity default resources",
        "assets/bin/Data/mainData", "assets/bin/Data/level0", "assets/bin/Data/Resources/unity_builtin_extra",
        "Data/globalgamemanagers", "Data/data.unity3d",
    )
    val ABI_ORDER = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86", "armeabi")

    fun scan(file: File, deep: Boolean = true): ApkScanResult = ZipReader(SeekableSource.of(file)).use { scan(it, deep) }

    fun scan(zip: ZipReader, deep: Boolean = true): ApkScanResult {
        val libs = ArrayList<LibCandidate>()
        val metas = ArrayList<MetadataCandidate>()
        val nested = ArrayList<String>()
        val assemblies = ArrayList<EntryPath>()
        var unity: String? = null
        var mono = false

        fun visit(reader: ZipReader, prefix: List<String>, depth: Int) {
            for (e in reader.entries) {
                if (e.isDirectory) continue
                val name = e.name
                val path = EntryPath(prefix + name)
                libRegex.find(name)?.let { m -> libs.add(LibCandidate(m.groupValues[1], path, e.size)); return@let }
                if (name.endsWith("GameAssembly.dll") || name.endsWith("UserAssembly.dll")) assemblies.add(path)
                if (name.endsWith("global-metadata.dat")) {
                    metas.add(MetadataCandidate(path, e.size, headerVersion(runCatching { reader.readHead(e, 8) }.getOrNull()), renamed = false))
                }
                if (name.endsWith("/Managed/Assembly-CSharp.dll")) mono = true
                if (unity == null && unityVersionFiles.any { name.endsWith(it) }) {
                    unity = runCatching { detectUnityVersion(reader.readHead(e, 4096)) }.getOrNull()
                }
                if (depth == 0 && (name.endsWith(".apk") || name.endsWith(".zip")) && !name.contains('/')) {
                    val inner = runCatching { reader.openNested(e) }.getOrNull()
                    if (inner != null) {
                        nested.add(name)
                        runCatching { visit(inner, prefix + name, depth + 1) }
                    }
                }
            }
            if (deep && metas.none { it.path.parts.dropLast(1) == prefix && it.looksValid }) {
                // look for renamed / moved metadata by magic
                for (e in reader.entries) {
                    if (e.isDirectory || e.size < 0x400 || e.name.endsWith("global-metadata.dat")) continue
                    if (!(e.name.startsWith("assets/") || e.name.contains("Data/"))) continue
                    if (e.name.endsWith(".so") || e.name.endsWith(".dex") || e.name.endsWith(".png") || e.name.endsWith(".ogg")) continue
                    val head = runCatching { reader.readHead(e, 8) }.getOrNull() ?: continue
                    val ver = headerVersion(head)
                    if (ver != null) metas.add(MetadataCandidate(EntryPath(prefix + e.name), e.size, ver, renamed = true))
                }
            }
        }
        visit(zip, emptyList(), 0)
        libs.sortBy { ABI_ORDER.indexOf(it.abi).let { i -> if (i < 0) 99 else i } }
        return ApkScanResult(libs, metas, unity, mono && libs.isEmpty(), nested, assemblies)
    }

    fun headerVersion(head: ByteArray?): Int? {
        if (head == null || head.size < 8) return null
        val magic = (head[0].toLong() and 0xFF) or ((head[1].toLong() and 0xFF) shl 8) or ((head[2].toLong() and 0xFF) shl 16) or ((head[3].toLong() and 0xFF) shl 24)
        if (magic != 0xFAB11BAFL) return null
        val ver = (head[4].toInt() and 0xFF) or ((head[5].toInt() and 0xFF) shl 8) or ((head[6].toInt() and 0xFF) shl 16) or ((head[7].toInt() and 0xFF) shl 24)
        return if (ver in 16..40) ver else null
    }

    fun detectUnityVersion(head: ByteArray): String? {
        val text = String(head, Charsets.ISO_8859_1)
        return unityRegex.find(text)?.groupValues?.get(1)
    }

    /** Opens the zip reader that contains [path] (following nested APKs) and returns it with the entry. */
    fun resolve(root: ZipReader, path: EntryPath): Pair<ZipReader, ZipEntryInfo> {
        var reader = root
        for (i in 0 until path.parts.size - 1) {
            val e = reader.entry(path.parts[i]) ?: error("Entry not found: ${path.parts[i]}")
            reader = reader.openNested(e) ?: error("Nested archive is compressed: ${path.parts[i]}")
        }
        val entry = reader.entry(path.parts.last()) ?: error("Entry not found: ${path.parts.last()}")
        return reader to entry
    }

    fun extract(root: ZipReader, path: EntryPath, target: File, onProgress: (Long, Long) -> Unit = { _, _ -> }) {
        val (reader, entry) = resolve(root, path)
        reader.extract(entry, target, onProgress)
    }
}
