package net.corda.libs.cpiupload.impl

import net.corda.chunking.ChunkWriterFactory
import net.corda.data.chunking.ChunkAck
import net.corda.data.chunking.ChunkAckKey
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.subscription.Subscription
import net.corda.schema.Schemas
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import java.io.ByteArrayInputStream
import java.util.concurrent.CompletableFuture

class CpiUploadManagerImplTest {
    private lateinit var cpiUploadManagerImpl: CpiUploadManagerImpl
    private val ackProcessor = UploadStatusProcessor()
    private val publisher = mock(Publisher::class.java)
    private val subscription: Subscription<ChunkAckKey, ChunkAck> = mock()

    companion object {
        const val DUMMY_FILE_NAME = "dummyFileName"
    }

    @BeforeEach
    @Suppress("UNCHECKED_CAST")
    fun setUp() {
        cpiUploadManagerImpl =
            CpiUploadManagerImpl(Schemas.VirtualNode.CPI_UPLOAD_TOPIC, publisher, subscription, ackProcessor)
    }

    @Test
    fun `upload manager returns CPI requestId on success`() {
        var chunkCount = 0
        `when`(publisher.publish(anyOrNull())).thenAnswer { invocation ->
            val chunks = invocation.arguments[0] as List<*>
            chunkCount = chunks.size
            chunks.mapIndexed { index, _ ->
                val last = (index + 1) == chunks.size
                CompletableFuture<ChunkAck>().also {
                    it.complete(ChunkAck(last, null))
                }
            }
        }
        val expectedNoZeroChunkCount = 3
        val expectedChunkCount = expectedNoZeroChunkCount + 1
        val cpiBytes = ByteArray(ChunkWriterFactory.SUGGESTED_CHUNK_SIZE * expectedNoZeroChunkCount)
        cpiUploadManagerImpl.uploadCpi(DUMMY_FILE_NAME, ByteArrayInputStream(cpiBytes))

        assertThat(chunkCount).isNotEqualTo(expectedChunkCount)
    }
}
