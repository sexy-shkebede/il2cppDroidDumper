package dev.il2cpp.dumper.core.formats

import dev.il2cpp.dumper.core.il2cpp.Il2Cpp
import dev.il2cpp.dumper.core.utils.SearchSection
import dev.il2cpp.dumper.core.utils.SearchSectionType
import dev.il2cpp.dumper.core.utils.SectionHelper
import dev.il2cpp.dumper.core.utils.uge
import dev.il2cpp.dumper.core.utils.ule
import java.nio.ByteBuffer

class PESectionHeader(
    val name: ByteArray,
    var virtualSize: Long,
    var virtualAddress: Long,
    var sizeOfRawData: Long,
    var pointerToRawData: Long,
    val characteristics: Long,
)

/** Port of PE.cs (GameAssembly.dll / UserAssembly.dll). */
class PE(buffer: ByteBuffer) : Il2Cpp(buffer) {
    private val sections: List<PESectionHeader>
    override val formatName: String get() = if (is32Bit) "PE32" else "PE32+"

    init {
        position = 0
        val magicDos = readUInt16()
        if (magicDos != 0x5A4D) throw IllegalArgumentException("ERROR: Invalid PE file")
        position = 0x3C
        val lfanew = readUInt32()
        position = lfanew
        if (readUInt32() != 0x4550L) throw IllegalArgumentException("ERROR: Invalid PE file")
        // FileHeader
        readUInt16() // Machine
        val numberOfSections = readUInt16()
        readUInt32(); readUInt32(); readUInt32()
        val sizeOfOptionalHeader = readUInt16()
        readUInt16() // Characteristics
        val pos = position
        val magic = readUInt16()
        position -= 2
        when (magic) {
            0x10b -> {
                is32Bit = true
                position = pos + 28
                imageBase = readUInt32()
            }
            0x20b -> {
                position = pos + 24
                imageBase = readUInt64()
            }
            else -> throw UnsupportedOperationException("Invalid Optional header magic $magic")
        }
        position = pos + sizeOfOptionalHeader
        sections = readArray(position, numberOfSections.toLong(), 40) {
            val name = readBytes(8)
            val vs = readUInt32()
            val va = readUInt32()
            val srd = readUInt32()
            val ptrd = readUInt32()
            readUInt32(); readUInt32(); readUInt16(); readUInt16()
            val ch = readUInt32()
            PESectionHeader(name, vs, va, srd, ptrd, ch)
        }
    }

    fun loadFromMemory(addr: Long) {
        imageBase = addr
        for (section in sections) {
            section.pointerToRawData = section.virtualAddress
            section.sizeOfRawData = section.virtualSize
        }
    }

    override fun mapVATR(addr: Long): Long {
        val rel = addr - imageBase
        val section = sections.firstOrNull { uge(rel, it.virtualAddress) && ule(rel, it.virtualAddress + it.virtualSize) } ?: return 0L
        return rel - section.virtualAddress + section.pointerToRawData
    }

    override fun mapRTVA(addr: Long): Long {
        val section = sections.firstOrNull { uge(addr, it.pointerToRawData) && ule(addr, it.pointerToRawData + it.sizeOfRawData) } ?: return 0L
        return addr - section.pointerToRawData + section.virtualAddress + imageBase
    }

    override fun search(): Boolean = false

    override fun plusSearch(methodCount: Int, typeDefinitionsCount: Int, imageCount: Int): Boolean {
        val sectionHelper = getSectionHelper(methodCount, typeDefinitionsCount, imageCount)
        val codeRegistration = sectionHelper.findCodeRegistration()
        val metadataRegistration = sectionHelper.findMetadataRegistration()
        return autoPlusInit(codeRegistration, metadataRegistration)
    }

    override fun symbolSearch(): Boolean = false

    override fun getRVA(pointer: Long): Long = pointer - imageBase

    override fun getSectionHelper(methodCount: Int, typeDefinitionsCount: Int, imageCount: Int): SectionHelper {
        val exec = ArrayList<SearchSection>()
        val data = ArrayList<SearchSection>()
        for (section in sections) {
            val sec = SearchSection(
                section.pointerToRawData, section.pointerToRawData + section.sizeOfRawData,
                section.virtualAddress + imageBase, section.virtualAddress + section.virtualSize + imageBase
            )
            when (section.characteristics) {
                0x60000020L -> exec.add(sec)
                0x40000040L, 0xC0000040L -> data.add(sec)
            }
        }
        val sectionHelper = SectionHelper(this, methodCount, typeDefinitionsCount, metadataUsagesCount, imageCount)
        sectionHelper.setSection(SearchSectionType.Exec, exec)
        sectionHelper.setSection(SearchSectionType.Data, data)
        sectionHelper.setSection(SearchSectionType.Bss, data)
        return sectionHelper
    }

    override fun checkDump(): Boolean = if (is32Bit) imageBase != 0x10000000L else imageBase != 0x180000000L
}
