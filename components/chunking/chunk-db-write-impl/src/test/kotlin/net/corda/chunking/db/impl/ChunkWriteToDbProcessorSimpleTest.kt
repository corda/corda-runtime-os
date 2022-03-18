package net.corda.chunking.db.impl

import net.corda.chunking.RequestId
import net.corda.chunking.db.impl.persistence.ChunkPersistence
import net.corda.data.chunking.Chunk
import net.corda.data.chunking.ChunkAckKey
import net.corda.data.chunking.ChunkAck
import net.corda.data.crypto.SecureHash
import net.corda.messaging.api.records.Record
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.nio.ByteBuffer
import java.util.UUID

class ChunkWriteToDbProcessorSimpleTest {
    companion object {
        private const val topic = "unused"
        private const val fileName = "unused.txt"
        val validator = { _: RequestId -> }
    }

    @Test
    fun `chunk processor onNext calls persist`() {
        val persistence = mock<ChunkPersistence>()

        val processor = ChunkWriteToDbProcessor(topic, persistence, validator)
        val requestId = UUID.randomUUID().toString()

        val chunk = Chunk(
            requestId,
            fileName,
            SecureHash("foo", ByteBuffer.wrap(ByteArray(12))),
            0,
            0,
            ByteBuffer.wrap("12345678".toByteArray())
        )
        processor.onNext(listOf(Record(topic, requestId, chunk)))

        verify(persistence).persistChunk(chunk)
    }

    @Test
    fun `chunk processor onNext captures exception`() {
        val exceptionMessage = "exception message"

        val persistence = mock<ChunkPersistence>().apply {
            `when`(persistChunk(any())).thenThrow(CordaRuntimeException(exceptionMessage))
        }
        val processor = ChunkWriteToDbProcessor(topic, persistence, validator)
        val requestId = UUID.randomUUID().toString()

        val chunk = Chunk(
            requestId,
            fileName,
            SecureHash("foo", ByteBuffer.wrap("12345678".toByteArray())),
            0,
            0,
            ByteBuffer.wrap("12345678".toByteArray())
        )

        val records = processor.onNext(listOf(Record(topic, requestId, chunk)))
        verify(persistence).persistChunk(chunk)

        assertThat(records.isNotEmpty()).isTrue
        val key = records.first().key as ChunkAckKey
        val chunkAck = records.first().value as ChunkAck

        assertThat(requestId).isEqualTo(key.requestId)
        assertThat(exceptionMessage).isEqualTo(chunkAck.exception.errorMessage)
        assertThat(CordaRuntimeException::class.java.name).isEqualTo(chunkAck.exception.errorType)
    }
}
