package net.corda.v5.ledger.obsolete.contracts

import org.junit.jupiter.api.Test
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.jar.JarFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.fail

class AttachmentTest {

    @Test
    fun `openAsJAR does not leak file handle if attachment has corrupted manifest`() {
        var closeCalls = 0
        val inputStream = spy(ByteArrayOutputStream().apply {
            ZipOutputStream(this).use {
                with(it) {
                    putNextEntry(ZipEntry(JarFile.MANIFEST_NAME))
                    write(ByteArray(512)) // One byte above the limit.
                }
            }
        }.toByteArray().inputStream()).apply { doAnswer { closeCalls += 1 }.whenever(this).close() }
        val attachment = object : Attachment {
            override val id get() = throw UnsupportedOperationException()
            override fun open() = inputStream
            override fun extractFile(path: String, outputTo: OutputStream) = throw java.lang.UnsupportedOperationException()

            override val signerKeys get() = throw UnsupportedOperationException()
            override val size: Int = 512
        }
        try {
            attachment.openAsJAR()
            fail("Expected line too long.")
        } catch (e: IOException) {
            assertEquals("line too long", e.message)
        }
        assertEquals(1, closeCalls)
    }
}
