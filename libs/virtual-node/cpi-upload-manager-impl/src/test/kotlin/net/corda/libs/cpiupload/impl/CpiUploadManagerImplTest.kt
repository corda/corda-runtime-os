package net.corda.libs.cpiupload.impl

import net.corda.crypto.core.toAvro
import net.corda.data.chunking.UploadStatus
import net.corda.data.chunking.UploadStatusKey
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.Subscription
import net.corda.schema.Schemas
import net.corda.v5.crypto.SecureHash
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
    private val subscription: Subscription<UploadStatusKey, UploadStatus> = mock()
    private val maxAllowedMessageSize = 97280

    companion object {
        const val DUMMY_FILE_NAME = "dummyFileName"
    }

    @BeforeEach
    @Suppress("UNCHECKED_CAST")
    fun setUp() {
        cpiUploadManagerImpl =
            CpiUploadManagerImpl(
                Schemas.VirtualNode.CPI_UPLOAD_TOPIC,
                publisher,
                subscription,
                ackProcessor,
                maxAllowedMessageSize)
    }

    @Test
    fun `upload manager returns CPI requestId on upload request`() {
        var chunkCount = 0
        val checksum = SecureHash("SHA-256", ByteArray(12))
        `when`(publisher.publish(anyOrNull())).thenAnswer { invocation ->
            val chunks = invocation.arguments[0] as List<*>
            chunkCount = chunks.size
            chunks.mapIndexed { index, _ ->
                val last = (index + 1) == chunks.size
                CompletableFuture<UploadStatus>().also {
                    it.complete(UploadStatus(last, "", checksum.toAvro(), null))
                }
            }
        }
        val expectedNoZeroChunkCount = 3
        val expectedChunkCount = expectedNoZeroChunkCount + 1
        val cpiBytes = ByteArray(maxAllowedMessageSize * expectedNoZeroChunkCount)
        val request = cpiUploadManagerImpl.uploadCpi(DUMMY_FILE_NAME, ByteArrayInputStream(cpiBytes))

        assertThat(chunkCount).isNotEqualTo(expectedChunkCount)
        assertThat(request.requestId.isNotEmpty()).isTrue
    }

    @Test
    fun `upload manager returns success on status request`() {
        val checksum = SecureHash("SHA-256", ByteArray(12))
        `when`(publisher.publish(anyOrNull())).thenAnswer { invocation ->
            val chunks = invocation.arguments[0] as List<*>
            chunks.mapIndexed { index, _ ->
                val last = (index + 1) == chunks.size
                CompletableFuture<UploadStatus>().also {
                    it.complete(UploadStatus(last, "", checksum.toAvro(), null))
                }
            }
        }

        val expectedNoZeroChunkCount = 3
        val cpiBytes = ByteArray(maxAllowedMessageSize * expectedNoZeroChunkCount)
        val request = cpiUploadManagerImpl.uploadCpi(DUMMY_FILE_NAME, ByteArrayInputStream(cpiBytes))

        // send a single message with 'last' = true.  sequence number doesn't matter here since it's a test.
        ackProcessor.onNext(
            Record(
                "",
                UploadStatusKey(request.requestId, 1),
                UploadStatus(true, "", request.secureHash.toAvro(), null)
            ), null, emptyMap()
        )

        val status = cpiUploadManagerImpl.status(request.requestId)

        assertThat(status).isNotNull
        assertThat(status!!.complete).isTrue
    }
}
