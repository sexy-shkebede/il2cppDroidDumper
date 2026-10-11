package dev.il2cpp.dumper.core

import dev.il2cpp.dumper.core.extra.MetadataDoctor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Exercises the Metadata Doctor. Needs a real metadata sample via -Dil2cpp.samples=<dir>
 * (reuses the ReferenceDumpTest layout: <dir>/<name>/global-metadata.dat). Skipped otherwise.
 */
class MetadataDoctorTest {
    private fun sampleMetadata(): ByteArray? {
        val root = System.getProperty("il2cpp.samples").orEmpty()
        if (root.isEmpty()) return null
        val f = File(root).listFiles()?.asSequence()
            ?.mapNotNull { File(it, "global-metadata.dat").takeIf { m -> m.isFile } }
            ?.firstOrNull()
        return f?.readBytes()
    }

    @Test
    fun validFileReportsValid() {
        val meta = sampleMetadata(); assumeTrue(meta != null)
        val d = MetadataDoctor.diagnose(meta!!)
        assertEquals(MetadataDoctor.Verdict.VALID, d.verdict)
        assertTrue(d.types > 0 && d.methods > 0)
        assertNull(MetadataDoctor.attemptRecover(meta))
    }

    @Test
    fun singleByteXorIsRecovered() {
        val meta = sampleMetadata(); assumeTrue(meta != null)
        val key = 0x5A
        val enc = ByteArray(meta!!.size) { (meta[it].toInt() xor key).toByte() }
        val d = MetadataDoctor.diagnose(enc)
        assertTrue("should be recoverable", d.recoverable)
        val rec = MetadataDoctor.attemptRecover(enc)
        assertNotNull(rec)
        assertTrue(rec!!.bytes.contentEquals(meta))
        assertTrue(rec.method.contains("single-byte"))
    }

    @Test
    fun repeatingKeyXorIsRecovered() {
        val meta = sampleMetadata(); assumeTrue(meta != null)
        val key = byteArrayOf(0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77.toByte())
        val enc = ByteArray(meta!!.size) { (meta[it].toInt() xor key[it % key.size].toInt()).toByte() }
        val rec = MetadataDoctor.attemptRecover(enc)
        assertNotNull("repeating-key XOR should be recovered", rec)
        assertTrue(rec!!.bytes.contentEquals(meta))
    }

    @Test
    fun wipedMagicIsRestored() {
        val meta = sampleMetadata(); assumeTrue(meta != null)
        val broken = meta!!.copyOf()
        broken[0] = 0; broken[1] = 0; broken[2] = 0; broken[3] = 0
        val rec = MetadataDoctor.attemptRecover(broken)
        assertNotNull(rec)
        assertTrue(rec!!.bytes.contentEquals(meta))
    }

    @Test
    fun encryptedFileIsClassifiedNotRecovered() {
        val meta = sampleMetadata(); assumeTrue(meta != null)
        // Deterministic PRNG stand-in for real encryption: keystream from a LCG, no plaintext leakage.
        var s = 0x12345678
        val enc = ByteArray(meta!!.size) {
            s = (s * 1103515245 + 12345) and 0x7fffffff
            (meta[it].toInt() xor (s ushr 16)).toByte()
        }
        val d = MetadataDoctor.diagnose(enc)
        assertEquals(MetadataDoctor.Verdict.FULLY_ENCRYPTED, d.verdict)
        assertTrue(d.entropy > 7.5)
        assertNull(MetadataDoctor.attemptRecover(enc))
    }
}
