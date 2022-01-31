package net.corda.chunking.db.impl.tests

import net.corda.chunking.db.impl.AllChunksReceived
import net.corda.chunking.db.impl.ChunkDbQueries
import net.corda.chunking.db.impl.ChunkWriteToDbProcessor
import net.corda.data.chunking.Chunk
import net.corda.data.chunking.ChunkAck
import net.corda.messaging.api.publisher.Publisher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.CompletableFuture

internal class ChunkWriteToDbProcessorTest {
    /** Returns a mock [Publisher]. */
    private fun getPublisher() = mock<Publisher>().apply {
        whenever(publish(any())).thenReturn(listOf(CompletableFuture.completedFuture(Unit)))
    }

    private val queries = mock<ChunkDbQueries>().apply {
        whenever(checksumIsValid(any())).thenReturn(true)
        whenever(persist(any())).thenReturn(AllChunksReceived.YES)
    }

    private fun processRequest(processor: ChunkWriteToDbProcessor, req: Chunk): ChunkAck {
        val respFuture = CompletableFuture<ChunkAck>()
        processor.onNext(req, respFuture)
        return respFuture.get()
    }

    private fun randomString() = UUID.randomUUID().toString()

    @Test
    fun `processor calls through to persist method`() {
        val processor = ChunkWriteToDbProcessor(getPublisher(), queries)
        val chunk = Chunk(randomString(), randomString(), null, 0, 0, ByteBuffer.wrap("1234".toByteArray()))
        val ack = processRequest(processor, chunk)

        assertThat(ack.requestId).isEqualTo(chunk.requestId)
        assertThat(ack.success).isTrue

        verify(queries).persist(chunk)
    }

    @Test
    fun `processor calls through to checksum method`() {
        val processor = ChunkWriteToDbProcessor(getPublisher(), queries)
        val chunk = Chunk(randomString(), randomString(), null, 0, 0, ByteBuffer.wrap("1234".toByteArray()))
        val ack = processRequest(processor, chunk)

        assertThat(ack.requestId).isEqualTo(chunk.requestId)
        assertThat(ack.success).isTrue

        verify(queries).persist(chunk)
        verify(queries).checksumIsValid(chunk.requestId)
    }
}
