package dev.il2cpp.dumper.core

import dev.il2cpp.dumper.core.extra.DumpDiff
import dev.il2cpp.dumper.core.extra.DumpIndex
import dev.il2cpp.dumper.core.extra.SearchEngine
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Byte-for-byte comparison with outputs of the original C# Il2CppDumper 6.7.46.
 *
 * Layout: -Dil2cpp.samples=<dir> where every sub directory contains `libil2cpp.so` (or *.so / GameAssembly.dll),
 * `global-metadata.dat` and a `reference/` folder with dump.cs, script.json, stringliteral.json, il2cpp.h.
 * An optional `dumpaddr.txt` holds the base address for memory-dumped binaries.
 * The test is skipped when no samples are configured.
 */
class ReferenceDumpTest {
    private fun sampleDirs(): List<File> {
        val root = System.getProperty("il2cpp.samples").orEmpty()
        if (root.isEmpty()) return emptyList()
        return File(root).listFiles()?.filter { File(it, "reference").isDirectory }?.sortedBy { it.name } ?: emptyList()
    }

    @Test
    fun outputsMatchOriginalTool() {
        val samples = sampleDirs()
        assumeTrue("no samples configured", samples.isNotEmpty())
        for (sample in samples) {
            val binary = sample.listFiles()!!.first { it.name.endsWith(".so") || it.name.endsWith(".dll") }
            val metadata = File(sample, "global-metadata.dat")
            val out = Files.createTempDirectory("dump-${sample.name}").toFile()
            val addr = File(sample, "dumpaddr.txt").takeIf { it.isFile }?.readText()?.trim()?.toLong(16) ?: 0L
            val interaction = object : DumpInteraction {
                override fun askDumpAddress() = addr
            }
            val result = Dumper(Config(copyScripts = false), DumpLogger.NONE, interaction = interaction).dump(binary, metadata, out)
            for (name in listOf("dump.cs", "script.json", "stringliteral.json", "il2cpp.h")) {
                val expected = File(sample, "reference/$name")
                if (!expected.isFile) continue
                assertArrayEquals("${sample.name}/$name", expected.readBytes(), File(out, name).readBytes())
            }
            // index round trip + search + self diff
            val index = DumpIndex.load(File(out, DumpIndex.FILE_NAME))
            assertEquals(result.index!!.types.size, index.types.size)
            assertEquals(result.index!!.methodCount, index.methodCount)
            val firstMethod = index.types.flatMap { t -> t.methods.map { t to it } }.first { it.second.hasBody }
            val hits = SearchEngine(index).search("0x" + java.lang.Long.toHexString(firstMethod.second.rva))
            assertTrue(hits.isNotEmpty())
            val diff = DumpDiff.compare(index, index)
            assertEquals(0, diff.methods.size)
            out.deleteRecursively()
            println("OK ${sample.name}: ${result.typeCount} types, ${result.methodCount} methods in ${result.elapsedMs} ms")
        }
    }
}
