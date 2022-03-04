package net.corda.chunking.db.impl.tests

import net.corda.chunking.RequestId
import net.corda.chunking.db.impl.AllChunksReceived
import net.corda.chunking.db.impl.DatabaseQueries
import net.corda.chunking.db.impl.ChunkWriteToDbProcessor
import net.corda.data.chunking.Chunk
import net.corda.data.chunking.UploadFileStatus
import net.corda.data.chunking.UploadStatus
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.util.UUID

internal class ChunkWriteToDbProcessorTest {
    companion object {
        const val topic = Schemas.VirtualNode.CPI_UPLOAD_TOPIC
    }

    private lateinit var processor: ChunkWriteToDbProcessor

    private val queries = mock<DatabaseQueries>().apply {
        whenever(checksumIsValid(any())).thenReturn(true)
        whenever(persistChunk(any())).thenReturn(AllChunksReceived.YES)
    }

    private fun processRequest(processor: ChunkWriteToDbProcessor, req: Chunk): UploadStatus {
        val records = processor.onNext(listOf(Record(topic, req.requestId, req)))
        return records.first().value as UploadStatus
    }

    private fun randomString() = UUID.randomUUID().toString()

    @BeforeEach
    private fun beforeEach() {
        val validator = { _ : RequestId ->  UploadFileStatus.OK }
        processor = ChunkWriteToDbProcessor(topic, queries, validator)
    }

    @Test
    fun `processor calls through to persist method`() {
        val chunk = Chunk(randomString(), randomString(), null, 0, 0, ByteBuffer.wrap("1234".toByteArray()))
        val ack = processRequest(processor, chunk)

        assertThat(ack.requestId).isEqualTo(chunk.requestId)

        verify(queries).persistChunk(chunk)
    }

    @Test
    fun `processor calls through to checksum method`() {
        val validator = { _ : RequestId ->  UploadFileStatus.OK }
        val processor = ChunkWriteToDbProcessor(topic, queries, validator)
        val chunk = Chunk(randomString(), randomString(), null, 0, 0, ByteBuffer.wrap("1234".toByteArray()))
        val ack = processRequest(processor, chunk)

        assertThat(ack.requestId).isEqualTo(chunk.requestId)

        verify(queries).persistChunk(chunk)
        verify(queries).checksumIsValid(chunk.requestId)
    }
}
