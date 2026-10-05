package dev.il2cpp.dumper.core.utils

import dev.il2cpp.dumper.core.il2cpp.Il2CppType
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** Value read from a metadata blob (port of BlobValue.cs). */
class BlobValue(
    var value: Any?,
    var il2CppTypeEnum: Int,
    var enumType: Il2CppType? = null,
) {
    override fun toString(): String = "Il2CppDumper.BlobValue"
}

/**
 * Helpers that reproduce .NET (invariant culture) string conversions so that the generated
 * dump.cs matches the desktop Il2CppDumper byte for byte.
 */
object CSharp {
    /** Equivalent of $"{value}" / value.ToString() for the boxed values produced by GetConstantValueFromBlob. */
    fun toString(value: Any?): String = when (value) {
        null -> ""
        is Boolean -> if (value) "True" else "False"
        is Float -> formatFloat(value)
        is Double -> formatDouble(value)
        is ULong -> value.toString()
        is Char -> value.toString()
        is Array<*> -> "Il2CppDumper.BlobValue[]"
        else -> value.toString()
    }

    fun formatDouble(d: Double): String {
        if (d.isNaN()) return "NaN"
        if (d == Double.POSITIVE_INFINITY) return "Infinity"
        if (d == Double.NEGATIVE_INFINITY) return "-Infinity"
        if (d == 0.0) return if (1.0 / d < 0) "-0" else "0"
        val exact = BigDecimal(d)
        var r = exact
        for (p in 1..17) {
            val c = exact.round(MathContext(p, RoundingMode.HALF_EVEN))
            if (c.toDouble() == d) { r = c; break }
        }
        return formatGeneral(r, 17)
    }

    fun formatFloat(f: Float): String {
        if (f.isNaN()) return "NaN"
        if (f == Float.POSITIVE_INFINITY) return "Infinity"
        if (f == Float.NEGATIVE_INFINITY) return "-Infinity"
        if (f == 0.0f) return if (1.0f / f < 0) "-0" else "0"
        val exact = BigDecimal(f.toDouble())
        var r = exact
        for (p in 1..9) {
            val c = exact.round(MathContext(p, RoundingMode.HALF_EVEN))
            if (c.toFloat() == f) { r = c; break }
        }
        return formatGeneral(r, 9)
    }

    /** .NET Number.FormatGeneral with nMaxDigits = max(digits, maxRoundTripDigits). */
    private fun formatGeneral(value: BigDecimal, maxRoundTrip: Int): String {
        val stripped = value.stripTrailingZeros()
        val negative = stripped.signum() < 0
        val digits = stripped.unscaledValue().abs().toString()
        val scale = digits.length - stripped.scale()
        val nMaxDigits = maxOf(digits.length, maxRoundTrip)
        val sb = StringBuilder()
        if (negative) sb.append('-')
        var digPos = scale
        var scientific = false
        if (digPos > nMaxDigits || digPos < -3) {
            digPos = 1
            scientific = true
        }
        var di = 0
        if (digPos > 0) {
            do {
                sb.append(if (di < digits.length) digits[di++] else '0')
            } while (--digPos > 0)
        } else {
            sb.append('0')
        }
        if (di < digits.length || digPos < 0) {
            sb.append('.')
            while (digPos < 0) {
                sb.append('0')
                digPos++
            }
            while (di < digits.length) sb.append(digits[di++])
        }
        if (scientific) {
            val e = scale - 1
            sb.append('E')
            sb.append(if (e < 0) '-' else '+')
            val abs = Math.abs(e).toString()
            if (abs.length < 2) sb.append('0')
            sb.append(abs)
        }
        return sb.toString()
    }

    /** Port of StringExtensions.ToEscapedString */
    fun escape(s: String): String {
        val re = StringBuilder(s.length)
        for (c in s) {
            when (c) {
                '\'' -> re.append("\\'")
                '"' -> re.append("\\\"")
                '\\' -> re.append("\\\\")
                '\u0000' -> re.append("\\0")
                '\u0007' -> re.append("\\a")
                '\b' -> re.append("\\b")
                '\u000C' -> re.append("\\f")
                '\n' -> re.append("\\n")
                '\r' -> re.append("\\r")
                '\t' -> re.append("\\t")
                '\u000B' -> re.append("\\v")
                '\u0085' -> re.append("\\u0085")
                ' ' -> re.append("\\u2028")
                ' ' -> re.append("\\u2029")
                else -> re.append(c)
            }
        }
        return re.toString()
    }

    /** Ordinal String.Replace (throws on empty oldValue like .NET). */
    fun replace(s: String, oldValue: String, newValue: String): String {
        if (oldValue.isEmpty()) throw IllegalArgumentException("String cannot be of zero length. (Parameter 'oldValue')")
        return s.replace(oldValue, newValue)
    }
}
