package dev.il2cpp.dumper.core.formats

import dev.il2cpp.dumper.core.io.BinaryStream

/* Port of ElfClass.cs. Fields are plain values; unsigned 32-bit values are stored in Longs. */

class Elf32Ehdr {
    var eiMag = 0L
    var eiClass = 0
    var eiData = 0
    var eType = 0
    var eMachine = 0
    var eVersion = 0L
    var eEntry = 0L
    var ePhoff = 0L
    var eShoff = 0L
    var eFlags = 0L
    var eEhsize = 0
    var ePhentsize = 0
    var ePhnum = 0
    var eShentsize = 0
    var eShnum = 0
    var eShstrndx = 0

    companion object {
        fun read(s: BinaryStream): Elf32Ehdr {
            val h = Elf32Ehdr()
            h.eiMag = s.readUInt32()
            h.eiClass = s.readByte()
            h.eiData = s.readByte()
            s.readByte(); s.readByte(); s.readByte() // ei_version, ei_osabi, ei_abiversion
            s.readBytes(7) // ei_pad
            h.eType = s.readUInt16()
            h.eMachine = s.readUInt16()
            h.eVersion = s.readUInt32()
            h.eEntry = s.readUInt32()
            h.ePhoff = s.readUInt32()
            h.eShoff = s.readUInt32()
            h.eFlags = s.readUInt32()
            h.eEhsize = s.readUInt16()
            h.ePhentsize = s.readUInt16()
            h.ePhnum = s.readUInt16()
            h.eShentsize = s.readUInt16()
            h.eShnum = s.readUInt16()
            h.eShstrndx = s.readUInt16()
            return h
        }
    }
}

class Elf32Phdr {
    var pType = 0L
    var pOffset = 0L
    var pVaddr = 0L
    var pPaddr = 0L
    var pFilesz = 0L
    var pMemsz = 0L
    var pFlags = 0L
    var pAlign = 0L

    companion object {
        const val SIZE = 32
        fun read(s: BinaryStream): Elf32Phdr {
            val p = Elf32Phdr()
            p.pType = s.readUInt32()
            p.pOffset = s.readUInt32()
            p.pVaddr = s.readUInt32()
            p.pPaddr = s.readUInt32()
            p.pFilesz = s.readUInt32()
            p.pMemsz = s.readUInt32()
            p.pFlags = s.readUInt32()
            p.pAlign = s.readUInt32()
            return p
        }
    }
}

class Elf32Shdr(
    val shName: Long, val shType: Long, val shFlags: Long, val shAddr: Long, val shOffset: Long,
    val shSize: Long, val shLink: Long, val shInfo: Long, val shAddralign: Long, val shEntsize: Long,
) {
    companion object {
        fun read(s: BinaryStream) = Elf32Shdr(
            s.readUInt32(), s.readUInt32(), s.readUInt32(), s.readUInt32(), s.readUInt32(),
            s.readUInt32(), s.readUInt32(), s.readUInt32(), s.readUInt32(), s.readUInt32()
        )
    }
}

class Elf32Sym(val stName: Long, val stValue: Long, val stSize: Long, val stInfo: Int, val stOther: Int, val stShndx: Int) {
    companion object {
        fun read(s: BinaryStream) = Elf32Sym(s.readUInt32(), s.readUInt32(), s.readUInt32(), s.readByte(), s.readByte(), s.readUInt16())
    }
}

class Elf32Dyn(val dTag: Int, var dUn: Long) {
    companion object {
        fun read(s: BinaryStream) = Elf32Dyn(s.readInt32(), s.readUInt32())
    }
}

class Elf32Rel(val rOffset: Long, val rInfo: Long) {
    companion object {
        fun read(s: BinaryStream) = Elf32Rel(s.readUInt32(), s.readUInt32())
    }
}

class Elf64Ehdr {
    var eiMag = 0L
    var eiClass = 0
    var eiData = 0
    var eType = 0
    var eMachine = 0
    var eVersion = 0L
    var eEntry = 0L
    var ePhoff = 0L
    var eShoff = 0L
    var eFlags = 0L
    var eEhsize = 0
    var ePhentsize = 0
    var ePhnum = 0
    var eShentsize = 0
    var eShnum = 0
    var eShstrndx = 0

    companion object {
        fun read(s: BinaryStream): Elf64Ehdr {
            val h = Elf64Ehdr()
            h.eiMag = s.readUInt32()
            h.eiClass = s.readByte()
            h.eiData = s.readByte()
            s.readByte(); s.readByte(); s.readByte()
            s.readBytes(7)
            h.eType = s.readUInt16()
            h.eMachine = s.readUInt16()
            h.eVersion = s.readUInt32()
            h.eEntry = s.readUInt64()
            h.ePhoff = s.readUInt64()
            h.eShoff = s.readUInt64()
            h.eFlags = s.readUInt32()
            h.eEhsize = s.readUInt16()
            h.ePhentsize = s.readUInt16()
            h.ePhnum = s.readUInt16()
            h.eShentsize = s.readUInt16()
            h.eShnum = s.readUInt16()
            h.eShstrndx = s.readUInt16()
            return h
        }
    }
}

class Elf64Phdr {
    var pType = 0L
    var pFlags = 0L
    var pOffset = 0L
    var pVaddr = 0L
    var pPaddr = 0L
    var pFilesz = 0L
    var pMemsz = 0L
    var pAlign = 0L

    companion object {
        const val SIZE = 56
        fun read(s: BinaryStream): Elf64Phdr {
            val p = Elf64Phdr()
            p.pType = s.readUInt32()
            p.pFlags = s.readUInt32()
            p.pOffset = s.readUInt64()
            p.pVaddr = s.readUInt64()
            p.pPaddr = s.readUInt64()
            p.pFilesz = s.readUInt64()
            p.pMemsz = s.readUInt64()
            p.pAlign = s.readUInt64()
            return p
        }
    }
}

class Elf64Shdr(
    val shName: Long, val shType: Long, val shFlags: Long, val shAddr: Long, val shOffset: Long,
    val shSize: Long, val shLink: Long, val shInfo: Long, val shAddralign: Long, val shEntsize: Long,
) {
    companion object {
        fun read(s: BinaryStream) = Elf64Shdr(
            s.readUInt32(), s.readUInt32(), s.readUInt64(), s.readUInt64(), s.readUInt64(),
            s.readUInt64(), s.readUInt32(), s.readUInt32(), s.readUInt64(), s.readUInt64()
        )
    }
}

class Elf64Sym(val stName: Long, val stInfo: Int, val stOther: Int, val stShndx: Int, val stValue: Long, val stSize: Long) {
    companion object {
        fun read(s: BinaryStream) = Elf64Sym(s.readUInt32(), s.readByte(), s.readByte(), s.readUInt16(), s.readUInt64(), s.readUInt64())
    }
}

class Elf64Dyn(val dTag: Long, var dUn: Long) {
    companion object {
        /** d_tag is a long -> ReadIntPtr in the original. */
        fun read(s: BinaryStream) = Elf64Dyn(s.readIntPtr(), s.readUIntPtr())
    }
}

class Elf64Rela(val rOffset: Long, val rInfo: Long, val rAddend: Long) {
    companion object {
        fun read(s: BinaryStream) = Elf64Rela(s.readUIntPtr(), s.readUIntPtr(), s.readUIntPtr())
    }
}

object ElfConstants {
    // e_machine
    const val EM_386 = 3
    const val EM_ARM = 40
    const val EM_X86_64 = 62
    const val EM_AARCH64 = 183

    // p_type
    const val PT_LOAD = 1L
    const val PT_DYNAMIC = 2L

    // p_flags
    const val PF_X = 1L

    // d_tag
    const val DT_NULL = 0L
    const val DT_PLTGOT = 3L
    const val DT_HASH = 4L
    const val DT_STRTAB = 5L
    const val DT_SYMTAB = 6L
    const val DT_RELA = 7L
    const val DT_RELASZ = 8L
    const val DT_INIT = 12L
    const val DT_FINI = 13L
    const val DT_REL = 17L
    const val DT_RELSZ = 18L
    const val DT_JMPREL = 23L
    const val DT_INIT_ARRAY = 25L
    const val DT_FINI_ARRAY = 26L
    const val DT_RELRSZ = 35L
    const val DT_RELR = 36L
    const val DT_GNU_HASH = 0x6ffffef5L
    const val DT_ANDROID_REL = 0x6000000FL
    const val DT_ANDROID_RELSZ = 0x60000010L
    const val DT_ANDROID_RELA = 0x60000011L
    const val DT_ANDROID_RELASZ = 0x60000012L
    const val DT_ANDROID_RELR = 0x6fffe000L
    const val DT_ANDROID_RELRSZ = 0x6fffe001L

    // sh_type
    const val SHT_LOUSER = 0x80000000L

    // relocations
    const val R_ARM_ABS32 = 2L
    const val R_ARM_RELATIVE = 23L
    const val R_386_32 = 1L
    const val R_386_RELATIVE = 8L
    const val R_AARCH64_ABS64 = 257L
    const val R_AARCH64_RELATIVE = 1027L
    const val R_X86_64_64 = 1L
    const val R_X86_64_RELATIVE = 8L
}
