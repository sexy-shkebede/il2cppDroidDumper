package dev.il2cpp.dumper.core.cli

import dev.il2cpp.dumper.core.Config
import dev.il2cpp.dumper.core.DumpException
import dev.il2cpp.dumper.core.DumpInteraction
import dev.il2cpp.dumper.core.DumpLogger
import dev.il2cpp.dumper.core.Dumper
import dev.il2cpp.dumper.core.il2cpp.Metadata
import java.io.File
import kotlin.system.exitProcess

/**
 * Desktop command line, compatible with the original:
 *   il2cppdumper <executable-file> <global-metadata> <output-directory> [--no-index] [--no-scripts]
 *       [--dump-addr=0x...] [--force-version=24.3] [--force-dump] [--no-redirect] [--offsets]
 *       [--code-reg=0x... --meta-reg=0x...]
 */
fun main(args: Array<String>) {
    if (args.isEmpty() || args[0] == "-h" || args[0] == "--help") {
        println("usage: il2cppdumper <executable-file> <global-metadata> <output-directory> [options]")
        return
    }
    var il2cppPath: File? = null
    var metadataPath: File? = null
    var outputDir: File? = null
    var config = Config(generateOffsetsHeader = false)
    var dumpAddr = 0L
    var codeReg = 0L
    var metaReg = 0L
    for (arg in args) {
        when {
            arg == "--no-index" -> config = config.copy(generateIndex = false)
            arg == "--no-scripts" -> config = config.copy(copyScripts = false)
            arg == "--offsets" -> config = config.copy(generateOffsetsHeader = true)
            arg == "--force-dump" -> config = config.copy(forceDump = true)
            arg == "--no-redirect" -> config = config.copy(noRedirectedPointer = true)
            arg.startsWith("--force-version=") -> config = config.copy(forceIl2CppVersion = true, forceVersion = arg.substringAfter('=').toDouble())
            arg.startsWith("--dump-addr=") -> dumpAddr = parseHex(arg.substringAfter('='))
            arg.startsWith("--code-reg=") -> codeReg = parseHex(arg.substringAfter('='))
            arg.startsWith("--meta-reg=") -> metaReg = parseHex(arg.substringAfter('='))
            File(arg).isFile -> if (Dumper.readMagic(File(arg)) == Metadata.SANITY) metadataPath = File(arg) else il2cppPath = File(arg)
            else -> outputDir = File(arg)
        }
    }
    if (il2cppPath == null) {
        println("ERROR: executable file not found")
        exitProcess(1)
    }
    if (metadataPath == null) {
        println("ERROR: Metadata file not found or encrypted.")
        exitProcess(1)
    }
    val interaction = object : DumpInteraction {
        override fun askDumpAddress(): Long = dumpAddr
        override fun askRegistrations(): Pair<Long, Long>? = if (codeReg != 0L && metaReg != 0L) codeReg to metaReg else null
    }
    try {
        val result = Dumper(config, DumpLogger.STDOUT, interaction = interaction)
            .dump(il2cppPath, metadataPath, outputDir ?: File("."))
        println("Finished in ${result.elapsedMs} ms (${result.typeCount} types, ${result.methodCount} methods)")
    } catch (e: DumpException) {
        println(e.message)
        e.cause?.let { println(it) }
        exitProcess(2)
    }
}

private fun parseHex(s: String): Long = java.lang.Long.parseUnsignedLong(s.removePrefix("0x").removePrefix("0X"), 16)
