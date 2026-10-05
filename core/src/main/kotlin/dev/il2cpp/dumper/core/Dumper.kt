package dev.il2cpp.dumper.core

import dev.il2cpp.dumper.core.extra.CodeGen
import dev.il2cpp.dumper.core.extra.DumpIndex
import dev.il2cpp.dumper.core.extra.DumpInfo
import dev.il2cpp.dumper.core.extra.StringLiteralEntry
import dev.il2cpp.dumper.core.formats.ElfBase
import dev.il2cpp.dumper.core.formats.Elf
import dev.il2cpp.dumper.core.formats.Elf64
import dev.il2cpp.dumper.core.formats.Fat
import dev.il2cpp.dumper.core.formats.Macho
import dev.il2cpp.dumper.core.formats.Macho64
import dev.il2cpp.dumper.core.formats.MachoFat
import dev.il2cpp.dumper.core.formats.NSO
import dev.il2cpp.dumper.core.formats.PE
import dev.il2cpp.dumper.core.formats.WebAssembly
import dev.il2cpp.dumper.core.il2cpp.Il2Cpp
import dev.il2cpp.dumper.core.il2cpp.Metadata
import dev.il2cpp.dumper.core.outputs.Il2CppDecompiler
import dev.il2cpp.dumper.core.outputs.StructGenerator
import dev.il2cpp.dumper.core.utils.Il2CppExecutor
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class DumpException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Decisions the desktop tool asked on the console. Implementations may show UI. */
interface DumpInteraction {
    /** Called for files that look like memory dumps. Return the base address (0 = continue as normal file). */
    fun askDumpAddress(): Long = 0L

    /** Called when auto search fails. Return (CodeRegistration, MetadataRegistration) or null to abort. */
    fun askRegistrations(): Pair<Long, Long>? = null

    /** Mach-O FAT: choose architecture index. Default prefers 64-bit. */
    fun chooseFatArch(fats: Array<Fat>): Int = fats.indexOfFirst { it.magic == 0xFEEDFACFL }.coerceAtLeast(0)

    companion object {
        val DEFAULT = object : DumpInteraction {}
    }
}

/** Extra information about the source, stored in the index (title, package, unity version). */
data class SourceInfo(
    val title: String = "",
    val packageName: String = "",
    val unityVersion: String = "",
)

data class DumpResult(
    val outputDir: File,
    val files: List<File>,
    val metadataVersion: Double,
    val il2cppVersion: Double,
    val formatName: String,
    val arch: String,
    val imageCount: Int,
    val typeCount: Int,
    val methodCount: Int,
    val stringLiteralCount: Int,
    val isDumped: Boolean,
    val maybeProtected: Boolean,
    val codeRegistration: Long,
    val metadataRegistration: Long,
    val elapsedMs: Long,
    val index: DumpIndex?,
)

/** Port of Program.cs (Init + Dump) as a reusable API. */
class Dumper(
    private val config: Config = Config(),
    private val logger: DumpLogger = DumpLogger.STDOUT,
    private val progress: DumpProgress = DumpProgress.NONE,
    private val interaction: DumpInteraction = DumpInteraction.DEFAULT,
) {
    lateinit var metadata: Metadata
        private set
    lateinit var il2Cpp: Il2Cpp
        private set

    fun dump(il2cppFile: File, metadataFile: File, outputDir: File, source: SourceInfo = SourceInfo()): DumpResult =
        dump(mapFile(il2cppFile, writable = true), mapFile(metadataFile, writable = false), outputDir, source)

    fun dump(il2cppBuffer: ByteBuffer, metadataBuffer: ByteBuffer, outputDir: File, source: SourceInfo = SourceInfo()): DumpResult {
        val start = System.currentTimeMillis()
        outputDir.mkdirs()
        init(il2cppBuffer, metadataBuffer)
        return write(outputDir, source, start)
    }

    fun init(il2cppBuffer: ByteBuffer, metadataBuffer: ByteBuffer) {
        progress.onProgress("metadata", -1f)
        logger.log("Initializing metadata...")
        metadata = try {
            Metadata(metadataBuffer)
        } catch (e: Exception) {
            throw DumpException(e.message ?: "ERROR: Metadata file supplied is not valid metadata file.", e)
        }
        logger.log("Metadata Version: ${Il2Cpp.fmtVersion(metadata.version)}")

        progress.onProgress("il2cpp", -1f)
        logger.log("Initializing il2cpp file...")
        il2Cpp = openIl2Cpp(il2cppBuffer)
        val version = if (config.forceIl2CppVersion) config.forceVersion else metadata.version
        il2Cpp.setProperties(version, metadata.metadataUsagesCount)
        logger.log("Il2Cpp Version: ${Il2Cpp.fmtVersion(il2Cpp.version)}")
        if (config.forceDump || il2Cpp.checkDump()) {
            val il = il2Cpp
            if (il is ElfBase) {
                logger.log("Detected this may be a dump file.")
                val dumpAddr = interaction.askDumpAddress()
                if (dumpAddr != 0L) {
                    il.imageBase = dumpAddr
                    il.isDumped = true
                    if (!config.noRedirectedPointer) {
                        il.reload()
                    }
                }
            } else {
                il.isDumped = true
            }
        }

        progress.onProgress("searching", -1f)
        logger.log("Searching...")
        try {
            val methodCount = metadata.methodDefs.count { it.methodIndex >= 0 }
            var flag = il2Cpp.plusSearch(methodCount, metadata.typeDefs.size, metadata.imageDefs.size)
            progress.checkCancelled()
            if (!flag) flag = il2Cpp.search()
            if (!flag) flag = il2Cpp.symbolSearch()
            if (!flag) {
                logger.log("ERROR: Can't use auto mode to process file, try manual mode.")
                val regs = interaction.askRegistrations()
                    ?: throw DumpException("ERROR: Can't use auto mode to process file, try manual mode.")
                il2Cpp.init(regs.first, regs.second)
            }
            if (il2Cpp.version >= 27 && il2Cpp.isDumped) {
                val typeDef = metadata.typeDefs[0]
                val il2CppType = il2Cpp.types[typeDef.byvalTypeIndex]
                metadata.imageBase = il2CppType.typeHandle - metadata.header.typeDefinitionsOffset
            }
        } catch (e: DumpException) {
            throw e
        } catch (e: java.util.concurrent.CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.log(e.toString())
            throw DumpException("ERROR: An error occurred while processing.", e)
        }
    }

    private fun openIl2Cpp(buffer: ByteBuffer): Il2Cpp {
        val buf = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        if (buf.limit() < 8) throw DumpException("ERROR: il2cpp file not supported.")
        val magic = buf.getInt(0).toLong() and 0xFFFFFFFFL
        val il: Il2Cpp = withLogger { when (magic) {
            0x6D736100L -> WebAssembly(buf).createMemory()
            0x304F534EL -> NSO(buf).unCompress()
            0x905A4DL -> PE(buf)
            0x464c457fL -> if (buf.get(4).toInt() == 2) Elf64(buf) else Elf(buf)
            0xCAFEBABEL, 0xBEBAFECAL -> {
                val fat = MachoFat(buf)
                val index = interaction.chooseFatArch(fat.fats)
                val m = fat.fats[index % 2].magic
                val bytes = ByteBuffer.wrap(fat.getMacho(index % 2))
                if (m == 0xFEEDFACFL) Macho64(bytes) else Macho(bytes)
            }
            0xFEEDFACFL -> Macho64(buf)
            0xFEEDFACEL -> Macho(buf)
            else -> throw DumpException("ERROR: il2cpp file not supported.")
        } }
        il.logger = logger
        return il
    }

    /** ELF constructors log while loading, so the logger must be set before construction. */
    private inline fun <T : Il2Cpp> withLogger(block: () -> T): T {
        val previous = pendingLogger.get()
        pendingLogger.set(logger)
        try {
            return block()
        } finally {
            pendingLogger.set(previous)
        }
    }

    private fun write(outputDir: File, source: SourceInfo, start: Long): DumpResult {
        logger.log("Dumping...")
        val executor = Il2CppExecutor(metadata, il2Cpp)
        val decompiler = Il2CppDecompiler(executor, logger)
        decompiler.decompile(config, outputDir, config.generateIndex, progress)
        logger.log("Done!")
        var structGenerator: StructGenerator? = null
        if (config.generateStruct) {
            logger.log("Generate struct...")
            structGenerator = StructGenerator(executor, logger)
            structGenerator.writeScript(outputDir, progress)
            logger.log("Done!")
        }
        val arch = (il2Cpp as? ElfBase)?.archName ?: if (il2Cpp.is32Bit) "32-bit" else "64-bit"
        var index: DumpIndex? = null
        if (config.generateIndex) {
            progress.onProgress("index", -1f)
            val strings = structGenerator?.lastScriptJson?.scriptString?.map { StringLiteralEntry(it.address, it.value ?: "") } ?: emptyList()
            val info = DumpInfo(
                title = source.title, packageName = source.packageName, unityVersion = source.unityVersion,
                metadataVersion = Il2Cpp.fmtVersion(metadata.version), il2cppVersion = Il2Cpp.fmtVersion(il2Cpp.version),
                format = il2Cpp.formatName, arch = arch, imageBase = il2Cpp.imageBase,
                codeRegistration = il2Cpp.codeRegistrationAddress, metadataRegistration = il2Cpp.metadataRegistrationAddress,
                createdAt = System.currentTimeMillis(), is32Bit = il2Cpp.is32Bit, isDumped = il2Cpp.isDumped,
            )
            index = DumpIndex(info, metadata.imageDefs.map { metadata.getStringFromIndex(it.nameIndex) }, decompiler.typeEntries, strings)
            index.save(File(outputDir, DumpIndex.FILE_NAME))
            if (config.generateOffsetsHeader) {
                File(outputDir, "offsets.h").writeText(CodeGen.offsetsHeader(index, index.types))
            }
        }
        if (config.copyScripts) {
            copyScripts(outputDir)
        }
        val files = outputDir.listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: emptyList()
        return DumpResult(
            outputDir = outputDir,
            files = files,
            metadataVersion = metadata.version,
            il2cppVersion = il2Cpp.version,
            formatName = il2Cpp.formatName,
            arch = arch,
            imageCount = metadata.imageDefs.size,
            typeCount = metadata.typeDefs.size,
            methodCount = metadata.methodDefs.size,
            stringLiteralCount = metadata.stringLiterals.size,
            isDumped = il2Cpp.isDumped,
            maybeProtected = (il2Cpp as? ElfBase)?.maybeProtected ?: false,
            codeRegistration = il2Cpp.codeRegistrationAddress,
            metadataRegistration = il2Cpp.metadataRegistrationAddress,
            elapsedMs = System.currentTimeMillis() - start,
            index = index,
        )
    }

    private fun copyScripts(outputDir: File) {
        for (name in SCRIPTS) {
            val stream = Dumper::class.java.getResourceAsStream("/scripts/$name") ?: continue
            val target = File(outputDir, name)
            target.parentFile?.mkdirs()
            stream.use { input -> target.outputStream().use { input.copyTo(it) } }
        }
    }

    companion object {
        internal val pendingLogger = ThreadLocal<DumpLogger?>()

        val SCRIPTS = listOf(
            "ida.py", "ida_py3.py", "ida_with_struct.py", "ida_with_struct_py3.py",
            "ghidra.py", "ghidra_wasm.py", "ghidra_with_struct.py", "il2cpp_header_to_ghidra.py",
            "il2cpp_header_to_binja.py", "hopper-py3.py",
            "Il2CppBinaryNinja/__init__.py", "Il2CppBinaryNinja/plugin.json",
        )

        /**
         * Maps a file. The il2cpp binary is mapped copy-on-write because relocations are applied in place;
         * the original file is never modified.
         */
        fun mapFile(file: File, writable: Boolean): ByteBuffer {
            if (file.length() > Int.MAX_VALUE) throw DumpException("File too large: ${file.name}")
            if (writable) {
                // Copy-on-write mapping needs a read/write channel; fall back to a heap copy for read-only files.
                if (file.canWrite()) {
                    try {
                        RandomAccessFile(file, "rw").use { raf ->
                            return raf.channel.map(FileChannel.MapMode.PRIVATE, 0, raf.channel.size()).order(ByteOrder.LITTLE_ENDIAN)
                        }
                    } catch (e: Exception) {
                        // fall through
                    }
                }
                return ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
            }
            RandomAccessFile(file, "r").use { raf ->
                return raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.channel.size()).order(ByteOrder.LITTLE_ENDIAN)
            }
        }

        fun readMagic(file: File): Long {
            RandomAccessFile(file, "r").use { raf ->
                if (raf.length() < 4) return 0
                val b = ByteArray(4)
                raf.readFully(b)
                return (b[0].toLong() and 0xFF) or ((b[1].toLong() and 0xFF) shl 8) or
                    ((b[2].toLong() and 0xFF) shl 16) or ((b[3].toLong() and 0xFF) shl 24)
            }
        }
    }
}
