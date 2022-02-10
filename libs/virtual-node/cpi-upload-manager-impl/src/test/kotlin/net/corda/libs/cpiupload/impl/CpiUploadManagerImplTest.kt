package net.corda.libs.cpiupload.impl

import net.corda.data.ExceptionEnvelope
import net.corda.data.chunking.Chunk
import net.corda.data.chunking.ChunkAck
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.cpiupload.CpiUploadManagerException
import net.corda.messaging.api.publisher.RPCSender
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.kotlin.anyOrNull
import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.concurrent.CompletableFuture

class CpiUploadManagerImplTest {
    private lateinit var cpiUploadManagerImpl: CpiUploadManagerImpl
    private lateinit var rpcSender: RPCSender<Chunk, ChunkAck>

    @BeforeEach
    @Suppress("UNCHECKED_CAST")
    fun setUp() {
        rpcSender = mock(RPCSender::class.java) as RPCSender<Chunk, ChunkAck>
        cpiUploadManagerImpl = CpiUploadManagerImpl(mock(SmartConfig::class.java), rpcSender)
    }

    @Test
    fun `on successfully uploading CPI to Kafka returns CPI's request Id`() {
        `when`(rpcSender.isRunning).thenReturn(true)
        `when`(rpcSender.sendRequest(anyOrNull())).thenAnswer { invocation ->
            val chunk = invocation.arguments[0] as Chunk
            CompletableFuture<ChunkAck>().also {
                it.complete(
                    ChunkAck(
                        chunk.requestId,
                        chunk.partNumber,
                        true,
                        null
                    )
                )
            }
        }

        val cpiBytes = "dummyCPI".toByteArray()
        val requestId = cpiUploadManagerImpl.uploadCpi(ByteArrayInputStream(cpiBytes))
        assertDoesNotThrow { UUID.fromString(requestId) }
    }

    @Test
    fun `on erroneous CPI chunk ack without exception throws CpiUploadManagerException`() {
        var chunkId: CpiUploadManagerImpl.ChunkId? = null
        var firstChunk = true

        `when`(rpcSender.isRunning).thenReturn(true)
        `when`(rpcSender.sendRequest(anyOrNull())).thenAnswer { invocation ->
            val chunk = invocation.arguments[0] as Chunk
            CompletableFuture<ChunkAck>().also {
                it.complete(
                    ChunkAck(
                        chunk.requestId,
                        chunk.partNumber,
                        !firstChunk,
                        null
                    ).also {
                        if (firstChunk) {
                            // just grab the first chunk's id
                            chunkId = CpiUploadManagerImpl.ChunkId(chunk.requestId, chunk.partNumber)
                            firstChunk = false
                        }
                    }
                )
            }
        }

        val cpiBytes = "dummyCPI".toByteArray()
        val e = assertThrows<CpiUploadManagerException> {
            cpiUploadManagerImpl.uploadCpi(ByteArrayInputStream(cpiBytes))
        }

        assertNull(e.cause)
        assertEquals("Unsuccessful ACK for chunk: $chunkId.", e.message)
    }

    @Test
    fun `on erroneous CPI chunk ack with exception throws CpiUploadManagerException with exception included`() {
        var chunkId: CpiUploadManagerImpl.ChunkId? = null
        var firstChunk = true

        `when`(rpcSender.isRunning).thenReturn(true)
        `when`(rpcSender.sendRequest(anyOrNull())).thenAnswer { invocation ->
            val chunk = invocation.arguments[0] as Chunk
            CompletableFuture<ChunkAck>().also {
                it.complete(
                    ChunkAck(
                        chunk.requestId,
                        chunk.partNumber,
                        !firstChunk,
                        ExceptionEnvelope(
                            "DummyException",
                            "dummy error message"
                        ).also {
                            if (firstChunk) {
                                // just grab the first chunk's id
                                chunkId = CpiUploadManagerImpl.ChunkId(chunk.requestId, chunk.partNumber)
                                firstChunk = false
                            }
                        }
                    )
                )
            }
        }

        val cpiBytes = "dummyCPI".toByteArray()
        val e = assertThrows<CpiUploadManagerException> {
            cpiUploadManagerImpl.uploadCpi(ByteArrayInputStream(cpiBytes))
        }

        assertNull(e.cause)
        assertEquals("Unsuccessful ACK for chunk: $chunkId. Error was DummyException: dummy error message", e.message)
    }

    @Test
    fun `if RPCSender stopped running aborts CPI uploading by throwing CpiUploadManagerException`() {
        `when`(rpcSender.isRunning).thenReturn(false)

        val cpiBytes = "dummyCPI".toByteArray()
        val e = assertThrows<CpiUploadManagerException> {
            cpiUploadManagerImpl.uploadCpi(ByteArrayInputStream(cpiBytes))
        }
        assertEquals("RPCSender has stopped running. Aborting CPI uploading.", e.message)
    }
}