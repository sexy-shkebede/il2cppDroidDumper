package dev.il2cpp.dumper.core

import dev.il2cpp.dumper.core.io.Utf8
import dev.il2cpp.dumper.core.outputs.JsonWriter
import dev.il2cpp.dumper.core.outputs.StructGenerator
import dev.il2cpp.dumper.core.utils.CSharp
import org.junit.Assert.assertEquals
import org.junit.Test

/** Values produced by .NET 8 (InvariantGlobalization) for the same inputs. */
class FormattingTest {
    @Test
    fun doubleFormattingMatchesDotNet() {
        val expected = mapOf(
            1e15 to "1000000000000000", 1e16 to "10000000000000000", 1e17 to "1E+17",
            123456789012345678.0 to "1.2345678901234568E+17", 1e14 to "100000000000000", 0.0001 to "0.0001",
            0.00001 to "1E-05", 1.5 to "1.5", -60.0 to "-60", 1.0 / 3 to "0.3333333333333333",
            3.4028234663852886E+38 to "3.4028234663852886E+38", Double.MAX_VALUE to "1.7976931348623157E+308",
            Double.MIN_VALUE to "5E-324", -0.0 to "-0", Double.NaN to "NaN",
            Double.POSITIVE_INFINITY to "Infinity", Double.NEGATIVE_INFINITY to "-Infinity",
        )
        for ((v, s) in expected) assertEquals("double $v", s, CSharp.formatDouble(v))
    }

    @Test
    fun floatFormattingMatchesDotNet() {
        val expected = mapOf(
            1e7f to "10000000", 1e8f to "100000000", 1e9f to "1E+09", 123456789f to "123456790", 0.1f to "0.1",
            1.0f / 3 to "0.33333334", Float.MAX_VALUE to "3.4028235E+38", Float.MIN_VALUE to "1E-45",
            16777216f to "16777216", 1234567f to "1234567", 12345678f to "12345678", -0.0f to "-0",
            Float.NaN to "NaN", Float.POSITIVE_INFINITY to "Infinity", 0.0001f to "0.0001", 0.00001f to "1E-05",
        )
        for ((v, s) in expected) assertEquals("float $v", s, CSharp.formatFloat(v))
    }

    @Test
    fun jsonEscapingMatchesSystemTextJson() {
        val sb = StringBuilder()
        for (c in 0 until 0x3000) {
            if (c in 0xD800 until 0xE000) continue
            sb.append(c.toChar())
        }
        sb.append("﻿�").appendCodePoint(0x1F600)
        val out = StringBuilder()
        val w = JsonWriter(out)
        w.beginObject()
        w.name("s").value(sb.toString())
        w.name("arr").beginArray().valueULong(1).valueULong(-1L).endArray()
        w.name("empty").beginArray().endArray()
        w.name("nul").value(null as String?)
        w.endObject()
        val expected = javaClass.getResourceAsStream("/dotnet_json_escape.json")!!.readBytes()
        assertEquals(Utf8.decode(expected), out.toString())
    }

    @Test
    fun fixNameMatchesRegexBehaviour() {
        assertEquals("_class", StructGenerator.fixName("class"))
        assertEquals("_inline_", StructGenerator.fixName("inline"))
        assertEquals("_1abc", StructGenerator.fixName("1abc"))
        assertEquals("System_Collections_Generic_List_T_", StructGenerator.fixName("System.Collections.Generic.List<T>"))
        assertEquals("a__b", StructGenerator.fixName("a😀b")) // surrogate pair = 2 UTF-16 units
    }

    @Test
    fun escapedString() {
        assertEquals("a\\'b\\\"c\\\\d\\0\\n\\u2028", CSharp.escape("a'b\"c\\d\u0000\n "))
    }
}
