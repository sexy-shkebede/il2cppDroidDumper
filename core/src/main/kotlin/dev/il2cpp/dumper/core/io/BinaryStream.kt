package dev.il2cpp.dumper.core.io

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class EndOfStreamException(message: String = "Unable to read beyond the end of the stream.") : IOException(message)

/**
 * Sequential little-endian reader shared by [BinaryStream] (big files) and [ByteArrayReader] (small blobs).
 * Mirrors the subset of .NET BinaryReader used by Il2CppDumper.
 */
interface DataReader {
    var position: Long
    val length: Long

    fun readByte(): Int
    fun readBytes(count: Int): ByteArray

    fun readBoolean(): Boolean = readByte() != 0
    fun readSByte(): Int = readByte().toByte().toInt()
    fun readUInt16(): Int = readByte() or (readByte() shl 8)
    fun readInt16(): Int = readUInt16().toShort().toInt()
    fun readInt32(): Int = readByte() or (readByte() shl 8) or (readByte() shl 16) or (readByte() shl 24)
    fun readUInt32(): Long = readInt32().toLong() and 0xFFFFFFFFL
    fun readInt64(): Long = (readUInt32()) or (readUInt32() shl 32)
    fun readUInt64(): Long = readInt64()
    fun readSingle(): Float = java.lang.Float.intBitsToFloat(readInt32())
    fun readDouble(): Double = java.lang.Double.longBitsToDouble(readInt64())

    /** Il2Cpp compressed unsigned int. Returned as unsigned 32-bit value in a Long. */
    fun readCompressedUInt32(): Long {
        val read = readByte()
        return when {
            read and 0x80 == 0 -> read.toLong()
            read and 0xC0 == 0x80 -> (((read and 0x80.inv()).toLong() shl 8) or readByte().toLong())
            read and 0xE0 == 0xC0 -> {
                var v = (read and 0xC0.inv()).toLong() shl 24
                v = v or (readByte().toLong() shl 16)
                v = v or (readByte().toLong() shl 8)
                v = v or readByte().toLong()
                v and 0xFFFFFFFFL
            }
            read == 0xF0 -> readUInt32()
            read == 0xFE -> 0xFFFFFFFEL
            read == 0xFF -> 0xFFFFFFFFL
            else -> throw IOException("Invalid compressed integer format")
        }
    }

    fun readCompressedInt32(): Int {
        var encoded = readCompressedUInt32()
        if (encoded == 0xFFFFFFFFL) return Int.MIN_VALUE
        val isNegative = (encoded and 1L) != 0L
        encoded = encoded ushr 1
        return if (isNegative) -(encoded + 1).toInt() else encoded.toInt()
    }

    fun readULeb128(): Long {
        var value = readByte().toLong()
        if (value >= 0x80) {
            var bitshift = 0
            value = value and 0x7f
            while (true) {
                val b = readByte()
                bitshift += 7
                value = value or ((b and 0x7f).toLong() shl bitshift)
                if (b < 0x80) break
            }
        }
        return value and 0xFFFFFFFFL
    }

    /** Port of BinaryReaderExtensions.ReadString(numChars): reads [numChars] UTF-16 chars of UTF-8 data. */
    fun readStringChars(numChars: Int): String {
        val start = position
        val bytes = readBytes(numChars * 4)
        val decoded = Utf8.decode(bytes)
        if (decoded.length < numChars) throw IndexOutOfBoundsException("numChars")
        val str = decoded.substring(0, numChars)
        position = start
        readBytes(Utf8.byteCount(str))
        return str
    }
}

/**
 * Random access little-endian stream over a [ByteBuffer] (heap or memory mapped).
 * Port of Il2CppDumper.BinaryStream.
 */
open class BinaryStream(buffer: ByteBuffer) : DataReader {
    @JvmField var buf: ByteBuffer = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
    @JvmField var version: Double = 0.0
    @JvmField var is32Bit: Boolean = false
    @JvmField var imageBase: Long = 0L
    private var pos: Long = 0L

    override var position: Long
        get() = pos
        set(value) { pos = value }

    override val length: Long get() = buf.limit().toLong()

    val pointerSize: Long get() = if (is32Bit) 4L else 8L

    protected fun check(n: Int): Int {
        val p = pos
        if (p < 0 || p + n > buf.limit()) throw EndOfStreamException()
        return p.toInt()
    }

    override fun readByte(): Int {
        val p = check(1); pos += 1
        return buf.get(p).toInt() and 0xFF
    }

    override fun readBytes(count: Int): ByteArray {
        if (count < 0) throw IllegalArgumentException("count")
        val p = pos
        if (p >= buf.limit() || p < 0) return ByteArray(0)
        val n = minOf(count.toLong(), buf.limit() - p).toInt()
        val out = ByteArray(n)
        val dup = buf.duplicate()
        dup.position(p.toInt())
        dup.get(out, 0, n)
        pos += n
        return out
    }

    override fun readUInt16(): Int {
        val p = check(2); pos += 2
        return buf.getShort(p).toInt() and 0xFFFF
    }

    override fun readInt16(): Int {
        val p = check(2); pos += 2
        return buf.getShort(p).toInt()
    }

    override fun readInt32(): Int {
        val p = check(4); pos += 4
        return buf.getInt(p)
    }

    override fun readUInt32(): Long = readInt32().toLong() and 0xFFFFFFFFL

    override fun readInt64(): Long {
        val p = check(8); pos += 8
        return buf.getLong(p)
    }

    override fun readUInt64(): Long = readInt64()

    fun readIntPtr(): Long = if (is32Bit) readInt32().toLong() else readInt64()

    open fun readUIntPtr(): Long = if (is32Bit) readUInt32() else readUInt64()

    fun readStringToNull(addr: Long): String {
        position = addr
        val start = check(0)
        var end = start
        val limit = buf.limit()
        while (true) {
            if (end >= limit) throw EndOfStreamException()
            if (buf.get(end).toInt() == 0) break
            end++
        }
        val bytes = ByteArray(end - start)
        val dup = buf.duplicate()
        dup.position(start)
        dup.get(bytes)
        pos = (end + 1).toLong()
        return Utf8.decode(bytes)
    }

    // ---- writes (used by relocation processing and dumped-file fixups) ----
    fun write(value: Int) {
        val p = check(4); pos += 4
        buf.putInt(p, value)
    }

    fun writeUInt32(value: Long) = write(value.toInt())

    fun write(value: Long) {
        val p = check(8); pos += 8
        buf.putLong(p, value)
    }

    fun writeBytes(bytes: ByteArray) {
        val p = check(bytes.size); pos += bytes.size
        val dup = buf.duplicate()
        dup.position(p)
        dup.put(bytes)
    }

    // ---- array helpers equivalent to ReadClassArray<primitive> ----
    fun checkCount(count: Long, elementSize: Int): Int {
        if (count < 0 || count > Int.MAX_VALUE) throw IllegalArgumentException("Invalid array length $count")
        if (elementSize > 0 && position + count * elementSize > length) throw EndOfStreamException()
        return count.toInt()
    }

    /** ReadClassArray<ulong>: pointer sized values. */
    fun readUIntPtrArray(addr: Long, count: Long): LongArray {
        position = addr
        val n = checkCount(count, pointerSize.toInt())
        val arr = LongArray(n)
        for (i in 0 until n) arr[i] = readUIntPtr()
        return arr
    }

    fun readUIntPtrArray(count: Long): LongArray = readUIntPtrArray(position, count)

    /** ReadClassArray<uint>. */
    fun readUInt32Array(addr: Long, count: Long): LongArray {
        position = addr
        val n = checkCount(count, 4)
        val arr = LongArray(n)
        for (i in 0 until n) arr[i] = readUInt32()
        return arr
    }

    /** ReadClassArray<int>. */
    fun readInt32Array(addr: Long, count: Long): IntArray {
        position = addr
        val n = checkCount(count, 4)
        val arr = IntArray(n)
        for (i in 0 until n) arr[i] = readInt32()
        return arr
    }

    inline fun <T> readArray(addr: Long, count: Long, minElementSize: Int, reader: () -> T): List<T> {
        position = addr
        val n = checkCount(count, minElementSize)
        val list = ArrayList<T>(n)
        for (i in 0 until n) list.add(reader())
        return list
    }
}

/** Small in-memory reader used for custom attribute blobs. */
class ByteArrayReader(private val data: ByteArray) : DataReader {
    override var position: Long = 0
    override val length: Long get() = data.size.toLong()

    override fun readByte(): Int {
        val p = position
        if (p < 0 || p >= data.size) throw EndOfStreamException()
        position = p + 1
        return data[p.toInt()].toInt() and 0xFF
    }

    override fun readBytes(count: Int): ByteArray {
        val p = position
        if (p >= data.size) return ByteArray(0)
        val n = minOf(count.toLong(), data.size - p).toInt()
        val out = data.copyOfRange(p.toInt(), p.toInt() + n)
        position = p + n
        return out
    }
}
