package net.corda.chunking.db.impl

import net.corda.chunking.RequestId
import net.corda.chunking.db.impl.persistence.ChunkPersistence
import net.corda.chunking.db.impl.persistence.StatusPublisher
import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.parseSecureHash
import net.corda.crypto.core.toAvro
import net.corda.data.chunking.Chunk
import net.corda.messaging.api.records.Record
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash
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
        val validator = { _: RequestId -> parseSecureHash("SHA-256:1234567890") }
    }

    @Test
    fun `chunk processor onNext calls persist`() {
        val persistence = mock<ChunkPersistence>()
        val statusPublisher = mock<StatusPublisher>()
        val processor = ChunkWriteToDbProcessor(statusPublisher, persistence, validator)
        val requestId = UUID.randomUUID().toString()

        val chunk = Chunk(
            requestId,
            fileName,
            SecureHashImpl("foo", ByteArray(12)).toAvro(),
            0,
            0,
            ByteBuffer.wrap("12345678".toByteArray()),
            null
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
        val publisher = mock<StatusPublisher>()
        val processor = ChunkWriteToDbProcessor(publisher, persistence, validator)
        val requestId = UUID.randomUUID().toString()

        val chunk = Chunk(
            requestId,
            fileName,
            SecureHashImpl("foo", ByteArray(12)).toAvro(),
            0,
            0,
            ByteBuffer.wrap("12345678".toByteArray()),
            null
        )

        processor.onNext(listOf(Record(topic, requestId, chunk)))
        verify(persistence).persistChunk(chunk)
    }
}
