package net.corda.messaging.chunking

import java.nio.ByteBuffer
import net.corda.chunking.ChunkBuilderService
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePairList
import net.corda.data.chunking.Chunk
import net.corda.data.crypto.SecureHash
import net.corda.messagebus.api.producer.CordaProducerRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ChunkSerializerServiceImplTest {

    private companion object {
        const val chunkSize = 1024 * 50L
        val someSmallBytes = ByteArray(1)
        val someLargeBytes = ByteArray(1024 * 120)
    }

    private lateinit var serializer: CordaAvroSerializer<Any>
    private lateinit var chunkBuilderService: ChunkBuilderService
    private lateinit var chunkSerializerService: ChunkSerializerServiceImpl
    private lateinit var platformDigestService: PlatformDigestService
    private lateinit var producerRecord: CordaProducerRecord<*, *>
    private val value: String = "somevalue"
    private val key: String = "somekey"

    @BeforeEach
    fun setup() {
        serializer = mock()
        chunkBuilderService = mock()
        producerRecord = mock()
        platformDigestService = mock()
        chunkSerializerService = ChunkSerializerServiceImpl(chunkSize, serializer, chunkBuilderService, platformDigestService)

        var partNumber = 0
        doAnswer {
            partNumber += 1
            Chunk.newBuilder()
                .setPartNumber(partNumber)
                .setRequestId("id")
                .setChecksum(null)
                .setFileName(null)
                .setOffset(partNumber.toLong())
                .setData(ByteBuffer.wrap("bytes".toByteArray()))
                .setProperties(KeyValuePairList()).build()
        }.whenever(chunkBuilderService).buildChunk(any(), any(), any(), any(), anyOrNull(), anyOrNull())
        doAnswer {
            partNumber += 1
            Chunk.newBuilder()
                .setPartNumber(partNumber)
                .setRequestId("id")
                .setChecksum(SecureHash())
                .setFileName(null)
                .setOffset(partNumber.toLong())
                .setData(ByteBuffer.wrap(ByteArray(0)))
                .setProperties(KeyValuePairList()).build()
        }.whenever(chunkBuilderService).buildFinalChunk(any(), any(), any(), any(), anyOrNull(), anyOrNull())

        whenever(platformDigestService.hash(any<ByteArray>(), any())).thenReturn(net.corda.v5.crypto.SecureHash("", someSmallBytes))
        whenever(producerRecord.topic).thenReturn("topic")
        whenever(producerRecord.key).thenReturn(key)
        whenever(producerRecord.value).thenReturn(value)
        whenever(serializer.serialize(key)).thenReturn(someSmallBytes)
        whenever(serializer.serialize(value)).thenReturn(someLargeBytes)
    }

    @Test
    fun `generateChunks from object fails serialization and returns empty list`() {
        whenever(serializer.serialize(value)).thenReturn(null)
        val result = chunkSerializerService.generateChunks(value)
        assertThat(result).isEmpty()
    }

    @Test
    fun `generateChunks from object success and returns 5 chunks`() {
        val result = chunkSerializerService.generateChunks(value)
        assertThat(result).isNotEmpty
        assertThat(result).size().isEqualTo(5)

        verify(chunkBuilderService, times(4)).buildChunk(any(), any(), any(), any(), anyOrNull(), anyOrNull())
        verify(chunkBuilderService, times(1)).buildFinalChunk(any(), any(), any(), any(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `generateChunks from corda producer record fails serialization and returns empty list`() {
        whenever(serializer.serialize(value)).thenReturn(null)
        val result = chunkSerializerService.generateChunkedRecords(producerRecord)
        assertThat(result).isEmpty()
    }

    @Test
    fun `generateChunks from corda producer record with null value`() {
        whenever(producerRecord.value).thenReturn(null)
        val result = chunkSerializerService.generateChunkedRecords(producerRecord)
        assertThat(result).isEmpty()
    }

    @Test
    fun `generateChunks from corda producer record success and returns 5 chunks`() {
        val result = chunkSerializerService.generateChunkedRecords(producerRecord)
        assertThat(result).isNotEmpty
        assertThat(result.size).isEqualTo(5)

        verify(chunkBuilderService, times(4)).buildChunk(any(), any(), any(), any(), anyOrNull(), anyOrNull())
        verify(chunkBuilderService, times(1)).buildFinalChunk(any(), any(), any(), any(), anyOrNull(), anyOrNull())
    }
}
