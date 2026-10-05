package dev.il2cpp.dumper.core.formats

import dev.il2cpp.dumper.core.il2cpp.Il2Cpp
import dev.il2cpp.dumper.core.io.BinaryStream
import dev.il2cpp.dumper.core.utils.SearchSection
import dev.il2cpp.dumper.core.utils.SearchSectionType
import dev.il2cpp.dumper.core.utils.SectionHelper
import java.nio.ByteBuffer

class DataSection(var index: Long = 0, var offset: Long = 0, var data: ByteArray = ByteArray(0))

/** Port of WebAssembly.cs */
class WebAssembly(buffer: ByteBuffer) : BinaryStream(buffer) {
    private var dataSections: Array<DataSection> = emptyArray()

    init {
        is32Bit = true
        position = 0
        readUInt32() // magic
        readInt32() // version
        while (position < length) {
            val id = readULeb128()
            val len = readULeb128()
            if (id == 11L) {
                val count = readULeb128()
                dataSections = Array(count.toInt()) { DataSection() }
                for (i in 0 until count.toInt()) {
                    val dataSection = dataSections[i]
                    dataSection.index = readULeb128()
                    var opCode = readByte()
                    if (opCode != 0x41) throw IllegalStateException("Unsupported data segment (i32.const expected)")
                    dataSection.offset = readULeb128()
                    opCode = readByte()
                    if (opCode != 0xB) throw IllegalStateException("Unsupported data segment (end expected)")
                    dataSection.data = readBytes(readULeb128().toInt())
                }
                break
            }
            position += len
        }
    }

    fun createMemory(): WebAssemblyMemory {
        val last = dataSections.last()
        val bssStart = last.offset + last.data.size
        val mem = ByteArray(length.toInt())
        for (dataSection in dataSections) {
            System.arraycopy(dataSection.data, 0, mem, dataSection.offset.toInt(), dataSection.data.size)
        }
        return WebAssemblyMemory(ByteBuffer.wrap(mem), bssStart)
    }
}

/** Port of WebAssemblyMemory.cs */
class WebAssemblyMemory(buffer: ByteBuffer, private val bssStart: Long) : Il2Cpp(buffer) {
    override val formatName: String get() = "WebAssembly"

    init {
        is32Bit = true
    }

    override fun mapVATR(addr: Long): Long = addr
    override fun mapRTVA(addr: Long): Long = addr

    override fun plusSearch(methodCount: Int, typeDefinitionsCount: Int, imageCount: Int): Boolean {
        val sectionHelper = getSectionHelper(methodCount, typeDefinitionsCount, imageCount)
        val codeRegistration = sectionHelper.findCodeRegistration()
        val metadataRegistration = sectionHelper.findMetadataRegistration()
        return autoPlusInit(codeRegistration, metadataRegistration)
    }

    override fun search(): Boolean = false
    override fun symbolSearch(): Boolean = false

    override fun getSectionHelper(methodCount: Int, typeDefinitionsCount: Int, imageCount: Int): SectionHelper {
        val exec = SearchSection(0, methodCount.toLong(), 0, methodCount.toLong())
        val data = SearchSection(1024, length, 1024, length)
        val bss = SearchSection(bssStart, Long.MAX_VALUE, bssStart, Long.MAX_VALUE)
        val sectionHelper = SectionHelper(this, methodCount, typeDefinitionsCount, metadataUsagesCount, imageCount)
        sectionHelper.setSection(SearchSectionType.Exec, listOf(exec))
        sectionHelper.setSection(SearchSectionType.Data, listOf(data))
        sectionHelper.setSection(SearchSectionType.Bss, listOf(bss))
        return sectionHelper
    }

    override fun checkDump(): Boolean = false
}
