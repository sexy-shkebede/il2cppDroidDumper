package dev.il2cpp.dumper.core.extra

import dev.il2cpp.dumper.core.il2cpp.Il2CppGlobalMetadataHeader
import dev.il2cpp.dumper.core.il2cpp.Metadata
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ln
import kotlin.math.min

/**
 * "Metadata Doctor": explains *why* a `global-metadata.dat` is rejected and what to do about it,
 * and tries to recover the simple, deterministic cases of YOUR OWN file (wiped magic/version,
 * single-byte / short repeating-key XOR) — verified by actually re-parsing the result, so it
 * only ever reports success when the bytes are genuinely valid metadata.
 *
 * It does NOT defeat real encryption. When the file is properly encrypted the only reliable
 * route is to dump the already-decrypted metadata from the running process's memory (e.g. with
 * Zygisk-Il2CppDumper), which this app then loads via its memory-dump mode.
 */
object MetadataDoctor {
    const val MAGIC = 0xFAB11BAFL
    private val MAGIC_BYTES = byteArrayOf(0xAF.toByte(), 0x1B, 0xB1.toByte(), 0xFA.toByte())

    /** Known ASCII substrings that appear in plaintext metadata (image/type/method names). */
    private val MARKERS = listOf(
        "mscorlib", "Assembly-CSharp", "UnityEngine", "System.", "<Module>", ".ctor", "netstandard", "PlayerPrefs",
    )

    /** Versions worth trying when the version field itself looks wiped, newest first. */
    private val CANDIDATE_VERSIONS = listOf(31, 29, 27, 24, 23, 22, 21, 19)

    enum class Verdict {
        /** Parses cleanly — nothing wrong. */
        VALID,

        /** Magic + a plausible layout, but the version is newer than the parser supports. */
        UNSUPPORTED_VERSION,

        /** Magic + supported version, but the file ends before the header's sections do. */
        TRUNCATED,

        /** Magic + supported version, but the section table doesn't line up — fields look shuffled/obfuscated. */
        HEADER_SCRAMBLED,

        /** No magic, but plaintext names are visible — only the header (or part of it) is obfuscated. */
        HEADER_OBFUSCATED,

        /** No magic, no readable names, high entropy — the whole file is encrypted. */
        FULLY_ENCRYPTED,

        /** Too small / clearly not a metadata file. */
        NOT_METADATA,
    }

    data class Diagnosis(
        val verdict: Verdict,
        val fileSize: Long,
        val magicPresent: Boolean,
        val versionField: Int,
        val versionSupported: Boolean,
        /** 0..1 fraction of zero bytes (plaintext metadata is zero-heavy, ~0.2–0.4). */
        val zeroRatio: Double,
        /** Shannon entropy in bits/byte over a sample (encrypted ≈ 7.99, plaintext ≈ 5–6). */
        val entropy: Double,
        /** Which known plaintext markers were found. */
        val markersFound: List<String>,
        /** Parsed counts when [verdict] == VALID (0 otherwise). */
        val images: Int,
        val types: Int,
        val methods: Int,
        val stringLiterals: Int,
        /** One-line human headline. */
        val headline: String,
        /** Ordered, actionable advice lines. */
        val advice: List<String>,
        /** True when [attemptRecover] has a realistic chance (set after a dry probe). */
        val recoverable: Boolean,
        /** The recovered bytes when [recoverable] — computed during diagnosis so the UI needn't redo the work. */
        val recovery: Recovery? = null,
    )

    data class Recovery(val bytes: ByteArray, val version: Int, val method: String)

    private fun u32(b: ByteBuffer, off: Int): Long = b.getInt(off).toLong() and 0xFFFFFFFFL

    private fun entropyOf(bytes: ByteArray, sampleLimit: Int = 2 shl 20): Double {
        val n = min(bytes.size, sampleLimit)
        if (n == 0) return 0.0
        val counts = IntArray(256)
        for (i in 0 until n) counts[bytes[i].toInt() and 0xFF]++
        var e = 0.0
        for (c in counts) if (c > 0) {
            val p = c.toDouble() / n
            e -= p * (ln(p) / ln(2.0))
        }
        return e
    }

    private fun zeroRatioOf(bytes: ByteArray, sampleLimit: Int = 2 shl 20): Double {
        val n = min(bytes.size, sampleLimit)
        if (n == 0) return 0.0
        var z = 0
        for (i in 0 until n) if (bytes[i].toInt() == 0) z++
        return z.toDouble() / n
    }

    private fun markersIn(bytes: ByteArray, sampleLimit: Int = 4 shl 20): List<String> {
        val n = min(bytes.size, sampleLimit)
        val text = String(bytes, 0, n, Charsets.ISO_8859_1)
        return MARKERS.filter { text.contains(it) }
    }

    /** Does the header's section table line up inside the file for [version]? 0..1. */
    private fun layoutScore(buf: ByteBuffer, version: Double, fileSize: Long): Double {
        val headerSize = Il2CppGlobalMetadataHeader.size(version)
        if (headerSize + 8 > buf.limit()) return 0.0
        var ok = 0
        var total = 0
        var p = 8
        while (p + 8 <= headerSize) {
            val off = u32(buf, p)
            val len = buf.getInt(p + 4).toLong()
            total++
            // empty sections are always fine; non-empty must sit within the header..fileSize window
            if (len == 0L || (off in headerSize.toLong()..fileSize && off + len <= fileSize)) ok++
            p += 8
        }
        return if (total == 0) 0.0 else ok.toDouble() / total
    }

    fun diagnose(bytes: ByteArray): Diagnosis {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val size = bytes.size.toLong()
        val zero = zeroRatioOf(bytes)
        val entropy = entropyOf(bytes)
        val markers = markersIn(bytes)

        if (size < 0x110) {
            return Diagnosis(
                Verdict.NOT_METADATA, size, false, 0, false, zero, entropy, markers, 0, 0, 0, 0,
                "Not a metadata file",
                listOf(
                    "This file is only $size bytes — a real global-metadata.dat is at least tens of KB.",
                    "Make sure you picked global-metadata.dat (assets/bin/Data/Managed/Metadata/), not a placeholder.",
                ),
                recoverable = false,
            )
        }

        val magicPresent = u32(buf, 0) == MAGIC
        val versionField = buf.getInt(4)
        val versionSupported = versionField in 16..31

        // Case A: magic is present — parse and classify precisely.
        if (magicPresent) {
            if (!versionSupported) {
                return Diagnosis(
                    Verdict.UNSUPPORTED_VERSION, size, true, versionField, false, zero, entropy, markers, 0, 0, 0, 0,
                    "Metadata v$versionField is not supported yet",
                    listOf(
                        "The file is genuine (magic is intact) but its metadata version ($versionField) is newer than this dumper handles (16–31).",
                        "This usually means a very new Unity. Update the dumper's version tables (MetadataClasses.kt), or open an issue with the version number.",
                    ),
                    recoverable = false,
                )
            }
            return try {
                val md = Metadata(ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN))
                val data = MetadataTools.dataSize(buf, md.version)
                if (data > size) {
                    Diagnosis(
                        Verdict.TRUNCATED, size, true, versionField, true, zero, entropy, markers, 0, 0, 0, 0,
                        "File looks truncated",
                        listOf(
                            "The header describes ${data} bytes but the file is only $size. It was cut short during extraction/copy.",
                            "Re-extract global-metadata.dat from the APK — don't copy it out of a partially written location.",
                        ),
                        recoverable = false,
                    )
                } else {
                    Diagnosis(
                        Verdict.VALID, size, true, versionField, true, zero, entropy, markers,
                        md.imageDefs.size, md.typeDefs.size, md.methodDefs.size, md.stringLiterals.size,
                        "Valid metadata (v${dev.il2cpp.dumper.core.il2cpp.Il2Cpp.fmtVersion(md.version)})",
                        listOf("This file is fine — pair it with libil2cpp.so and dump."),
                        recoverable = false,
                    )
                }
            } catch (e: Exception) {
                Diagnosis(
                    Verdict.HEADER_SCRAMBLED, size, true, versionField, true, zero, entropy, markers, 0, 0, 0, 0,
                    "Header fields look scrambled",
                    listOf(
                        "Magic and version are intact but the section table doesn't line up (${e.message ?: "parse failed"}).",
                        "This is a protector that shuffles/obfuscates header fields. Static parsing can't fix it reliably.",
                        "Best route: dump the decrypted metadata from memory while the game runs (see below).",
                    ),
                    recoverable = false,
                )
            }
        }

        // Case B: no magic. Encrypted header, encrypted file, or garbage. Probe for cheap recovery first.
        val recovered = attemptRecover(bytes)
        if (recovered != null) {
            return Diagnosis(
                Verdict.HEADER_OBFUSCATED, size, false, versionField, false, zero, entropy, markers, 0, 0, 0, 0,
                "Lightly obfuscated — recoverable",
                listOf(
                    "No standard magic, but a simple transform recovers valid metadata (${recovered.method}).",
                    "Tap “Try to recover” to produce a clean global-metadata.dat (v${recovered.version}), then dump it with libil2cpp.so.",
                ),
                recoverable = true,
                recovery = recovered,
            )
        }

        val readable = markers.isNotEmpty()
        return if (readable && entropy < 7.5) {
            Diagnosis(
                Verdict.HEADER_OBFUSCATED, size, false, versionField, false, zero, entropy, markers, 0, 0, 0, 0,
                "Header obfuscated, body intact",
                listOf(
                    "No magic, but plaintext names are visible (${markers.take(3).joinToString(", ")}) and entropy is moderate (${"%.2f".format(entropy)}).",
                    "Only the header (usually the first 0x100–0x200 bytes) is scrambled; the body is plaintext.",
                    "Automatic recovery didn't crack it — the header transform isn't a simple XOR. Dump from memory for a guaranteed clean file (see below).",
                ),
                recoverable = false,
            )
        } else {
            Diagnosis(
                Verdict.FULLY_ENCRYPTED, size, false, versionField, false, zero, entropy, markers, 0, 0, 0, 0,
                "File is encrypted",
                listOf(
                    "No magic, no readable names, entropy ${"%.2f".format(entropy)}/8.0 and only ${"%.1f".format(zero * 100)}% zero bytes — this is encrypted (plaintext metadata is ~20–40% zeros).",
                    "This can't be undone from the file alone; the key lives in libil2cpp.so.",
                    "Reliable fix: the game decrypts metadata into RAM at launch, so dump it from the running process's memory — e.g. Zygisk-Il2CppDumper (root/Magisk). It writes a clean global-metadata.dat + libil2cpp.so that this app loads directly.",
                ),
                recoverable = false,
            )
        }
    }

    /**
     * Deterministic recovery of simple, self-reversible obfuscation of the user's own file.
     * Returns a byte array that actually re-parses as valid metadata, or null. Tries, in order:
     *   1) restore a wiped/altered magic + version where the layout is otherwise valid,
     *   2) single-byte XOR,
     *   3) short repeating-key XOR (period ≤ 32) recovered from the zero-heavy regions.
     */
    fun attemptRecover(bytes: ByteArray): Recovery? {
        if (bytes.size < 0x110) return null
        if (parses(bytes)) return null // already valid — nothing to recover

        // 1) Header restore: layout already valid, only magic/version wrong.
        run {
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val verField = buf.getInt(4)
            val candidates = if (verField in 16..31) listOf(verField) + CANDIDATE_VERSIONS else CANDIDATE_VERSIONS
            for (v in candidates.distinct()) {
                if (layoutScore(buf, v.toDouble(), bytes.size.toLong()) < 0.9) continue
                val fixed = bytes.copyOf()
                System.arraycopy(MAGIC_BYTES, 0, fixed, 0, 4)
                ByteBuffer.wrap(fixed).order(ByteOrder.LITTLE_ENDIAN).putInt(4, v)
                if (parses(fixed)) {
                    val what = if (u32(buf, 0) != MAGIC && verField != v) "magic + version → $v"
                    else if (u32(buf, 0) != MAGIC) "restored magic" else "version → $v"
                    return Recovery(fixed, v, what)
                }
            }
        }

        // 2) Single-byte XOR (the whole file XORed with one constant).
        run {
            val key = (bytes[0].toInt() xor MAGIC_BYTES[0].toInt()) and 0xFF
            if (key != 0) {
                val out = ByteArray(bytes.size) { (bytes[it].toInt() xor key).toByte() }
                if (parses(out)) return Recovery(out, versionOf(out), "single-byte XOR 0x${"%02X".format(key)}")
            }
        }

        // 3) Short repeating-key XOR. In metadata's long zero runs the ciphertext *is* the key,
        //    so the key is the most frequent byte at each position modulo the period.
        for (period in 1..32) {
            val key = ByteArray(period)
            for (k in 0 until period) {
                val counts = IntArray(256)
                var i = k
                val limit = min(bytes.size, 2 shl 20)
                while (i < limit) { counts[bytes[i].toInt() and 0xFF]++; i += period }
                var best = 0
                for (b in 0..255) if (counts[b] > counts[best]) best = b
                key[k] = best.toByte()
            }
            // Force the first 4 key bytes from the known magic plaintext.
            for (i in 0 until min(4, period)) key[i] = (bytes[i].toInt() xor MAGIC_BYTES[i].toInt()).toByte()
            if (key.all { it.toInt() == 0 }) continue
            val out = ByteArray(bytes.size) { (bytes[it].toInt() xor key[it % period].toInt()).toByte() }
            if (parses(out)) {
                return Recovery(out, versionOf(out), "repeating-key XOR (${period}B)")
            }
        }
        return null
    }

    private fun versionOf(bytes: ByteArray): Int =
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(4)

    /** True only if these bytes parse as metadata AND the first few image names end in ".dll". */
    private fun parses(bytes: ByteArray): Boolean = try {
        val md = Metadata(ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN))
        md.imageDefs.isNotEmpty() && md.imageDefs.take(5).all { md.getStringFromIndex(it.nameIndex).endsWith(".dll") }
    } catch (e: Exception) {
        false
    }
}
