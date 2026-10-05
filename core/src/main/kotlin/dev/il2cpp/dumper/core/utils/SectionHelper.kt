package dev.il2cpp.dumper.core.utils

import dev.il2cpp.dumper.core.formats.ElfBase
import dev.il2cpp.dumper.core.il2cpp.Il2Cpp

/** Port of SectionHelper.cs (with a batched reference scanner that keeps the original result order). */
class SectionHelper(
    private val il2Cpp: Il2Cpp,
    private val methodCount: Int,
    private val typeDefinitionsCount: Int,
    private val metadataUsagesCount: Long,
    private val imageCount: Int,
) {
    var exec: List<SearchSection> = emptyList(); private set
    var data: List<SearchSection> = emptyList(); private set
    var bss: List<SearchSection> = emptyList(); private set
    private var pointerInExec = false

    fun setSection(type: SearchSectionType, secs: List<SearchSection>) {
        when (type) {
            SearchSectionType.Exec -> exec = secs
            SearchSectionType.Data -> data = secs
            SearchSectionType.Bss -> bss = secs
        }
    }

    fun findCodeRegistration(): Long {
        if (il2Cpp.version >= 24.2) {
            var codeRegistration: Long
            if (il2Cpp is ElfBase) {
                codeRegistration = findCodeRegistrationExec()
                if (codeRegistration == 0L) {
                    codeRegistration = findCodeRegistrationData()
                } else {
                    pointerInExec = true
                }
            } else {
                codeRegistration = findCodeRegistrationData()
                if (codeRegistration == 0L) {
                    codeRegistration = findCodeRegistrationExec()
                    pointerInExec = true
                }
            }
            return codeRegistration
        }
        return findCodeRegistrationOld()
    }

    fun findMetadataRegistration(): Long {
        if (il2Cpp.version < 19) return 0L
        if (il2Cpp.version >= 27) return findMetadataRegistrationV21()
        return findMetadataRegistrationOld()
    }

    private fun findCodeRegistrationOld(): Long {
        val ptr = il2Cpp.pointerSize
        for (section in data) {
            il2Cpp.position = section.offset
            while (ult(il2Cpp.position, section.offsetEnd)) {
                val addr = il2Cpp.position
                if (il2Cpp.readIntPtr() == methodCount.toLong()) {
                    try {
                        val pointer = il2Cpp.mapVATR(il2Cpp.readUIntPtr())
                        if (checkPointerRangeDataRa(pointer)) {
                            val pointers = il2Cpp.readUIntPtrArray(pointer, methodCount.toLong())
                            if (checkPointerRangeExecVa(pointers)) {
                                return addr - section.offset + section.address
                            }
                        }
                    } catch (e: Exception) {
                        // ignored
                    }
                }
                il2Cpp.position = addr + ptr
            }
        }
        return 0L
    }

    private fun findMetadataRegistrationOld(): Long {
        val ptr = il2Cpp.pointerSize
        for (section in data) {
            il2Cpp.position = section.offset
            val end = umin(section.offsetEnd, il2Cpp.length) - ptr
            while (ult(il2Cpp.position, end)) {
                val addr = il2Cpp.position
                if (il2Cpp.readIntPtr() == typeDefinitionsCount.toLong()) {
                    try {
                        il2Cpp.position += ptr * 2
                        val pointer = il2Cpp.mapVATR(il2Cpp.readUIntPtr())
                        if (checkPointerRangeDataRa(pointer)) {
                            val pointers = il2Cpp.readUIntPtrArray(pointer, metadataUsagesCount)
                            if (checkPointerRangeBssVa(pointers)) {
                                return addr - ptr * 12 - section.offset + section.address
                            }
                        }
                    } catch (e: Exception) {
                        // ignored
                    }
                }
                il2Cpp.position = addr + ptr
            }
        }
        return 0L
    }

    private fun findMetadataRegistrationV21(): Long {
        val ptr = il2Cpp.pointerSize
        for (section in data) {
            il2Cpp.position = section.offset
            val end = umin(section.offsetEnd, il2Cpp.length) - ptr
            while (ult(il2Cpp.position, end)) {
                val addr = il2Cpp.position
                if (il2Cpp.readIntPtr() == typeDefinitionsCount.toLong()) {
                    il2Cpp.position += ptr
                    if (il2Cpp.readIntPtr() == typeDefinitionsCount.toLong()) {
                        try {
                            val pointer = il2Cpp.mapVATR(il2Cpp.readUIntPtr())
                            if (checkPointerRangeDataRa(pointer)) {
                                val pointers = il2Cpp.readUIntPtrArray(pointer, typeDefinitionsCount.toLong())
                                val flag = if (pointerInExec) checkPointerRangeExecVa(pointers) else checkPointerRangeDataVa(pointers)
                                if (flag) {
                                    return addr - ptr * 10 - section.offset + section.address
                                }
                            }
                        } catch (e: Exception) {
                            // ignored
                        }
                    }
                }
                il2Cpp.position = addr + ptr
            }
        }
        return 0L
    }

    private fun checkPointerRangeDataRa(pointer: Long): Boolean =
        data.any { uge(pointer, it.offset) && ule(pointer, it.offsetEnd) }

    private fun inRanges(x: Long, secs: List<SearchSection>): Boolean {
        for (y in secs) if (uge(x, y.address) && ule(x, y.addressEnd)) return true
        return false
    }

    private fun checkPointerRangeExecVa(pointers: LongArray): Boolean = pointers.all { inRanges(it, exec) }
    private fun checkPointerRangeDataVa(pointers: LongArray): Boolean = pointers.all { inRanges(it, data) }
    private fun checkPointerRangeBssVa(pointers: LongArray): Boolean = pointers.all { inRanges(it, bss) }

    private fun findCodeRegistrationData(): Long = findCodeRegistration2019(data)
    private fun findCodeRegistrationExec(): Long = findCodeRegistration2019(exec)

    private fun findCodeRegistration2019(secs: List<SearchSection>): Long {
        val ptr = il2Cpp.pointerSize
        for (sec in secs) {
            val start = sec.offset
            if (start < 0 || start >= il2Cpp.length) continue
            val len = minOf(sec.offsetEnd - sec.offset, il2Cpp.length - start).toInt()
            if (len <= 0) continue
            for (index in BoyerMooreHorspool.search(il2Cpp.buf, start.toInt(), len, FEATURE_BYTES)) {
                val dllva = index.toLong() + sec.address
                for (refva in findReference(dllva)) {
                    for (refva2 in findReference(refva)) {
                        if (il2Cpp.version >= 27) {
                            val targets = LongArray(imageCount) { i -> refva2 - i.toLong() * ptr }
                            val found = findReferences(targets)
                            for (i in imageCount - 1 downTo 0) {
                                for (refva3 in found[targets[i]] ?: continue) {
                                    il2Cpp.position = il2Cpp.mapVATR(refva3 - ptr)
                                    if (il2Cpp.readIntPtr() == imageCount.toLong()) {
                                        return if (il2Cpp.version >= 29) refva3 - ptr * 14 else refva3 - ptr * 13
                                    }
                                }
                            }
                        } else {
                            val targets = LongArray(imageCount) { i -> refva2 - i.toLong() * ptr }
                            val found = findReferences(targets)
                            for (i in 0 until imageCount) {
                                val list = found[targets[i]] ?: continue
                                if (list.isNotEmpty()) return list[0] - ptr * 13
                            }
                        }
                    }
                }
            }
        }
        return 0L
    }

    private val referenceCache = HashMap<Long, List<Long>>()

    private fun findReference(addr: Long): List<Long> {
        referenceCache[addr]?.let { return it }
        val r = findReferences(longArrayOf(addr))[addr] ?: emptyList()
        referenceCache[addr] = r
        return r
    }

    /** One pass over all data sections collecting the virtual addresses where any of [targets] is stored. */
    private fun findReferences(targets: LongArray): Map<Long, List<Long>> {
        val set = HashSet<Long>(targets.size * 2)
        for (t in targets) set.add(t)
        val result = HashMap<Long, MutableList<Long>>()
        val ptr = il2Cpp.pointerSize
        val single = if (targets.size == 1) targets[0] else 0L
        for (dataSec in data) {
            var position = dataSec.offset
            val end = umin(dataSec.offsetEnd, il2Cpp.length) - ptr
            while (ult(position, end)) {
                il2Cpp.position = position
                val value = il2Cpp.readUIntPtr()
                if (if (targets.size == 1) value == single else set.contains(value)) {
                    result.getOrPut(value) { ArrayList() }.add(position - dataSec.offset + dataSec.address)
                }
                position += ptr
            }
        }
        return result
    }

    companion object {
        /** "mscorlib.dll\0" */
        private val FEATURE_BYTES = byteArrayOf(0x6D, 0x73, 0x63, 0x6F, 0x72, 0x6C, 0x69, 0x62, 0x2E, 0x64, 0x6C, 0x6C, 0x00)
    }
}
