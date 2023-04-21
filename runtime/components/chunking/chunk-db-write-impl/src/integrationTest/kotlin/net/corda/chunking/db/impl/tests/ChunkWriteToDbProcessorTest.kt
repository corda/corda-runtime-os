package net.corda.chunking.db.impl.tests

import net.corda.chunking.RequestId
import net.corda.chunking.db.impl.AllChunksReceived
import net.corda.chunking.db.impl.ChunkWriteToDbProcessor
import net.corda.chunking.db.impl.persistence.StatusPublisher
import net.corda.chunking.db.impl.persistence.database.DatabaseChunkPersistence
import net.corda.crypto.core.SecureHashImpl
import net.corda.data.chunking.Chunk
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
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
    private lateinit var publisher: StatusPublisher

    private val persistence = mock<DatabaseChunkPersistence>().apply {
        whenever(checksumIsValid(any())).thenReturn(true)
        whenever(persistChunk(any())).thenReturn(AllChunksReceived.YES)
    }

    private fun processRequest(processor: ChunkWriteToDbProcessor, req: Chunk) {
        processor.onNext(listOf(Record(topic, req.requestId, req)))
    }

    private fun randomString() = UUID.randomUUID().toString()

    @BeforeEach
    fun beforeEach() {
        val checksum = SecureHashImpl("SHA-256", ByteArray(16))
        val validator = { _: RequestId -> checksum }
        publisher = mock<StatusPublisher>()
        processor = ChunkWriteToDbProcessor(publisher, persistence, validator)
    }

    @Test
    fun `processor calls through to persist method`() {
        val chunk = Chunk(randomString(), randomString(), null, 0, 0, ByteBuffer.wrap(ByteArray(256)), null)
        processRequest(processor, chunk)
        verify(persistence).persistChunk(chunk)
    }

    @Test
    fun `processor calls through to publisher`() {
        processRequest(processor, Chunk(randomString(), randomString(), null, 0, 0, ByteBuffer.wrap(ByteArray(256)), null))
        verify(persistence).persistChunk(any())
        verify(publisher).initialStatus(any())
    }
}
