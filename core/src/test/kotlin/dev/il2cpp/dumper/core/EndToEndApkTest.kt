package dev.il2cpp.dumper.core

import dev.il2cpp.dumper.core.extra.ApkInspector
import dev.il2cpp.dumper.core.extra.SeekableSource
import dev.il2cpp.dumper.core.extra.ZipReader
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * End-to-end test of the exact path the Android app uses: scan a real APK, extract
 * libil2cpp.so + global-metadata.dat with the in-house ZIP reader (raw deflate), and dump.
 * Configured with -Dil2cpp.apk=<path to an APK/XAPK>. Skipped when not provided.
 */
class EndToEndApkTest {
    @Test
    fun scanExtractAndDump() {
        val path = System.getProperty("il2cpp.apk").orEmpty()
        assumeTrue("no apk configured", path.isNotEmpty())
        val apk = File(path)
        assumeTrue(apk.isFile)

        val work = Files.createTempDirectory("e2e").toFile()
        ZipReader(SeekableSource.of(apk)).use { zr ->
            val scan = ApkInspector.scan(zr)
            println("scan: il2cpp=${scan.isIl2Cpp} unity=${scan.unityVersion} libs=${scan.libs.map { it.abi }} meta=${scan.metadata.map { it.version }}")
            assertTrue("not an il2cpp apk", scan.isIl2Cpp)
            val lib = scan.bestLib(ApkInspector.ABI_ORDER)!!
            val meta = scan.bestMetadata()!!
            val binFile = File(work, "libil2cpp.so")
            val metaFile = File(work, "global-metadata.dat")
            ApkInspector.extract(zr, lib.path, binFile)
            ApkInspector.extract(zr, meta.path, metaFile)
            assertTrue(binFile.length() > 0 && metaFile.length() > 0)

            val out = File(work, "out").apply { mkdirs() }
            val result = Dumper(Config(copyScripts = false), DumpLogger.STDOUT).dump(binFile, metaFile, out)
            println("dumped ${result.typeCount} types, ${result.methodCount} methods, metadata v${result.metadataVersion}")
            assertTrue(result.typeCount > 0)
            assertTrue(File(out, "dump.cs").length() > 0)
            assertTrue(File(out, "il2cpp.h").length() > 0)
            assertTrue(result.index!!.types.isNotEmpty())
        }
        work.deleteRecursively()
    }
}
