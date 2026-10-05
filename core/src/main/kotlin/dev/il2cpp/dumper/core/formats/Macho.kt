package dev.il2cpp.dumper.core.formats

import dev.il2cpp.dumper.core.il2cpp.Il2Cpp
import dev.il2cpp.dumper.core.io.BinaryStream
import dev.il2cpp.dumper.core.io.Utf8
import dev.il2cpp.dumper.core.utils.ArmUtils
import dev.il2cpp.dumper.core.utils.SearchSection
import dev.il2cpp.dumper.core.utils.SearchSectionType
import dev.il2cpp.dumper.core.utils.SectionHelper
import dev.il2cpp.dumper.core.utils.uge
import dev.il2cpp.dumper.core.utils.ule
import java.nio.ByteBuffer

class MachoSection(var sectname: String = "", var addr: Long = 0, var size: Long = 0, var offset: Long = 0, var flags: Long = 0)

class Fat(var offset: Long = 0, var size: Long = 0, var magic: Long = 0)

private fun BinaryStream.readName16(): String = Utf8.decode(readBytes(16)).trimEnd('\u0000')

/** Port of MachoFat.cs */
class MachoFat(buffer: ByteBuffer) : BinaryStream(buffer) {
    val fats: Array<Fat>

    init {
        position += 4
        val size = readBigEndianInt(readBytes(4))
        fats = Array(size) { Fat() }
        for (i in 0 until size) {
            position += 8
            fats[i].offset = readBigEndianInt(readBytes(4)).toLong() and 0xFFFFFFFFL
            fats[i].size = readBigEndianInt(readBytes(4)).toLong() and 0xFFFFFFFFL
            position += 4
        }
        for (i in 0 until size) {
            position = fats[i].offset
            fats[i].magic = readUInt32()
        }
    }

    private fun readBigEndianInt(b: ByteArray): Int {
        if (b.size < 4) throw dev.il2cpp.dumper.core.io.EndOfStreamException()
        return ((b[0].toInt() and 0xFF) shl 24) or ((b[1].toInt() and 0xFF) shl 16) or ((b[2].toInt() and 0xFF) shl 8) or (b[3].toInt() and 0xFF)
    }

    fun getMacho(index: Int): ByteArray {
        position = fats[index].offset
        return readBytes(fats[index].size.toInt())
    }
}

/** Port of Macho.cs (32-bit) */
class Macho(buffer: ByteBuffer) : Il2Cpp(buffer) {
    private val sections = ArrayList<MachoSection>()
    private var vmaddr = 0L
    override val formatName: String get() = "Mach-O 32"

    init {
        is32Bit = true
        position += 16
        val ncmds = readUInt32()
        position += 8
        for (i in 0 until ncmds) {
            val pos = position
            val cmd = readUInt32()
            val cmdsize = readUInt32()
            when (cmd) {
                1L -> { // LC_SEGMENT
                    val segname = readName16()
                    if (segname == "__TEXT") {
                        vmaddr = readUInt32()
                    } else {
                        position += 4
                    }
                    position += 20
                    val nsects = readUInt32()
                    position += 4
                    for (j in 0 until nsects) {
                        val section = MachoSection()
                        sections.add(section)
                        section.sectname = readName16()
                        position += 16
                        section.addr = readUInt32()
                        section.size = readUInt32()
                        section.offset = readUInt32()
                        position += 12
                        section.flags = readUInt32()
                        position += 8
                    }
                }
                0x21L -> { // LC_ENCRYPTION_INFO
                    position += 8
                    val cryptID = readUInt32()
                    if (cryptID != 0L) {
                        logger.log("ERROR: This Mach-O executable is encrypted and cannot be processed.")
                    }
                }
            }
            position = pos + cmdsize
        }
    }

    override fun init(codeRegistration: Long, metadataRegistration: Long) {
        super.init(codeRegistration, metadataRegistration)
        methodPointers = LongArray(methodPointers.size) { methodPointers[it] - 1 }
        customAttributeGenerators = customAttributeGenerators?.let { arr -> LongArray(arr.size) { arr[it] - 1 } }
            ?: throw NullPointerException("customAttributeGenerators")
    }

    override fun mapVATR(addr: Long): Long {
        val section = sections.first { uge(addr, it.addr) && ule(addr, it.addr + it.size) }
        return addr - section.addr + section.offset
    }

    override fun mapRTVA(addr: Long): Long {
        val section = sections.firstOrNull { uge(addr, it.offset) && ule(addr, it.offset + it.size) } ?: return 0L
        return addr - section.offset + section.addr
    }

    override fun search(): Boolean {
        val modInitFunc = sections.first { it.sectname == "__mod_init_func" }
        val addrs = readUInt32Array(modInitFunc.offset, modInitFunc.size / 4)
        for (a in addrs) {
            if (a > 0) {
                val i = a - 1
                position = mapVATR(i)
                position += 4
                var buff = readBytes(2)
                if (FeatureBytes1.contentEquals(buff)) {
                    position += 12
                    buff = readBytes(4)
                    if (FeatureBytes2.contentEquals(buff)) {
                        position = mapVATR(i) + 10
                        val subaddr = (ArmUtils.decodeMov(readBytes(8)) + i + 24 - 1) and 0xFFFFFFFFL
                        val rsubaddr = mapVATR(subaddr)
                        position = rsubaddr
                        val ptr = (ArmUtils.decodeMov(readBytes(8)) + subaddr + 16) and 0xFFFFFFFFL
                        position = mapVATR(ptr)
                        val metadataRegistration = readUInt32()
                        position = rsubaddr + 8
                        buff = readBytes(4)
                        position = rsubaddr + 14
                        buff += readBytes(4)
                        val codeRegistration = (ArmUtils.decodeMov(buff) + subaddr + (if (version < 21) 22 else 26)) and 0xFFFFFFFFL
                        logger.log("CodeRegistration : ${java.lang.Long.toHexString(codeRegistration)}")
                        logger.log("MetadataRegistration : ${java.lang.Long.toHexString(metadataRegistration)}")
                        init(codeRegistration, metadataRegistration)
                        return true
                    }
                }
            }
        }
        return false
    }

    override fun plusSearch(methodCount: Int, typeDefinitionsCount: Int, imageCount: Int): Boolean {
        val sectionHelper = getSectionHelper(methodCount, typeDefinitionsCount, imageCount)
        val codeRegistration = sectionHelper.findCodeRegistration()
        val metadataRegistration = sectionHelper.findMetadataRegistration()
        return autoPlusInit(codeRegistration, metadataRegistration)
    }

    override fun symbolSearch(): Boolean = false

    override fun getRVA(pointer: Long): Long = pointer - vmaddr

    override fun getSectionHelper(methodCount: Int, typeDefinitionsCount: Int, imageCount: Int): SectionHelper {
        fun conv(list: List<MachoSection>) = list.map { SearchSection(it.offset, it.offset + it.size, it.addr, it.addr + it.size) }
        val data = sections.filter { it.sectname == "__const" }
        val code = sections.filter { it.flags == 0x80000400L }
        val bss = sections.filter { it.flags == 1L }
        val sectionHelper = SectionHelper(this, methodCount, typeDefinitionsCount, metadataUsagesCount, imageCount)
        sectionHelper.setSection(SearchSectionType.Exec, conv(code))
        sectionHelper.setSection(SearchSectionType.Data, conv(data))
        sectionHelper.setSection(SearchSectionType.Bss, conv(bss))
        return sectionHelper
    }

    override fun checkDump(): Boolean = false

    companion object {
        private val FeatureBytes1 = byteArrayOf(0x0, 0x22) // MOVS R2, #0
        private val FeatureBytes2 = byteArrayOf(0x78, 0x44, 0x79, 0x44) // ADD R0, PC and ADD R1, PC
    }
}

/** Port of Macho64.cs */
class Macho64(buffer: ByteBuffer) : Il2Cpp(buffer) {
    private val sections = ArrayList<MachoSection>()
    private var vmaddr = 0L
    override val formatName: String get() = "Mach-O 64"

    init {
        position += 16
        val ncmds = readUInt32()
        position += 12
        for (i in 0 until ncmds) {
            val pos = position
            val cmd = readUInt32()
            val cmdsize = readUInt32()
            when (cmd) {
                0x19L -> { // LC_SEGMENT_64
                    val segname = readName16()
                    if (segname == "__TEXT") {
                        vmaddr = readUInt64()
                    } else {
                        position += 8
                    }
                    position += 32
                    val nsects = readUInt32()
                    position += 4
                    for (j in 0 until nsects) {
                        val section = MachoSection()
                        sections.add(section)
                        section.sectname = readName16()
                        position += 16
                        section.addr = readUInt64()
                        section.size = readUInt64()
                        section.offset = readUInt32()
                        position += 12
                        section.flags = readUInt32()
                        position += 12
                    }
                }
                0x2CL -> { // LC_ENCRYPTION_INFO_64
                    position += 8
                    val cryptID = readUInt32()
                    if (cryptID != 0L) {
                        logger.log("ERROR: This Mach-O executable is encrypted and cannot be processed.")
                    }
                }
            }
            position = pos + cmdsize
        }
    }

    override fun mapVATR(addr: Long): Long {
        val section = sections.first { uge(addr, it.addr) && ule(addr, it.addr + it.size) }
        if (section.sectname == "__bss") throw IllegalStateException("__bss")
        return addr - section.addr + section.offset
    }

    override fun mapRTVA(addr: Long): Long {
        val section = sections.firstOrNull { uge(addr, it.offset) && ule(addr, it.offset + it.size) } ?: return 0L
        if (section.sectname == "__bss") throw IllegalStateException("__bss")
        return addr - section.offset + section.addr
    }

    override fun search(): Boolean {
        var codeRegistration = 0L
        var metadataRegistration = 0L
        if (version < 23) {
            val modInitFunc = sections.first { it.sectname == "__mod_init_func" }
            val addrs = readUIntPtrArray(modInitFunc.offset, modInitFunc.size / 8)
            for (i in addrs) {
                if (i != 0L) {
                    var flag = false
                    var subaddr = 0L
                    position = mapVATR(i)
                    var buff = readBytes(4)
                    if (FeatureBytes1.contentEquals(buff)) {
                        buff = readBytes(4)
                        if (FeatureBytes2.contentEquals(buff)) {
                            position += 8
                            val inst = readBytes(4)
                            if (ArmUtils.isAdr(inst)) {
                                subaddr = ArmUtils.decodeAdr(i + 16, inst)
                                flag = true
                            }
                        }
                    } else {
                        position += 0xc
                        buff = readBytes(4)
                        if (FeatureBytes2.contentEquals(buff)) {
                            buff = readBytes(4)
                            if (FeatureBytes1.contentEquals(buff)) {
                                position -= 0x10
                                val inst = readBytes(4)
                                if (ArmUtils.isAdr(inst)) {
                                    subaddr = ArmUtils.decodeAdr(i + 8, inst)
                                    flag = true
                                }
                            }
                        }
                    }
                    if (flag) {
                        val rsubaddr = mapVATR(subaddr)
                        position = rsubaddr
                        codeRegistration = ArmUtils.decodeAdrp(subaddr, readBytes(4))
                        codeRegistration += ArmUtils.decodeAdd(readBytes(4))
                        position = rsubaddr + 8
                        metadataRegistration = ArmUtils.decodeAdrp(subaddr + 8, readBytes(4))
                        metadataRegistration += ArmUtils.decodeAdd(readBytes(4))
                    }
                }
            }
        }
        if (version == 23.0 || version >= 24) {
            // v23: MOV X2,#0 ; MOV W3,#0  -  v24+: MOV W3,#0 ; MOV X2,#0
            val first = if (version == 23.0) FeatureBytes1 else FeatureBytes2
            val second = if (version == 23.0) FeatureBytes2 else FeatureBytes1
            val modInitFunc = sections.first { it.sectname == "__mod_init_func" }
            val addrs = readUIntPtrArray(modInitFunc.offset, modInitFunc.size / 8)
            for (i in addrs) {
                if (i != 0L) {
                    position = mapVATR(i) + 16
                    var buff = readBytes(4)
                    if (first.contentEquals(buff)) {
                        buff = readBytes(4)
                        if (second.contentEquals(buff)) {
                            position -= 16
                            val subaddr = ArmUtils.decodeAdr(i + 8, readBytes(4))
                            val rsubaddr = mapVATR(subaddr)
                            position = rsubaddr
                            codeRegistration = ArmUtils.decodeAdrp(subaddr, readBytes(4))
                            codeRegistration += ArmUtils.decodeAdd(readBytes(4))
                            position = rsubaddr + 8
                            metadataRegistration = ArmUtils.decodeAdrp(subaddr + 8, readBytes(4))
                            metadataRegistration += ArmUtils.decodeAdd(readBytes(4))
                        }
                    }
                }
            }
        }
        if (codeRegistration != 0L && metadataRegistration != 0L) {
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

    override fun symbolSearch(): Boolean = false

    override fun getRVA(pointer: Long): Long = pointer - vmaddr

    override fun getSectionHelper(methodCount: Int, typeDefinitionsCount: Int, imageCount: Int): SectionHelper {
        fun conv(list: List<MachoSection>) = list.map { SearchSection(it.offset, it.offset + it.size, it.addr, it.addr + it.size) }
        val data = sections.filter { it.sectname == "__const" || it.sectname == "__cstring" || it.sectname == "__data" }
        val code = sections.filter { it.flags == 0x80000400L }
        val bss = sections.filter { it.flags == 1L }
        val sectionHelper = SectionHelper(this, methodCount, typeDefinitionsCount, metadataUsagesCount, imageCount)
        sectionHelper.setSection(SearchSectionType.Exec, conv(code))
        sectionHelper.setSection(SearchSectionType.Data, conv(data))
        sectionHelper.setSection(SearchSectionType.Bss, conv(bss))
        return sectionHelper
    }

    override fun checkDump(): Boolean = false

    override fun readUIntPtr(): Long {
        var pointer = readUInt64()
        if (java.lang.Long.compareUnsigned(pointer, vmaddr + 0xFFFFFFFFL) > 0) {
            val addr = position
            val section = sections.first { uge(addr, it.offset) && ule(addr, it.offset + it.size) }
            if (section.sectname == "__const" || section.sectname == "__data") {
                var rva = pointer - vmaddr
                rva = rva and 0xFFFFFFFFL
                pointer = rva + vmaddr
            }
        }
        return pointer
    }

    companion object {
        private val FeatureBytes1 = byteArrayOf(0x2, 0x0, 0x80.toByte(), 0xD2.toByte()) // MOV X2, #0
        private val FeatureBytes2 = byteArrayOf(0x3, 0x0, 0x80.toByte(), 0x52) // MOV W3, #0
    }
}
