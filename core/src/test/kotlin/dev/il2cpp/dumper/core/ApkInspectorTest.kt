package dev.il2cpp.dumper.core

import dev.il2cpp.dumper.core.extra.ApkInspector
import dev.il2cpp.dumper.core.extra.SeekableSource
import dev.il2cpp.dumper.core.extra.ZipReader
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ApkInspectorTest {
    private val metadataHead = byteArrayOf(0xAF.toByte(), 0x1B, 0xB1.toByte(), 0xFA.toByte(), 29, 0, 0, 0) + ByteArray(4096) { (it % 7).toByte() }

    private fun zip(entries: List<Triple<String, ByteArray, Boolean>>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            for ((name, data, stored) in entries) {
                val e = ZipEntry(name)
                if (stored) {
                    e.method = ZipEntry.STORED
                    e.size = data.size.toLong()
                    e.compressedSize = data.size.toLong()
                    e.crc = CRC32().apply { update(data) }.value
                }
                z.putNextEntry(e)
                z.write(data)
                z.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    @Test
    fun findsFilesInsideSplitApkBundle() {
        val lib = ByteArray(10000) { (it * 31).toByte() }
        val base = zip(listOf(
            Triple("AndroidManifest.xml", ByteArray(10), false),
            Triple("assets/bin/Data/Managed/Metadata/global-metadata.dat", metadataHead, false),
            Triple("assets/bin/Data/globalgamemanagers", "xxxx\u0000\u00002022.3.21f1\u0000".toByteArray(), false),
        ))
        val split = zip(listOf(Triple("lib/arm64-v8a/libil2cpp.so", lib, true)))
        val xapk = zip(listOf(
            Triple("manifest.json", "{}".toByteArray(), false),
            Triple("com.example.game.apk", base, true),
            Triple("config.arm64_v8a.apk", split, true),
        ))
        val file = Files.createTempFile("bundle", ".xapk").toFile()
        file.writeBytes(xapk)
        ZipReader(SeekableSource.of(file)).use { zr ->
            val r = ApkInspector.scan(zr)
            assertEquals("2022.3.21f1", r.unityVersion)
            assertEquals(1, r.libs.size)
            assertEquals("arm64-v8a", r.libs[0].abi)
            assertEquals(29, r.bestMetadata()!!.version)
            val out = File(file.parentFile, "extracted.so")
            ApkInspector.extract(zr, r.libs[0].path, out)
            assertArrayEquals(lib, out.readBytes())
            val outMeta = File(file.parentFile, "extracted.dat")
            ApkInspector.extract(zr, r.bestMetadata()!!.path, outMeta)
            assertArrayEquals(metadataHead, outMeta.readBytes())
            out.delete(); outMeta.delete()
        }
        file.delete()
    }

    @Test
    fun findsRenamedMetadataByMagic() {
        val apk = zip(listOf(
            Triple("lib/armeabi-v7a/libil2cpp.so", ByteArray(100), false),
            Triple("assets/bin/Data/Managed/Resources/blob.bytes", metadataHead, false),
        ))
        val file = Files.createTempFile("renamed", ".apk").toFile()
        file.writeBytes(apk)
        val r = ApkInspector.scan(file)
        val m = r.bestMetadata()!!
        assertTrue(m.renamed)
        assertEquals("assets/bin/Data/Managed/Resources/blob.bytes", m.path.name)
        file.delete()
    }
}
