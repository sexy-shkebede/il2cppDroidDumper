package dev.il2cpp.dumper.core.extra

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

/** Random access byte source (file, file channel from a content URI, or a window inside another source). */
interface SeekableSource : AutoCloseable {
    val size: Long
    fun read(position: Long, dst: ByteArray, off: Int, len: Int): Int

    fun readFully(position: Long, len: Int): ByteArray {
        val b = ByteArray(len)
        var done = 0
        while (done < len) {
            val n = read(position + done, b, done, len - done)
            if (n <= 0) throw IOException("Unexpected end of data")
            done += n
        }
        return b
    }

    fun window(offset: Long, length: Long): SeekableSource = WindowSource(this, offset, length)

    override fun close() {}

    companion object {
        fun of(file: File): SeekableSource = ChannelSource(RandomAccessFile(file, "r").channel, closeChannel = true)
        fun of(channel: FileChannel, closeChannel: Boolean = false): SeekableSource = ChannelSource(channel, closeChannel)
    }
}

private class ChannelSource(private val channel: FileChannel, private val closeChannel: Boolean) : SeekableSource {
    override val size: Long = channel.size()

    override fun read(position: Long, dst: ByteArray, off: Int, len: Int): Int {
        if (position >= size) return -1
        val bb = ByteBuffer.wrap(dst, off, len)
        return synchronized(this) { channel.read(bb, position) }
    }

    override fun close() {
        if (closeChannel) channel.close()
    }
}

private class WindowSource(private val parent: SeekableSource, private val offset: Long, override val size: Long) : SeekableSource {
    override fun read(position: Long, dst: ByteArray, off: Int, len: Int): Int {
        if (position >= size) return -1
        val n = minOf(len.toLong(), size - position).toInt()
        return parent.read(offset + position, dst, off, n)
    }
}

class ZipEntryInfo(
    val name: String,
    val method: Int,
    val compressedSize: Long,
    val size: Long,
    val localHeaderOffset: Long,
    val crc: Long,
) {
    val isDirectory: Boolean get() = name.endsWith("/")
}

/**
 * Small ZIP reader working on [SeekableSource] so that APKs nested inside XAPK/APKS bundles
 * can be read in place without extracting them first.
 */
class ZipReader(val source: SeekableSource) : AutoCloseable {
    val entries: List<ZipEntryInfo>
    private val byName: Map<String, ZipEntryInfo>

    init {
        entries = readCentralDirectory()
        byName = entries.associateBy { it.name }
    }

    fun entry(name: String): ZipEntryInfo? = byName[name]

    private fun le(b: ByteArray) = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)

    private fun readCentralDirectory(): List<ZipEntryInfo> {
        val size = source.size
        if (size < 22) throw IOException("Not a zip file")
        val tailLen = minOf(size, 65557L).toInt()
        val tail = source.readFully(size - tailLen, tailLen)
        var eocd = -1
        for (i in tailLen - 22 downTo 0) {
            if (tail[i] == 0x50.toByte() && tail[i + 1] == 0x4b.toByte() && tail[i + 2] == 0x05.toByte() && tail[i + 3] == 0x06.toByte()) {
                eocd = i; break
            }
        }
        if (eocd < 0) throw IOException("Not a zip file (EOCD not found)")
        val e = le(tail)
        var totalEntries = (e.getShort(eocd + 10).toInt() and 0xFFFF).toLong()
        var cdSize = e.getInt(eocd + 12).toLong() and 0xFFFFFFFFL
        var cdOffset = e.getInt(eocd + 16).toLong() and 0xFFFFFFFFL
        // ZIP64
        if (cdOffset == 0xFFFFFFFFL || totalEntries == 0xFFFFL) {
            val locPos = size - tailLen + eocd - 20
            if (locPos >= 0) {
                val loc = le(source.readFully(locPos, 20))
                if (loc.getInt(0) == 0x07064b50) {
                    val z64Off = loc.getLong(8)
                    val z = le(source.readFully(z64Off, 56))
                    if (z.getInt(0) == 0x06064b50) {
                        totalEntries = z.getLong(32)
                        cdSize = z.getLong(40)
                        cdOffset = z.getLong(48)
                    }
                }
            }
        }
        // Some APKs have data before the zip (e.g. signing blocks are fine, prepended stubs are not common);
        // compensate if the central directory is not where the EOCD says.
        val eocdAbs = size - tailLen + eocd
        val shift = eocdAbs - (cdOffset + cdSize)
        val cdStart = cdOffset + if (shift > 0) shift else 0
        if (cdSize > Int.MAX_VALUE) throw IOException("Central directory too large")
        val cd = le(source.readFully(cdStart, cdSize.toInt()))
        val list = ArrayList<ZipEntryInfo>(totalEntries.coerceAtMost(100000).toInt())
        var p = 0
        while (p + 46 <= cd.limit() && cd.getInt(p) == 0x02014b50) {
            val method = cd.getShort(p + 10).toInt() and 0xFFFF
            val crc = cd.getInt(p + 16).toLong() and 0xFFFFFFFFL
            var csize = cd.getInt(p + 20).toLong() and 0xFFFFFFFFL
            var usize = cd.getInt(p + 24).toLong() and 0xFFFFFFFFL
            val nameLen = cd.getShort(p + 28).toInt() and 0xFFFF
            val extraLen = cd.getShort(p + 30).toInt() and 0xFFFF
            val commentLen = cd.getShort(p + 32).toInt() and 0xFFFF
            var lho = cd.getInt(p + 42).toLong() and 0xFFFFFFFFL
            val nameBytes = ByteArray(nameLen)
            cd.position(p + 46); cd.get(nameBytes)
            val name = String(nameBytes, Charsets.UTF_8)
            // zip64 extra
            var x = p + 46 + nameLen
            val xEnd = x + extraLen
            while (x + 4 <= xEnd) {
                val id = cd.getShort(x).toInt() and 0xFFFF
                val len = cd.getShort(x + 2).toInt() and 0xFFFF
                if (id == 0x0001) {
                    var q = x + 4
                    if (usize == 0xFFFFFFFFL) { usize = cd.getLong(q); q += 8 }
                    if (csize == 0xFFFFFFFFL) { csize = cd.getLong(q); q += 8 }
                    if (lho == 0xFFFFFFFFL) { lho = cd.getLong(q) }
                }
                x += 4 + len
            }
            list.add(ZipEntryInfo(name, method, csize, usize, lho + if (shift > 0) shift else 0, crc))
            p += 46 + nameLen + extraLen + commentLen
        }
        return list
    }

    /** Offset of the entry data inside [source]. */
    fun dataOffset(entry: ZipEntryInfo): Long {
        val lh = le(source.readFully(entry.localHeaderOffset, 30))
        if (lh.getInt(0) != 0x04034b50) throw IOException("Bad local header for ${entry.name}")
        val nameLen = lh.getShort(26).toInt() and 0xFFFF
        val extraLen = lh.getShort(28).toInt() and 0xFFFF
        return entry.localHeaderOffset + 30 + nameLen + extraLen
    }

    fun openStream(entry: ZipEntryInfo): InputStream {
        val raw = SourceInputStream(source, dataOffset(entry), entry.compressedSize)
        return when (entry.method) {
            0 -> raw
            // raw deflate needs one extra dummy byte at the end of the input
            8 -> {
                val inflater = Inflater(true)
                object : InflaterInputStream(java.io.SequenceInputStream(raw, java.io.ByteArrayInputStream(ByteArray(1))), inflater, 1 shl 16) {
                    override fun close() {
                        try { super.close() } finally { inflater.end() }
                    }
                }
            }
            else -> throw IOException("Unsupported compression method ${entry.method} for ${entry.name}")
        }
    }

    /** Opens a stored (uncompressed) nested zip, e.g. split APKs inside an XAPK. */
    fun openNested(entry: ZipEntryInfo): ZipReader? {
        if (entry.method != 0) return null
        return ZipReader(source.window(dataOffset(entry), entry.size))
    }

    fun readHead(entry: ZipEntryInfo, n: Int): ByteArray {
        openStream(entry).use { s ->
            val b = ByteArray(minOf(n.toLong(), entry.size).toInt())
            var done = 0
            while (done < b.size) {
                val r = s.read(b, done, b.size - done)
                if (r <= 0) break
                done += r
            }
            return if (done == b.size) b else b.copyOf(done)
        }
    }

    fun extract(entry: ZipEntryInfo, target: File, onProgress: (Long, Long) -> Unit = { _, _ -> }) {
        target.parentFile?.mkdirs()
        openStream(entry).use { input ->
            target.outputStream().buffered(1 shl 16).use { out ->
                val buf = ByteArray(1 shl 16)
                var total = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    total += n
                    onProgress(total, entry.size)
                }
            }
        }
    }

    override fun close() = source.close()
}

private class SourceInputStream(private val src: SeekableSource, private var pos: Long, private var remaining: Long) : InputStream() {
    override fun read(): Int {
        val b = ByteArray(1)
        return if (read(b, 0, 1) <= 0) -1 else b[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (remaining <= 0) return -1
        val n = src.read(pos, b, off, minOf(len.toLong(), remaining).toInt())
        if (n <= 0) return -1
        pos += n
        remaining -= n
        return n
    }

    override fun available(): Int = remaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
