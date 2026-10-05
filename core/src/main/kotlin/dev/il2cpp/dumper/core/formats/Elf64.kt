package dev.il2cpp.dumper.core.formats

import dev.il2cpp.dumper.core.formats.ElfConstants.DT_ANDROID_RELA
import dev.il2cpp.dumper.core.formats.ElfConstants.DT_ANDROID_RELASZ
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
import dev.il2cpp.dumper.core.formats.ElfConstants.DT_RELASZ
import dev.il2cpp.dumper.core.formats.ElfConstants.DT_STRTAB
import dev.il2cpp.dumper.core.formats.ElfConstants.DT_SYMTAB
import dev.il2cpp.dumper.core.formats.ElfConstants.EM_AARCH64
import dev.il2cpp.dumper.core.formats.ElfConstants.EM_X86_64
import dev.il2cpp.dumper.core.formats.ElfConstants.PT_DYNAMIC
import dev.il2cpp.dumper.core.formats.ElfConstants.R_AARCH64_ABS64
import dev.il2cpp.dumper.core.formats.ElfConstants.R_AARCH64_RELATIVE
import dev.il2cpp.dumper.core.formats.ElfConstants.R_X86_64_64
import dev.il2cpp.dumper.core.formats.ElfConstants.R_X86_64_RELATIVE
import dev.il2cpp.dumper.core.formats.ElfConstants.SHT_LOUSER
import dev.il2cpp.dumper.core.utils.SearchSection
import dev.il2cpp.dumper.core.utils.SearchSectionType
import dev.il2cpp.dumper.core.utils.SectionHelper
import dev.il2cpp.dumper.core.utils.uge
import dev.il2cpp.dumper.core.utils.ule
import java.nio.ByteBuffer

/** Port of Elf64.cs */
class Elf64(buffer: ByteBuffer) : ElfBase(buffer) {
    private lateinit var elfHeader: Elf64Ehdr
    private lateinit var programSegment: List<Elf64Phdr>
    private lateinit var dynamicSection: List<Elf64Dyn>
    private var symbolTable: List<Elf64Sym>? = null
    private var sectionTable: List<Elf64Shdr>? = null
    private lateinit var ptDynamic: Elf64Phdr

    override val formatName: String get() = "ELF64"
    override val machine: Int get() = elfHeader.eMachine

    init {
        load()
    }

    override fun load() {
        position = 0
        elfHeader = Elf64Ehdr.read(this)
        programSegment = readArray(elfHeader.ePhoff, elfHeader.ePhnum.toLong(), Elf64Phdr.SIZE) { Elf64Phdr.read(this) }
        if (isDumped) {
            fixedProgramSegment()
        }
        ptDynamic = programSegment.first { it.pType == PT_DYNAMIC }
        dynamicSection = readArray(ptDynamic.pOffset, ptDynamic.pFilesz / 16L, 16) { Elf64Dyn.read(this) }
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
            val st = readArray(elfHeader.eShoff, elfHeader.eShnum.toLong(), 64) { Elf64Shdr.read(this) }
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

    override fun search(): Boolean = false

    override fun plusSearch(methodCount: Int, typeDefinitionsCount: Int, imageCount: Int): Boolean {
        val sectionHelper = getSectionHelper(methodCount, typeDefinitionsCount, imageCount)
        val codeRegistration = sectionHelper.findCodeRegistration()
        val metadataRegistration = sectionHelper.findMetadataRegistration()
        return autoPlusInit(codeRegistration, metadataRegistration)
    }

    override fun symbolSearch(): Boolean {
        var codeRegistration = 0L
        var metadataRegistration = 0L
        val dynstrOffset = mapVATR(dynamicSection.first { it.dTag == DT_STRTAB }.dUn)
        for (symbol in symbolTable ?: throw NullPointerException("symbolTable")) {
            when (readStringToNull(dynstrOffset + symbol.stName)) {
                "g_CodeRegistration" -> codeRegistration = symbol.stValue
                "g_MetadataRegistration" -> metadataRegistration = symbol.stValue
            }
        }
        if (codeRegistration != 0L && metadataRegistration != 0L) {
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
            val hash = dynamicSection.firstOrNull { it.dTag == DT_HASH }
            if (hash != null) {
                val addr = mapVATR(hash.dUn)
                position = addr
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
            for (rela in relaTable) applyRela(rela.rOffset, rela.rInfo, rela.rAddend)
        } catch (e: Exception) {
            // ignored
        }
        // Extension: Android packed relocations (APS2) - many recent libil2cpp.so builds use them.
        try {
            val packed = dynamicSection.firstOrNull { it.dTag == DT_ANDROID_RELA }
            val packedSize = dynamicSection.firstOrNull { it.dTag == DT_ANDROID_RELASZ }
            if (packed != null && packedSize != null) {
                val start = mapVATR(packed.dUn)
                var count = 0
                PackedRelocationDecoder(this, start, start + packedSize.dUn).decode(rela = true) { off, info, addend ->
                    val saved = position
                    if (applyRela(off, info, addend)) count++
                    position = saved
                }
                packedRelocationsApplied = count
                logger.log("Applied $count packed (APS2) relocations")
            }
        } catch (e: Exception) {
            logger.log("WARNING: failed to apply packed relocations: ${e.message}")
        }
    }

    private fun applyRela(rOffset: Long, rInfo: Long, rAddend: Long): Boolean {
        val type = rInfo and 0xffffffffL
        val sym = rInfo ushr 32
        val machine = elfHeader.eMachine
        val value: Long = when {
            type == R_AARCH64_ABS64 && machine == EM_AARCH64 -> symbolTable!![sym.toInt()].stValue + rAddend
            type == R_AARCH64_RELATIVE && machine == EM_AARCH64 -> rAddend
            type == R_X86_64_64 && machine == EM_X86_64 -> symbolTable!![sym.toInt()].stValue + rAddend
            type == R_X86_64_RELATIVE && machine == EM_X86_64 -> rAddend
            else -> return false
        }
        position = mapVATR(rOffset)
        write(value)
        return true
    }

    private fun checkProtection(): Boolean {
        try {
            if (dynamicSection.any { it.dTag == DT_INIT }) {
                logger.log("WARNING: find .init_proc")
                return true
            }
            val dynstrOffset = mapVATR(dynamicSection.first { it.dTag == DT_STRTAB }.dUn)
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
            position = elfHeader.ePhoff + i * 56L + 8L
            val phdr = programSegment[i]
            phdr.pOffset = phdr.pVaddr
            write(phdr.pOffset)
            phdr.pVaddr += imageBase
            write(phdr.pVaddr)
            position += 8
            phdr.pFilesz = phdr.pMemsz
            write(phdr.pFilesz)
        }
    }

    private fun fixedDynamicSection() {
        for (i in dynamicSection.indices) {
            position = ptDynamic.pOffset + i * 16L + 8
            val dyn = dynamicSection[i]
            when (dyn.dTag) {
                DT_PLTGOT, DT_HASH, DT_STRTAB, DT_SYMTAB, DT_RELA, DT_INIT, DT_FINI, DT_REL, DT_JMPREL, DT_INIT_ARRAY, DT_FINI_ARRAY -> {
                    dyn.dUn += imageBase
                    write(dyn.dUn)
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

    val segments: List<Elf64Phdr> get() = programSegment
}
