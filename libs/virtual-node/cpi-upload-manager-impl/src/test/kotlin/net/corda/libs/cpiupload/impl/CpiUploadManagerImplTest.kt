package net.corda.libs.cpiupload.impl

import net.corda.chunking.RequestId
import net.corda.data.chunking.Chunk
import net.corda.data.chunking.UploadFileStatus
import net.corda.data.chunking.UploadStatus
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.Subscription
import net.corda.schema.Schemas
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.concurrent.CompletableFuture

class CpiUploadManagerImplTest {
    private lateinit var cpiUploadManagerImpl: CpiUploadManagerImpl
    private val ackProcessor = UploadStatusProcessor()
    private val publisher = mock(Publisher::class.java)
    private val subscription : Subscription<RequestId, UploadStatus> = mock()

    companion object {
        const val DUMMY_FILE_NAME = "dummyFileName"
    }

    @BeforeEach
    @Suppress("UNCHECKED_CAST")
    fun setUp() {
        cpiUploadManagerImpl = CpiUploadManagerImpl(Schemas.VirtualNode.CPI_UPLOAD_TOPIC, publisher, subscription, ackProcessor)
    }

    @Test
    fun `on successfully uploading CPI to Kafka returns CPI's request Id`() {
        `when`(publisher.publish(anyOrNull())).thenAnswer { invocation ->
            val chunks = invocation.arguments[0] as List<*>

            chunks.map { it ->
                val record = it as Record<*, *>
                val chunk = record.value as Chunk
                CompletableFuture<UploadStatus>().also {
                    it.complete(
                        UploadStatus(
                            chunk.requestId,
                            UploadFileStatus.UPLOAD_IN_PROGRESS,
                            null
                        )
                    )
                }
            }
        }

        val cpiBytes = "dummyCPI".toByteArray()
        val requestId = cpiUploadManagerImpl.uploadCpi(DUMMY_FILE_NAME, ByteArrayInputStream(cpiBytes))
        assertDoesNotThrow { UUID.fromString(requestId) }
    }
}
