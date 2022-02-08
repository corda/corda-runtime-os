package net.corda.libs.cpiupload.impl

import net.corda.data.ExceptionEnvelope
import net.corda.data.chunking.ChunkAck
import net.corda.libs.cpiupload.CpiUploadManagerException
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.mock
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.UUID

class CpiUploadManagerImplTest {
    private lateinit var cpiUploadManagerImpl: CpiUploadManagerImpl

    companion object {
        private const val digestAlgorithm = "SHA-256"

        fun calculateChecksum(bytes: ByteArray): SecureHash {
            val md = MessageDigest.getInstance(digestAlgorithm)
            return SecureHash(digestAlgorithm, md.digest(bytes))
        }
    }

    @BeforeEach
    fun setUp() {
        cpiUploadManagerImpl = CpiUploadManagerImpl(mock(), mock())
    }

    @Test
    fun `on successfully uploading CPI to Kafka returns CPI's request Id and checksum`() {
        val cpiBytes = "dummyCPI".toByteArray()
        val expectedChecksum = calculateChecksum(cpiBytes)
        val checksum = cpiUploadManagerImpl.uploadCpi(ByteArrayInputStream(cpiBytes))
        assertDoesNotThrow { UUID.fromString(checksum.requestId) }
        assertEquals(expectedChecksum, checksum.checksum)
    }

    @Test
    fun `on erroneous CPI chunk ack without exception throws CpiUploadManagerException`() {
        var chunkAck: ChunkAck? = null
        cpiUploadManagerImpl.createDummyChunkAck = {
            ChunkAck(
                it.requestId,
                it.partNumber,
                false,
                null
            ).also {
                chunkAck = it
            }
        }

        val cpiBytes = "dummyCPI".toByteArray()
        val e: CpiUploadManagerException = assertThrows {
            cpiUploadManagerImpl.uploadCpi(ByteArrayInputStream(cpiBytes))
        }

        assertNull(e.cause)
        val chunkAckUniqueId = "{${chunkAck!!.requestId}, ${chunkAck!!.partNumber}}"
        assertEquals("Unsuccessful ACK for chunk: $chunkAckUniqueId.", e.message)
    }

    @Test
    fun `on erroneous CPI chunk ack with exception throws CpiUploadManagerException with exception included`() {
        var chunkAck: ChunkAck? = null
        cpiUploadManagerImpl.createDummyChunkAck = {
            ChunkAck(
                it.requestId,
                it.partNumber,
                false,
                ExceptionEnvelope(
                    "DummyException",
                    "dummy error message"
                )
            ).also {
                chunkAck = it
            }
        }

        val cpiBytes = "dummyCPI".toByteArray()
        val e: CpiUploadManagerException = assertThrows {
            cpiUploadManagerImpl.uploadCpi(ByteArrayInputStream(cpiBytes))
        }

        assertNull(e.cause)
        val chunkAckUniqueId = "{${chunkAck!!.requestId}, ${chunkAck!!.partNumber}}"
        assertEquals("Unsuccessful ACK for chunk: $chunkAckUniqueId. Exception was DummyException: dummy error message", e.message)
    }
}