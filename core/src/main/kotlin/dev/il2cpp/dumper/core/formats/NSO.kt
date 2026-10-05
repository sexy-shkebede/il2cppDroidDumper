package dev.il2cpp.dumper.core.formats

import dev.il2cpp.dumper.core.formats.ElfConstants.DT_GNU_HASH
import dev.il2cpp.dumper.core.formats.ElfConstants.DT_HASH
import dev.il2cpp.dumper.core.formats.ElfConstants.DT_RELA
import dev.il2cpp.dumper.core.formats.ElfConstants.DT_RELASZ
import dev.il2cpp.dumper.core.formats.ElfConstants.DT_SYMTAB
import dev.il2cpp.dumper.core.formats.ElfConstants.R_AARCH64_ABS64
import dev.il2cpp.dumper.core.formats.ElfConstants.R_AARCH64_RELATIVE
import dev.il2cpp.dumper.core.il2cpp.Il2Cpp
import dev.il2cpp.dumper.core.utils.SearchSection
import dev.il2cpp.dumper.core.utils.SearchSectionType
import dev.il2cpp.dumper.core.utils.SectionHelper
import dev.il2cpp.dumper.core.utils.uge
import dev.il2cpp.dumper.core.utils.ule
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class NSOSegmentHeader(var fileOffset: Long = 0, var memoryOffset: Long = 0, var decompressedSize: Long = 0)

/** Raw LZ4 block decoder (replacement for Lz4DecoderStream.cs). */
object Lz4 {
    fun decodeBlock(src: ByteArray, dst: ByteArray): Int {
        var sp = 0
        var dp = 0
        while (sp < src.size) {
            val token = src[sp++].toInt() and 0xFF
            var litLen = token ushr 4
            if (litLen == 15) {
                while (true) {
                    val b = src[sp++].toInt() and 0xFF
                    litLen += b
                    if (b != 255) break
                }
            }
            val copy = minOf(litLen, dst.size - dp)
            System.arraycopy(src, sp, dst, dp, copy)
            sp += litLen
            dp += copy
            if (sp >= src.size || dp >= dst.size) break
            val offset = (src[sp].toInt() and 0xFF) or ((src[sp + 1].toInt() and 0xFF) shl 8)
            sp += 2
            var matchLen = token and 0x0F
            if (matchLen == 15) {
                while (true) {
                    val b = src[sp++].toInt() and 0xFF
                    matchLen += b
                    if (b != 255) break
                }
            }
            matchLen += 4
            var from = dp - offset
            if (offset == 0 || from < 0) throw IllegalStateException("Invalid LZ4 match offset")
            val end = minOf(dp + matchLen, dst.size)
            while (dp < end) dst[dp++] = dst[from++]
        }
        return dp
    }
}

/** Port of NSO.cs (Nintendo Switch). */
class NSO(buffer: ByteBuffer) : Il2Cpp(buffer) {
    private var magic = 0L
    private var nsoVersion = 0L
    private var reserved = 0L
    private var flags = 0L
    private lateinit var textSegment: NSOSegmentHeader
    private var moduleOffset = 0L
    private lateinit var roDataSegment: NSOSegmentHeader
    private var moduleFileSize = 0L
    private lateinit var dataSegment: NSOSegmentHeader
    private var bssSize = 0L
    private lateinit var digestBuildID: ByteArray
    private var textCompressedSize = 0L
    private var roDataCompressedSize = 0L
    private var dataCompressedSize = 0L
    private lateinit var padding: ByteArray
    private val extents = LongArray(6)
    private lateinit var hashes: ByteArray
    private var bssSegment: NSOSegmentHeader? = null
    private val isTextCompressed: Boolean
    private val isRoDataCompressed: Boolean
    private val isDataCompressed: Boolean
    private val segments = ArrayList<NSOSegmentHeader>()
    private var symbolTable: List<Elf64Sym>? = null
    private val dynamicSection = ArrayList<Elf64Dyn>()
    private val isCompressed get() = isTextCompressed || isRoDataCompressed || isDataCompressed
    override val formatName: String get() = "NSO"

    init {
        position = 0
        magic = readUInt32()
        nsoVersion = readUInt32()
        reserved = readUInt32()
        flags = readUInt32()
        isTextCompressed = (flags and 1L) != 0L
        isRoDataCompressed = (flags and 2L) != 0L
        isDataCompressed = (flags and 4L) != 0L
        textSegment = NSOSegmentHeader(readUInt32(), readUInt32(), readUInt32())
        segments.add(textSegment)
        moduleOffset = readUInt32()
        roDataSegment = NSOSegmentHeader(readUInt32(), readUInt32(), readUInt32())
        segments.add(roDataSegment)
        moduleFileSize = readUInt32()
        dataSegment = NSOSegmentHeader(readUInt32(), readUInt32(), readUInt32())
        segments.add(dataSegment)
        bssSize = readUInt32()
        digestBuildID = readBytes(0x20)
        textCompressedSize = readUInt32()
        roDataCompressedSize = readUInt32()
        dataCompressedSize = readUInt32()
        padding = readBytes(0x1C)
        for (i in 0 until 6) extents[i] = readUInt32()
        hashes = readBytes(0x60)

        if (!isCompressed) {
            position = textSegment.fileOffset + 4
            val modOffset = readUInt32()
            position = textSegment.fileOffset + modOffset + 4
            val dynamicOffset = (readUInt32() + modOffset) and 0xFFFFFFFFL
            val bssStart = readUInt32()
            val bssEnd = readUInt32()
            bssSegment = NSOSegmentHeader(bssStart, bssStart, (bssEnd - bssStart) and 0xFFFFFFFFL)
            val maxSize = ((dataSegment.memoryOffset + dataSegment.decompressedSize - dynamicOffset) and 0xFFFFFFFFL) / 16
            position = mapVATR(dynamicOffset)
            for (i in 0 until maxSize) {
                val dynamic = Elf64Dyn.read(this)
                if (dynamic.dTag == 0L) break else dynamicSection.add(dynamic)
            }
            readSymbol()
            relocationProcessing()
        }
    }

    private fun readSymbol() {
        try {
            var symbolCount: Long
            val hash = dynamicSection.firstOrNull { it.dTag == DT_HASH }
            if (hash != null) {
                position = mapVATR(hash.dUn)
                readUInt32()
                symbolCount = readUInt32()
            } else {
                val gnu = dynamicSection.first { it.dTag == DT_GNU_HASH }
                val addr = mapVATR(gnu.dUn)
                position = addr
                val nbuckets = readUInt32()
                val symoffset = readUInt32()
                val bloomSize = readUInt32()
                readUInt32()
                val bucketsAddress = addr + 16 + (8 * bloomSize)
                val buckets = readUInt32Array(bucketsAddress, nbuckets)
                var lastSymbol = buckets.max()
                if (lastSymbol < symoffset) {
                    symbolCount = symoffset
                } else {
                    val chainsBaseAddress = bucketsAddress + 4 * nbuckets
                    position = chainsBaseAddress + (lastSymbol - symoffset) * 4
                    while (true) {
                        val chainEntry = readUInt32()
                        ++lastSymbol
                        if ((chainEntry and 1L) != 0L) break
                    }
                    symbolCount = lastSymbol
                }
            }
            val dynsymOffset = mapVATR(dynamicSection.first { it.dTag == DT_SYMTAB }.dUn)
            symbolTable = readArray(dynsymOffset, symbolCount, 24) { Elf64Sym.read(this) }
        } catch (e: Exception) {
            // ignored
        }
    }

    private fun relocationProcessing() {
        logger.log("Applying relocations...")
        try {
            val relaOffset = mapVATR(dynamicSection.first { it.dTag == DT_RELA }.dUn)
            val relaSize = dynamicSection.first { it.dTag == DT_RELASZ }.dUn
            val relaTable = readArray(relaOffset, java.lang.Long.divideUnsigned(relaSize, 24L), 24) { Elf64Rela.read(this) }
            for (rela in relaTable) {
                val type = rela.rInfo and 0xffffffffL
                val sym = rela.rInfo ushr 32
                when (type) {
                    R_AARCH64_ABS64 -> {
                        val symbol = symbolTable!![sym.toInt()]
                        position = mapVATR(rela.rOffset)
                        write(symbol.stValue + rela.rAddend)
                    }
                    R_AARCH64_RELATIVE -> {
                        position = mapVATR(rela.rOffset)
                        write(rela.rAddend)
                    }
                }
            }
        } catch (e: Exception) {
            // ignored
        }
    }

    override fun mapVATR(addr: Long): Long {
        val segment = segments.first { uge(addr, it.memoryOffset) && ule(addr, it.memoryOffset + it.decompressedSize) }
        return addr - segment.memoryOffset + segment.fileOffset
    }

    override fun mapRTVA(addr: Long): Long {
        val segment = segments.firstOrNull { uge(addr, it.fileOffset) && ule(addr, it.fileOffset + it.decompressedSize) } ?: return 0L
        return addr - segment.fileOffset + segment.memoryOffset
    }

    override fun search(): Boolean = false

    override fun plusSearch(methodCount: Int, typeDefinitionsCount: Int, imageCount: Int): Boolean {
        val sectionHelper = getSectionHelper(methodCount, typeDefinitionsCount, imageCount)
        val codeRegistration = sectionHelper.findCodeRegistration()
        val metadataRegistration = sectionHelper.findMetadataRegistration()
        return autoPlusInit(codeRegistration, metadataRegistration)
    }

    override fun symbolSearch(): Boolean = false

    fun unCompress(): NSO {
        if (!isCompressed) return this
        val out = ByteArrayOutputStream()
        val hdr = ByteBuffer.allocate(0x100).order(ByteOrder.LITTLE_ENDIAN)
        hdr.putInt(magic.toInt()); hdr.putInt(nsoVersion.toInt()); hdr.putInt(reserved.toInt()); hdr.putInt(0)
        hdr.putInt(textSegment.fileOffset.toInt()); hdr.putInt(textSegment.memoryOffset.toInt()); hdr.putInt(textSegment.decompressedSize.toInt())
        hdr.putInt(moduleOffset.toInt())
        val roOffset = textSegment.fileOffset + textSegment.decompressedSize
        hdr.putInt(roOffset.toInt()); hdr.putInt(roDataSegment.memoryOffset.toInt()); hdr.putInt(roDataSegment.decompressedSize.toInt())
        hdr.putInt(moduleFileSize.toInt())
        hdr.putInt((roOffset + roDataSegment.decompressedSize).toInt()); hdr.putInt(dataSegment.memoryOffset.toInt()); hdr.putInt(dataSegment.decompressedSize.toInt())
        hdr.putInt(bssSize.toInt())
        hdr.put(digestBuildID)
        hdr.putInt(textCompressedSize.toInt()); hdr.putInt(roDataCompressedSize.toInt()); hdr.putInt(dataCompressedSize.toInt())
        hdr.put(padding)
        for (e in extents) hdr.putInt(e.toInt())
        hdr.put(hashes)
        out.write(hdr.array(), 0, hdr.position())
        // writer.BaseStream.Position = TextSegment.FileOffset
        val headerBytes = out.toByteArray()
        val result = ByteArrayOutputStream()
        result.write(headerBytes)
        while (result.size() < textSegment.fileOffset) result.write(0)
        val trimmed = result.toByteArray().copyOf(textSegment.fileOffset.toInt())
        val body = ByteArrayOutputStream()
        body.write(trimmed)
        position = textSegment.fileOffset
        fun segment(compressedSize: Long, compressed: Boolean, decompressedSize: Long) {
            val bytes = readBytes(compressedSize.toInt())
            if (compressed) {
                val data = ByteArray(decompressedSize.toInt())
                Lz4.decodeBlock(bytes, data)
                body.write(data)
            } else {
                body.write(bytes)
            }
        }
        segment(textCompressedSize, isTextCompressed, textSegment.decompressedSize)
        segment(roDataCompressedSize, isRoDataCompressed, roDataSegment.decompressedSize)
        segment(dataCompressedSize, isDataCompressed, dataSegment.decompressedSize)
        val nso = NSO(ByteBuffer.wrap(body.toByteArray()))
        nso.logger = logger
        return nso
    }

    override fun getSectionHelper(methodCount: Int, typeDefinitionsCount: Int, imageCount: Int): SectionHelper {
        fun conv(vararg s: NSOSegmentHeader?) = s.filterNotNull().map {
            SearchSection(it.fileOffset, it.fileOffset + it.decompressedSize, it.memoryOffset, it.memoryOffset + it.decompressedSize)
        }
        val sectionHelper = SectionHelper(this, methodCount, typeDefinitionsCount, metadataUsagesCount, imageCount)
        sectionHelper.setSection(SearchSectionType.Exec, conv(textSegment))
        sectionHelper.setSection(SearchSectionType.Data, conv(dataSegment, roDataSegment))
        sectionHelper.setSection(SearchSectionType.Bss, conv(bssSegment))
        return sectionHelper
    }

    override fun checkDump(): Boolean = false
}
