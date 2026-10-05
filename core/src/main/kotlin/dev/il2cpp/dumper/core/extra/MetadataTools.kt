package dev.il2cpp.dumper.core.extra

import dev.il2cpp.dumper.core.il2cpp.Il2CppGlobalMetadataHeader
import dev.il2cpp.dumper.core.il2cpp.Metadata
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Read-only inspection of a global-metadata.dat file (shown in the app before dumping). */
object MetadataTools {
    const val MAGIC = 0xFAB11BAFL

    data class Report(
        val valid: Boolean,
        val version: Int?,
        val detailedVersion: String?,
        val headerSize: Int,
        val dataSize: Long,
        val images: Int,
        val types: Int,
        val methods: Int,
        val stringLiterals: Int,
        val notes: List<String>,
    )

    private fun u32(b: ByteBuffer, off: Int): Long = b.getInt(off).toLong() and 0xFFFFFFFFL

    /** Total byte size described by the header = end of its furthest section. */
    fun dataSize(buf: ByteBuffer, version: Double): Long {
        val size = Il2CppGlobalMetadataHeader.size(version)
        var max = 0L
        var p = 8
        while (p + 8 <= size && p + 8 <= buf.limit()) {
            val off = u32(buf, p)
            val len = buf.getInt(p + 4).toLong()
            if (len > 0) max = maxOf(max, off + len)
            p += 8
        }
        return max
    }

    fun analyze(bytes: ByteBuffer): Report {
        val buf = bytes.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val notes = ArrayList<String>()
        if (buf.limit() < 0x110) {
            return Report(false, null, null, 0, 0, 0, 0, 0, 0, listOf("File is too small to be global-metadata.dat"))
        }
        val magic = u32(buf, 0)
        val version = buf.getInt(4)
        if (magic != MAGIC) {
            notes.add("Not a standard il2cpp metadata header (magic 0x${java.lang.Long.toHexString(magic).uppercase()}).")
        }
        if (version !in 16..31) notes.add("Unsupported version field: $version")
        return try {
            val md = Metadata(buf)
            val size = dataSize(buf, md.version)
            if (size > buf.limit()) notes.add("Header describes ${size} bytes but the file has ${buf.limit()} bytes (truncated?)")
            Report(
                valid = true,
                version = version,
                detailedVersion = dev.il2cpp.dumper.core.il2cpp.Il2Cpp.fmtVersion(md.version),
                headerSize = Il2CppGlobalMetadataHeader.size(md.version),
                dataSize = size,
                images = md.imageDefs.size,
                types = md.typeDefs.size,
                methods = md.methodDefs.size,
                stringLiterals = md.stringLiterals.size,
                notes = notes,
            )
        } catch (e: Exception) {
            notes.add(e.message ?: e.toString())
            Report(false, if (version in 16..31) version else null, null, 0, 0, 0, 0, 0, 0, notes)
        }
    }
}
