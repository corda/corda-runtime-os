package net.corda.chunking.db.impl

import net.corda.data.chunking.Chunk
import net.corda.data.chunking.UploadStatus
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

    }

    @Test
    fun `persist is called onNext`() {
        val queries = mock<ChunkDbQueries>()
        val processor = ChunkWriteToDbProcessor(topic, queries)
        val requestId = UUID.randomUUID().toString()

        val chunk = Chunk(
            requestId,
            fileName,
            SecureHash("foo", ByteBuffer.wrap("12345678".toByteArray())),
            0,
            0,
            ByteBuffer.wrap("12345678".toByteArray())
        )
        processor.onNext(listOf(Record(topic, requestId, chunk)))

        verify(queries).persist(chunk)
    }

    @Test
    fun `exception is captured in onNext`() {
        val exceptionMessage = "exception message"

        val queries = mock<ChunkDbQueries>().apply {
            `when`(persist(any())).thenThrow(CordaRuntimeException(exceptionMessage))
        }
        val processor = ChunkWriteToDbProcessor(topic, queries)
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
        verify(queries).persist(chunk)

        assertThat(records.isNotEmpty()).isTrue
        assertThat(requestId).isEqualTo(records.first().key)

        val uploadStatus = records.first().value as UploadStatus
        assertThat(exceptionMessage).isEqualTo(uploadStatus.exception.errorMessage)
        assertThat(CordaRuntimeException::class.java.name).isEqualTo(uploadStatus.exception.errorType)
    }
}
