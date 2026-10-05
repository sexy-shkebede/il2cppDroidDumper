package dev.il2cpp.dumper.core.formats

import dev.il2cpp.dumper.core.il2cpp.Il2Cpp
import dev.il2cpp.dumper.core.io.BinaryStream
import java.nio.ByteBuffer

abstract class ElfBase(buffer: ByteBuffer) : Il2Cpp(buffer) {
    protected abstract fun load()
    protected abstract fun checkSection(): Boolean

    override fun checkDump(): Boolean = !checkSection()

    fun reload() = load()

    /** True when the binary looked protected (.init_proc / JNI_OnLoad / SHT_LOUSER). */
    var maybeProtected = false
        protected set

    /** Number of relocations applied from Android packed (APS2) tables - an extension over the original. */
    var packedRelocationsApplied = 0
        protected set

    abstract val machine: Int

    val archName: String
        get() = when (machine) {
            ElfConstants.EM_AARCH64 -> "arm64-v8a"
            ElfConstants.EM_ARM -> "armeabi-v7a"
            ElfConstants.EM_386 -> "x86"
            ElfConstants.EM_X86_64 -> "x86_64"
            else -> "machine $machine"
        }
}

/** Decoder for Android "APS2" packed relocation tables (DT_ANDROID_REL/RELA). */
internal class PackedRelocationDecoder(private val s: BinaryStream, start: Long, private val end: Long) {
    init {
        s.position = start
    }

    fun readSleb(): Long {
        var value = 0L
        var shift = 0
        var b: Int
        do {
            if (s.position >= end) throw IllegalStateException("APS2 overflow")
            b = s.readByte()
            value = value or ((b and 0x7f).toLong() shl shift)
            shift += 7
        } while (b and 0x80 != 0 && shift < 64)
        if (shift < 64 && (b and 0x40) != 0) {
            value = value or (-1L shl shift)
        }
        return value
    }

    /** Calls [emit] with (r_offset, r_info, r_addend) for every relocation. */
    fun decode(rela: Boolean, emit: (Long, Long, Long) -> Unit) {
        val magic = s.readBytes(4)
        if (magic.size != 4 || magic[0] != 'A'.code.toByte() || magic[1] != 'P'.code.toByte() ||
            magic[2] != 'S'.code.toByte() || magic[3] != '2'.code.toByte()
        ) throw IllegalStateException("bad APS2 magic")
        val count = readSleb()
        var rOffset = readSleb()
        var rInfo = 0L
        var rAddend = 0L
        var done = 0L
        while (done < count) {
            val groupSize = readSleb()
            val groupFlags = readSleb()
            val byInfo = groupFlags and 1L != 0L
            val byOffsetDelta = groupFlags and 2L != 0L
            val byAddend = groupFlags and 4L != 0L
            val hasAddend = groupFlags and 8L != 0L
            var groupOffsetDelta = 0L
            if (byOffsetDelta) groupOffsetDelta = readSleb()
            if (byInfo) rInfo = readSleb()
            if (rela) {
                if (hasAddend && byAddend) rAddend += readSleb()
                else if (!hasAddend) rAddend = 0
            }
            var i = 0L
            while (i < groupSize && done < count) {
                rOffset += if (byOffsetDelta) groupOffsetDelta else readSleb()
                if (!byInfo) rInfo = readSleb()
                if (rela && hasAddend && !byAddend) rAddend += readSleb()
                emit(rOffset, rInfo, rAddend)
                i++
                done++
            }
        }
    }
}
