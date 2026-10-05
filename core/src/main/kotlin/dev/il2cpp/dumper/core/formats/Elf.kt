package dev.il2cpp.dumper.core.formats

import dev.il2cpp.dumper.core.formats.ElfConstants.DT_ANDROID_REL
import dev.il2cpp.dumper.core.formats.ElfConstants.DT_ANDROID_RELSZ
import dev.il2cpp.dumper.core.formats.ElfConstants.DT_FINI
import dev.il2cpp.dumper.core.formats.ElfConstants.DT_FINI_ARRAY
import dev.il2cpp.dumper.core.formats.ElfConstants.DT_GNU_HASH
import dev.il2cpp.dumper.core.formats.ElfConstants.DT_HASH
import dev.il2cpp.dumper.core.formats.ElfConstants.DT_INIT
import dev.il2cpp.dumper.core.formats.ElfConstants.DT_INIT_ARRAY
import dev.il2cpp.dumper.core.formats.ElfConstants.DT_JMPREL
import dev.il2cpp.dumper.core.formats.ElfConstants.DT_PLTGOT
import dev.il2cpp.dumper.core.formats.ElfConstants.DT_REL
import dev.il2cpp.dumper.core.formats.ElfConstants.DT_RELA
import dev.il2cpp.dumper.core.formats.ElfConstants.DT_RELSZ
import dev.il2cpp.dumper.core.formats.ElfConstants.DT_STRTAB
import dev.il2cpp.dumper.core.formats.ElfConstants.DT_SYMTAB
import dev.il2cpp.dumper.core.formats.ElfConstants.EM_ARM
import dev.il2cpp.dumper.core.formats.ElfConstants.PF_X
import dev.il2cpp.dumper.core.formats.ElfConstants.PT_DYNAMIC
import dev.il2cpp.dumper.core.formats.ElfConstants.PT_LOAD
import dev.il2cpp.dumper.core.formats.ElfConstants.R_386_32
import dev.il2cpp.dumper.core.formats.ElfConstants.R_ARM_ABS32
import dev.il2cpp.dumper.core.formats.ElfConstants.SHT_LOUSER
import dev.il2cpp.dumper.core.utils.BoyerMooreHorspool
import dev.il2cpp.dumper.core.utils.SearchSection
import dev.il2cpp.dumper.core.utils.SearchSectionType
import dev.il2cpp.dumper.core.utils.SectionHelper
import dev.il2cpp.dumper.core.utils.uge
import dev.il2cpp.dumper.core.utils.ule
import java.nio.ByteBuffer

/** Port of Elf.cs (32-bit ELF). */
class Elf(buffer: ByteBuffer) : ElfBase(buffer) {
    private lateinit var elfHeader: Elf32Ehdr
    private lateinit var programSegment: List<Elf32Phdr>
    private lateinit var dynamicSection: List<Elf32Dyn>
    private var symbolTable: List<Elf32Sym>? = null
    private var sectionTable: List<Elf32Shdr>? = null
    private lateinit var ptDynamic: Elf32Phdr

    override val formatName: String get() = "ELF32"
    override val machine: Int get() = elfHeader.eMachine

    init {
        is32Bit = true
        load()
    }

    override fun load() {
        position = 0
        elfHeader = Elf32Ehdr.read(this)
        programSegment = readArray(elfHeader.ePhoff, elfHeader.ePhnum.toLong(), Elf32Phdr.SIZE) { Elf32Phdr.read(this) }
        if (isDumped) {
            fixedProgramSegment()
        }
        ptDynamic = programSegment.first { it.pType == PT_DYNAMIC }
        dynamicSection = readArray(ptDynamic.pOffset, ptDynamic.pFilesz / 8L, 8) { Elf32Dyn.read(this) }
        if (isDumped) {
            fixedDynamicSection()
        }
        readSymbol()
        if (!isDumped) {
            relocationProcessing()
            if (checkProtection()) {
                maybeProtected = true
                logger.log("ERROR: This file may be protected.")
            }
        }
    }

    override fun checkSection(): Boolean {
        return try {
            val names = ArrayList<String>()
            val st = readArray(elfHeader.eShoff, elfHeader.eShnum.toLong(), 40) { Elf32Shdr.read(this) }
            sectionTable = st
            val shstrndx = st[elfHeader.eShstrndx].shOffset
            for (section in st) {
                names.add(readStringToNull(shstrndx + section.shName))
            }
            names.contains(".text")
        } catch (e: Exception) {
            false
        }
    }

    override fun mapVATR(addr: Long): Long {
        val phdr = programSegment.first { uge(addr, it.pVaddr) && ule(addr, it.pVaddr + it.pMemsz) }
        return addr - phdr.pVaddr + phdr.pOffset
    }

    override fun mapRTVA(addr: Long): Long {
        val phdr = programSegment.firstOrNull { uge(addr, it.pOffset) && ule(addr, it.pOffset + it.pFilesz) } ?: return 0L
        return addr - phdr.pOffset + phdr.pVaddr
    }

    override fun search(): Boolean {
        val globalOffsetTable = dynamicSection.first { it.dTag.toLong() == DT_PLTGOT }.dUn
        val execs = programSegment.filter { it.pType == PT_LOAD && (it.pFlags and PF_X) == 1L }
        val resultList = ArrayList<Int>()
        val featureBytes = if (elfHeader.eMachine == EM_ARM) ARMFeatureBytes else X86FeatureBytes
        for (exec in execs) {
            val start = exec.pOffset
            if (start >= length) continue
            val len = minOf(exec.pFilesz, length - start).toInt()
            for (temp in BoyerMooreHorspool.search(buf, start.toInt(), len, featureBytes)) {
                val b = buf.get(start.toInt() + temp + 2).toInt() and 0xFF
                if ((b shr 4) and 1 == 1) { // LDR
                    resultList.add(temp)
                }
            }
        }
        if (resultList.size == 1) {
            var codeRegistration = 0L
            var metadataRegistration = 0L
            val result = resultList[0].toLong() and 0xFFFFFFFFL
            if (version < 24) {
                if (elfHeader.eMachine == EM_ARM) {
                    position = result + 0x14
                    codeRegistration = (readUInt32() + globalOffsetTable) and 0xFFFFFFFFL
                    position = result + 0x18
                    val ptr = (readUInt32() + globalOffsetTable) and 0xFFFFFFFFL
                    position = mapVATR(ptr)
                    metadataRegistration = readUInt32()
                }
            } else if (version >= 24) {
                if (elfHeader.eMachine == EM_ARM) {
                    position = result + 0x14
                    codeRegistration = (readUInt32() + result + 0xcL + (imageBase and 0xFFFFFFFFL)) and 0xFFFFFFFFL
                    position = result + 0x10
                    val ptr = (readUInt32() + result + 0x8) and 0xFFFFFFFFL
                    position = mapVATR((ptr + imageBase))
                    metadataRegistration = readUInt32()
                }
            }
            logger.log("CodeRegistration : ${java.lang.Long.toHexString(codeRegistration)}")
            logger.log("MetadataRegistration : ${java.lang.Long.toHexString(metadataRegistration)}")
            init(codeRegistration, metadataRegistration)
            return true
        }
        return false
    }

    override fun plusSearch(methodCount: Int, typeDefinitionsCount: Int, imageCount: Int): Boolean {
        val sectionHelper = getSectionHelper(methodCount, typeDefinitionsCount, imageCount)
        val codeRegistration = sectionHelper.findCodeRegistration()
        val metadataRegistration = sectionHelper.findMetadataRegistration()
        return autoPlusInit(codeRegistration, metadataRegistration)
    }

    override fun symbolSearch(): Boolean {
        var codeRegistration = 0L
        var metadataRegistration = 0L
        val dynstrOffset = mapVATR(dynamicSection.first { it.dTag.toLong() == DT_STRTAB }.dUn)
        for (symbol in symbolTable ?: throw NullPointerException("symbolTable")) {
            when (readStringToNull(dynstrOffset + symbol.stName)) {
                "g_CodeRegistration" -> codeRegistration = symbol.stValue
                "g_MetadataRegistration" -> metadataRegistration = symbol.stValue
            }
        }
        if (codeRegistration > 0 && metadataRegistration > 0) {
            logger.log("Detected Symbol !")
            logger.log("CodeRegistration : ${java.lang.Long.toHexString(codeRegistration)}")
            logger.log("MetadataRegistration : ${java.lang.Long.toHexString(metadataRegistration)}")
            init(codeRegistration, metadataRegistration)
            return true
        }
        logger.log("ERROR: No symbol is detected")
        return false
    }

    private fun readSymbol() {
        try {
            var symbolCount: Long
            val hash = dynamicSection.firstOrNull { it.dTag.toLong() == DT_HASH }
            if (hash != null) {
                val addr = mapVATR(hash.dUn)
                position = addr
                readUInt32() // nbucket
                val nchain = readUInt32()
                symbolCount = nchain
            } else {
                val gnu = dynamicSection.first { it.dTag.toLong() == DT_GNU_HASH }
                val addr = mapVATR(gnu.dUn)
                position = addr
                val nbuckets = readUInt32()
                val symoffset = readUInt32()
                val bloomSize = readUInt32()
                readUInt32() // bloom_shift
                val bucketsAddress = addr + 16 + (4 * bloomSize)
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
            val dynsymOffset = mapVATR(dynamicSection.first { it.dTag.toLong() == DT_SYMTAB }.dUn)
            symbolTable = readArray(dynsymOffset, symbolCount, 16) { Elf32Sym.read(this) }
        } catch (e: Exception) {
            // ignored
        }
    }

    private fun relocationProcessing() {
        logger.log("Applying relocations...")
        try {
            val reldynOffset = mapVATR(dynamicSection.first { it.dTag.toLong() == DT_REL }.dUn)
            val reldynSize = dynamicSection.first { it.dTag.toLong() == DT_RELSZ }.dUn
            val relTable = readArray(reldynOffset, reldynSize / 8, 8) { Elf32Rel.read(this) }
            for (rel in relTable) applyRel(rel.rOffset, rel.rInfo)
        } catch (e: Exception) {
            // ignored
        }
        // Extension: Android packed relocations (APS2) produced by "-Wl,--pack-dyn-relocs=android".
        try {
            val packed = dynamicSection.firstOrNull { it.dTag.toLong() == DT_ANDROID_REL }
            val packedSize = dynamicSection.firstOrNull { it.dTag.toLong() == DT_ANDROID_RELSZ }
            if (packed != null && packedSize != null) {
                val start = mapVATR(packed.dUn)
                var count = 0
                PackedRelocationDecoder(this, start, start + packedSize.dUn).decode(rela = false) { off, info, _ ->
                    val saved = position
                    if (applyRel(off, info)) count++
                    position = saved
                }
                packedRelocationsApplied = count
                logger.log("Applied $count packed (APS2) relocations")
            }
        } catch (e: Exception) {
            logger.log("WARNING: failed to apply packed relocations: ${e.message}")
        }
    }

    private fun applyRel(rOffset: Long, rInfo: Long): Boolean {
        val type = rInfo and 0xff
        val sym = rInfo shr 8
        val isx86 = elfHeader.eMachine == 0x3
        if ((type == R_386_32 && isx86) || (type == R_ARM_ABS32 && !isx86)) {
            val symbol = symbolTable!![sym.toInt()]
            position = mapVATR(rOffset)
            writeUInt32(symbol.stValue)
            return true
        }
        return false
    }

    private fun checkProtection(): Boolean {
        try {
            if (dynamicSection.any { it.dTag.toLong() == DT_INIT }) {
                logger.log("WARNING: find .init_proc")
                return true
            }
            val dynstrOffset = mapVATR(dynamicSection.first { it.dTag.toLong() == DT_STRTAB }.dUn)
            for (symbol in symbolTable!!) {
                if (readStringToNull(dynstrOffset + symbol.stName) == "JNI_OnLoad") {
                    logger.log("WARNING: find JNI_OnLoad")
                    return true
                }
            }
            if (sectionTable != null && sectionTable!!.any { it.shType == SHT_LOUSER }) {
                logger.log("WARNING: find SHT_LOUSER section")
                return true
            }
        } catch (e: Exception) {
            // ignored
        }
        return false
    }

    override fun getRVA(pointer: Long): Long = if (isDumped) pointer - imageBase else pointer

    private fun fixedProgramSegment() {
        for (i in programSegment.indices) {
            position = elfHeader.ePhoff + i * 32L + 4L
            val phdr = programSegment[i]
            phdr.pOffset = phdr.pVaddr
            writeUInt32(phdr.pOffset)
            phdr.pVaddr = (phdr.pVaddr + imageBase) and 0xFFFFFFFFL
            writeUInt32(phdr.pVaddr)
            position += 4
            phdr.pFilesz = phdr.pMemsz
            writeUInt32(phdr.pFilesz)
        }
    }

    private fun fixedDynamicSection() {
        for (i in dynamicSection.indices) {
            position = ptDynamic.pOffset + i * 8L + 4
            val dyn = dynamicSection[i]
            when (dyn.dTag.toLong()) {
                DT_PLTGOT, DT_HASH, DT_STRTAB, DT_SYMTAB, DT_RELA, DT_INIT, DT_FINI, DT_REL, DT_JMPREL, DT_INIT_ARRAY, DT_FINI_ARRAY -> {
                    dyn.dUn = (dyn.dUn + imageBase) and 0xFFFFFFFFL
                    writeUInt32(dyn.dUn)
                }
            }
        }
    }

    override fun getSectionHelper(methodCount: Int, typeDefinitionsCount: Int, imageCount: Int): SectionHelper {
        val dataList = ArrayList<SearchSection>()
        val execList = ArrayList<SearchSection>()
        for (phdr in programSegment) {
            if (phdr.pMemsz != 0L) {
                val sec = SearchSection(phdr.pOffset, phdr.pOffset + phdr.pFilesz, phdr.pVaddr, phdr.pVaddr + phdr.pMemsz)
                when (phdr.pFlags) {
                    1L, 3L, 5L, 7L -> execList.add(sec)
                    2L, 4L, 6L -> dataList.add(sec)
                }
            }
        }
        val sectionHelper = SectionHelper(this, methodCount, typeDefinitionsCount, metadataUsagesCount, imageCount)
        sectionHelper.setSection(SearchSectionType.Exec, execList)
        sectionHelper.setSection(SearchSectionType.Data, dataList)
        sectionHelper.setSection(SearchSectionType.Bss, dataList)
        return sectionHelper
    }

    /** Program headers (for tools like the signature generator). */
    val segments: List<Elf32Phdr> get() = programSegment

    companion object {
        /* LDR R1, [X]; ADD R0, X, X; ADD R2, X, X */
        private const val ARMFeatureBytes = "? 0x10 ? 0xE7 ? 0x00 ? 0xE0 ? 0x20 ? 0xE0"
        private const val X86FeatureBytes = "? 0x10 ? 0xE7 ? 0x00 ? 0xE0 ? 0x20 ? 0xE0" // TODO
    }
}
