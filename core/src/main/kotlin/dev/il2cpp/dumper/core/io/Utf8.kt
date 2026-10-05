package dev.il2cpp.dumper.core.io

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.Writer

/** UTF-8 helpers that behave like .NET's Encoding.UTF8 (U+FFFD replacement for broken surrogates). */
object Utf8 {
    fun decode(bytes: ByteArray): String = String(bytes, Charsets.UTF_8)

    fun decode(bytes: ByteArray, offset: Int, len: Int): String = String(bytes, offset, len, Charsets.UTF_8)

    fun byteCount(s: String): Int {
        var count = 0
        var i = 0
        val n = s.length
        while (i < n) {
            val c = s[i]
            when {
                c.code < 0x80 -> count += 1
                c.code < 0x800 -> count += 2
                Character.isHighSurrogate(c) && i + 1 < n && Character.isLowSurrogate(s[i + 1]) -> { count += 4; i++ }
                else -> count += 3
            }
            i++
        }
        return count
    }

    fun encode(s: String): ByteArray {
        val out = java.io.ByteArrayOutputStream(s.length + 16)
        Utf8Writer(out).use { it.write(s) }
        return out.toByteArray()
    }

    fun writeFile(file: File, text: CharSequence) {
        Utf8Writer(FileOutputStream(file)).use { it.append(text) }
    }
}

/**
 * Fast UTF-8 writer without BOM. Lone surrogates are written as U+FFFD exactly like .NET StreamWriter.
 */
class Utf8Writer(out: OutputStream, bufferSize: Int = 1 shl 16) : Writer() {
    private val os = if (out is BufferedOutputStream) out else BufferedOutputStream(out, bufferSize)
    private var pendingHigh: Char? = null

    private fun writeReplacement() {
        os.write(0xEF); os.write(0xBF); os.write(0xBD)
    }

    private fun writeChar(c: Char) {
        val high = pendingHigh
        if (high != null) {
            pendingHigh = null
            if (Character.isLowSurrogate(c)) {
                val cp = Character.toCodePoint(high, c)
                os.write(0xF0 or (cp shr 18))
                os.write(0x80 or ((cp shr 12) and 0x3F))
                os.write(0x80 or ((cp shr 6) and 0x3F))
                os.write(0x80 or (cp and 0x3F))
                return
            }
            writeReplacement()
        }
        val code = c.code
        when {
            code < 0x80 -> os.write(code)
            code < 0x800 -> {
                os.write(0xC0 or (code shr 6))
                os.write(0x80 or (code and 0x3F))
            }
            Character.isHighSurrogate(c) -> pendingHigh = c
            Character.isLowSurrogate(c) -> writeReplacement()
            else -> {
                os.write(0xE0 or (code shr 12))
                os.write(0x80 or ((code shr 6) and 0x3F))
                os.write(0x80 or (code and 0x3F))
            }
        }
    }

    override fun write(cbuf: CharArray, off: Int, len: Int) {
        for (i in off until off + len) writeChar(cbuf[i])
    }

    override fun write(str: String) {
        for (i in str.indices) writeChar(str[i])
    }

    override fun write(str: String, off: Int, len: Int) {
        for (i in off until off + len) writeChar(str[i])
    }

    override fun write(c: Int) = writeChar(c.toChar())

    override fun append(csq: CharSequence?): Writer {
        val s = csq ?: "null"
        for (i in 0 until s.length) writeChar(s[i])
        return this
    }

    override fun flush() {
        os.flush()
    }

    override fun close() {
        if (pendingHigh != null) {
            pendingHigh = null
            writeReplacement()
        }
        os.flush()
        os.close()
    }
}
