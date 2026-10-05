package dev.il2cpp.dumper.core.utils

import java.nio.ByteBuffer

enum class SearchSectionType { Exec, Data, Bss }

class SearchSection(
    @JvmField var offset: Long,
    @JvmField var offsetEnd: Long,
    @JvmField var address: Long,
    @JvmField var addressEnd: Long,
)

/** Unsigned comparison helpers: the original works with ulong everywhere. */
@Suppress("NOTHING_TO_INLINE")
inline fun uge(a: Long, b: Long) = java.lang.Long.compareUnsigned(a, b) >= 0

@Suppress("NOTHING_TO_INLINE")
inline fun ule(a: Long, b: Long) = java.lang.Long.compareUnsigned(a, b) <= 0

@Suppress("NOTHING_TO_INLINE")
inline fun ult(a: Long, b: Long) = java.lang.Long.compareUnsigned(a, b) < 0

@Suppress("NOTHING_TO_INLINE")
inline fun umin(a: Long, b: Long) = if (ult(a, b)) a else b

fun hex(v: Long): String = java.lang.Long.toHexString(v).uppercase()

/** Port of BoyerMooreHorspool.cs operating on a ByteBuffer window without copying. */
object BoyerMooreHorspool {
    fun search(buf: ByteBuffer, start: Int, length: Int, pattern: ByteArray): Sequence<Int> = sequence {
        val patternLength = pattern.size
        if (length == 0 || patternLength == 0 || patternLength > length) return@sequence
        val bad = IntArray(256) { patternLength }
        val last = patternLength - 1
        for (i in 0 until last) bad[pattern[i].toInt() and 0xFF] = last - i
        var index = 0
        while (index <= length - patternLength) {
            var i = last
            while (buf.get(start + index + i) == pattern[i]) {
                if (i == 0) {
                    yield(index)
                    break
                }
                i--
            }
            index += bad[buf.get(start + index + last).toInt() and 0xFF]
        }
    }

    /** Pattern like "? 0x10 ? 0xE7" ("?" = wildcard). */
    fun search(buf: ByteBuffer, start: Int, length: Int, stringPattern: String): Sequence<Int> = sequence {
        val parts = stringPattern.split(' ')
        val pattern = IntArray(parts.size) { i ->
            val p = parts[i]
            if (p == "?") -1 else p.removePrefix("0x").removePrefix("0X").toInt(16)
        }
        val patternLength = pattern.size
        if (length == 0 || patternLength == 0 || patternLength > length) return@sequence
        val bad = IntArray(256) { patternLength }
        val last = patternLength - 1
        for (i in 0 until last) {
            if (pattern[i] != -1) bad[pattern[i]] = last - i
        }
        var index = 0
        while (index <= length - patternLength) {
            var i = last
            while (pattern[i] == -1 || (buf.get(start + index + i).toInt() and 0xFF) == pattern[i]) {
                if (i == 0) {
                    yield(index)
                    break
                }
                i--
            }
            index += bad[buf.get(start + index + last).toInt() and 0xFF]
        }
    }
}

/** Port of ArmUtils.cs using bit operations instead of binary strings. */
object ArmUtils {
    fun decodeMov(asm: ByteArray): Long {
        fun b(i: Int) = asm[i].toInt() and 0xFF
        val low = (b(2) + ((b(3) and 0x70) shl 4) + ((b(1) and 0x04) shl 9) + ((b(0) and 0x0f) shl 12)) and 0xFFFF
        val high = (b(6) + ((b(7) and 0x70) shl 4) + ((b(5) and 0x04) shl 9) + ((b(4) and 0x0f) shl 12)) and 0xFFFF
        return ((high.toLong() shl 16) + low) and 0xFFFFFFFFL
    }

    private fun word(inst: ByteArray): Long =
        (inst[0].toLong() and 0xFF) or ((inst[1].toLong() and 0xFF) shl 8) or
            ((inst[2].toLong() and 0xFF) shl 16) or ((inst[3].toLong() and 0xFF) shl 24)

    fun decodeAdr(pc: Long, inst: ByteArray): Long {
        val w = word(inst)
        val immhi = (w shr 5) and 0x7FFFF
        val immlo = (w shr 29) and 0x3
        var imm = (immhi shl 2) or immlo
        if (imm and (1L shl 20) != 0L) imm = imm or (-1L shl 21)
        return pc + imm
    }

    fun decodeAdrp(pcIn: Long, inst: ByteArray): Long {
        val pc = pcIn and -0x1000L
        val w = word(inst)
        val immhi = (w shr 5) and 0x7FFFF
        val immlo = (w shr 29) and 0x3
        var imm = ((immhi shl 2) or immlo) shl 12
        if (imm and (1L shl 32) != 0L) imm = imm or (-1L shl 33)
        return pc + imm
    }

    fun decodeAdd(inst: ByteArray): Long {
        val w = word(inst)
        var imm = (w shr 10) and 0xFFF
        if ((w shr 22) and 1L == 1L) imm = imm shl 12
        return imm
    }

    fun isAdr(inst: ByteArray): Boolean {
        val w = word(inst)
        return (w shr 31) and 1L == 0L && ((w shr 24) and 0x1F) == 0x10L
    }
}
