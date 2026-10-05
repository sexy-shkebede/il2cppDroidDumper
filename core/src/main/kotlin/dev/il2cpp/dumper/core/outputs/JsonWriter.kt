package dev.il2cpp.dumper.core.outputs

/**
 * Minimal streaming JSON writer that reproduces System.Text.Json output with
 * WriteIndented = true and the default JavaScriptEncoder (so script.json matches the desktop tool).
 */
class JsonWriter(private val out: Appendable, private val indented: Boolean = true) {
    private val hasItems = ArrayList<Boolean>()
    private var afterName = false

    private fun newlineIndent(depth: Int) {
        if (!indented) return
        out.append('\n')
        for (i in 0 until depth) out.append("  ")
    }

    private fun beforeValue() {
        if (afterName) {
            afterName = false
            return
        }
        if (hasItems.isEmpty()) return
        if (hasItems[hasItems.size - 1]) out.append(',')
        hasItems[hasItems.size - 1] = true
        newlineIndent(hasItems.size)
    }

    fun beginObject(): JsonWriter { beforeValue(); out.append('{'); hasItems.add(false); return this }

    fun endObject(): JsonWriter {
        val had = hasItems.removeAt(hasItems.size - 1)
        if (had) newlineIndent(hasItems.size)
        out.append('}')
        return this
    }

    fun beginArray(): JsonWriter { beforeValue(); out.append('['); hasItems.add(false); return this }

    fun endArray(): JsonWriter {
        val had = hasItems.removeAt(hasItems.size - 1)
        if (had) newlineIndent(hasItems.size)
        out.append(']')
        return this
    }

    fun name(name: String): JsonWriter {
        beforeValue()
        writeString(name)
        out.append(if (indented) ": " else ":")
        afterName = true
        return this
    }

    fun value(s: String?): JsonWriter {
        beforeValue()
        if (s == null) out.append("null") else writeString(s)
        return this
    }

    /** Unsigned 64-bit number. */
    fun valueULong(v: Long): JsonWriter {
        beforeValue()
        out.append(java.lang.Long.toUnsignedString(v))
        return this
    }

    fun value(v: Long): JsonWriter {
        beforeValue()
        out.append(v.toString())
        return this
    }

    fun value(v: Boolean): JsonWriter {
        beforeValue()
        out.append(if (v) "true" else "false")
        return this
    }

    private fun writeString(s: String) {
        out.append('"')
        for (c in s) {
            when {
                c == '\\' -> out.append("\\\\")
                c == '\b' -> out.append("\\b")
                c == '\t' -> out.append("\\t")
                c == '\n' -> out.append("\\n")
                c == '\u000C' -> out.append("\\f")
                c == '\r' -> out.append("\\r")
                c.code in 0x20..0x7E && c != '"' && c != '&' && c != '\'' && c != '+' && c != '<' && c != '>' && c != '`' -> out.append(c)
                else -> {
                    out.append("\\u")
                    val h = Integer.toHexString(c.code).uppercase()
                    for (i in h.length until 4) out.append('0')
                    out.append(h)
                }
            }
        }
        out.append('"')
    }

    companion object {
        fun escape(s: String): String {
            val sb = StringBuilder()
            JsonWriter(sb).writeString(s)
            return sb.toString()
        }
    }
}
