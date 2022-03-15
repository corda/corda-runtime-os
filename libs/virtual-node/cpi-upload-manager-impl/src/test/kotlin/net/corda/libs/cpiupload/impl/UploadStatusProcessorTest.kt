package net.corda.libs.cpiupload.impl

import net.corda.data.ExceptionEnvelope
import net.corda.data.chunking.ChunkKey
import net.corda.data.chunking.ChunkReceived
import net.corda.data.chunking.UploadStatus
import net.corda.messaging.api.records.Record
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

internal class UploadStatusProcessorTest {

    private lateinit var processor: UploadStatusProcessor
    private val topic = "don't care"

    @BeforeEach
    private fun beforeEach() {
        processor = UploadStatusProcessor()
    }

    private fun randomKey() = IncrementingChunkKey()
    private fun status(key: IncrementingChunkKey) = processor.status(key.requestId).first
    private fun exceptionEnvelope(key: IncrementingChunkKey) = processor.status(key.requestId).second

    /** Creates a new key with a request id, and [next()] returns the next key and partnumber, starting at 0 */
    class IncrementingChunkKey {
        val requestId = UUID.randomUUID().toString()
        private var partNumber = 0
        fun next(): ChunkKey {
            val key = ChunkKey(requestId, partNumber)
            ++partNumber
            return key
        }
    }

    @Test
    fun `processor returns in progress status for in progress request in snapshot`() {
        val chunkKey = randomKey()

        val key0 = chunkKey.next()
        processor.onSnapshot(
            mapOf(
                key0 to ChunkReceived(chunkKey.requestId, UploadStatus.IN_PROGRESS, key0.partNumber, false, null)
            )
        )

        assertThat(status(chunkKey)).isEqualTo(UploadStatus.IN_PROGRESS)
    }

    @Test
    fun `processor returns failed status for unknown request not in snapshot`() {
        val chunkKey = randomKey()
        val unexpectedChunkKey = randomKey()

        val key0 = chunkKey.next()
        processor.onSnapshot(
            mapOf(
                key0 to ChunkReceived(chunkKey.requestId, UploadStatus.IN_PROGRESS, key0.partNumber, false, null)
            )
        )

        assertThat(status(unexpectedChunkKey)).isEqualTo(UploadStatus.FAILED)
    }

    @Test
    fun `processor returns status OK on next chunk received in order`() {
        val chunkKey = randomKey()
        val key0 = chunkKey.next()
        val currentData = mapOf(
            key0 to ChunkReceived(chunkKey.requestId, UploadStatus.IN_PROGRESS, key0.partNumber, false, null)
        )
        processor.onSnapshot(currentData)

        val key1 = chunkKey.next()
        val nextChunkReceived = ChunkReceived(chunkKey.requestId, UploadStatus.OK, key1.partNumber, true, null)
        processor.onNext(Record(topic, key1, nextChunkReceived), nextChunkReceived, emptyMap())

        assertThat(status(chunkKey)).isEqualTo(UploadStatus.OK)
    }

    @Test
    fun `processor returns status OK on next chunk received out of order`() {
        val chunkKey = randomKey()
        val key0 = chunkKey.next()
        val key1 = chunkKey.next()
        val key2 = chunkKey.next()

        processor.onSnapshot(
            mapOf(
                key0 to ChunkReceived(chunkKey.requestId, UploadStatus.IN_PROGRESS, key0.partNumber, false, null)
            )
        )

        val nextChunkReceived = ChunkReceived(chunkKey.requestId, UploadStatus.OK, key2.partNumber, true, null)
        processor.onNext(Record(topic, key2, nextChunkReceived), nextChunkReceived, emptyMap())
        assertThat(status(chunkKey)).isEqualTo(UploadStatus.IN_PROGRESS)

        val finalChunkReceived =
            ChunkReceived(chunkKey.requestId, UploadStatus.IN_PROGRESS, key1.partNumber, false, null)
        processor.onNext(Record(topic, key1, finalChunkReceived), finalChunkReceived, emptyMap())
        assertThat(status(chunkKey)).isEqualTo(UploadStatus.OK)
    }

    @Test
    fun `processor returns failed status and exception in failed chunk received`() {
        val chunkKey = randomKey()
        val key0 = chunkKey.next()
        processor.onSnapshot(
            mapOf(
                key0 to ChunkReceived(chunkKey.requestId, UploadStatus.IN_PROGRESS, key0.partNumber, false, null)
            )
        )

        val key1 = chunkKey.next()
        // Still in progress with missing chunk
        val statusOfLastChunk =
            ChunkReceived(chunkKey.requestId, UploadStatus.IN_PROGRESS, key1.partNumber, false, null)
        processor.onNext(Record(topic, key1, statusOfLastChunk), statusOfLastChunk, emptyMap())

        val key2 = chunkKey.next()
        val statusOfNextChunk = ChunkReceived(
            chunkKey.requestId,
            UploadStatus.FAILED,
            key2.partNumber,
            false,
            ExceptionEnvelope("SomeException", "some message")
        )
        processor.onNext(Record(topic, key2, statusOfNextChunk), statusOfNextChunk, emptyMap())

        assertThat(status(chunkKey)).isEqualTo(UploadStatus.FAILED)
        assertThat(exceptionEnvelope(chunkKey)).isNotNull
    }

    @Test
    fun `processor removes chunks received if necessary`() {
        // Not sure that we'll ever get this situation, but tests the code anyway.

        val chunkKey = randomKey()
        processor.onSnapshot(emptyMap())

        val key0 = chunkKey.next()
        val firstChunk = ChunkReceived(chunkKey.requestId, UploadStatus.IN_PROGRESS, key0.partNumber, false, null)
        processor.onNext(Record(topic, key0, firstChunk), firstChunk, emptyMap())

        val key1 = chunkKey.next()
        val nextChunkReceived =
            ChunkReceived(chunkKey.requestId, UploadStatus.IN_PROGRESS, key1.partNumber, false, null)
        processor.onNext(Record(topic, key1, nextChunkReceived), nextChunkReceived, emptyMap())

        assertThat(status(chunkKey)).isEqualTo(UploadStatus.IN_PROGRESS)

        processor.onNext(Record(topic, key1, null), nextChunkReceived, emptyMap())
        assertThat(status(chunkKey)).isEqualTo(UploadStatus.FAILED)
    }

    @Test
    fun `processor handles two different requests`() {
        val chunkKeyA = randomKey()
        val chunkKeyB = randomKey()
        processor.onSnapshot(emptyMap())

        val keyA0 = chunkKeyA.next()
        val keyA1 = chunkKeyA.next()

        val keyB0 = chunkKeyB.next()
        val keyB1 = chunkKeyB.next()

        processor.onNext(
            Record(
                topic,
                keyA0,
                ChunkReceived(keyA0.requestId, UploadStatus.IN_PROGRESS, keyA0.partNumber, false, null)
            ), null, emptyMap()
        )
        processor.onNext(
            Record(
                topic,
                keyB0,
                ChunkReceived(keyB0.requestId, UploadStatus.IN_PROGRESS, keyB0.partNumber, false, null)
            ), null, emptyMap()
        )

        assertThat(status(chunkKeyA)).isEqualTo(UploadStatus.IN_PROGRESS)
        assertThat(status(chunkKeyB)).isEqualTo(UploadStatus.IN_PROGRESS)

        processor.onNext(
            Record(
                topic,
                keyA1,
                ChunkReceived(keyA1.requestId, UploadStatus.OK, keyA1.partNumber, true, null)
            ), null, emptyMap()
        )
        processor.onNext(
            Record(
                topic,
                keyB1,
                ChunkReceived(keyB1.requestId, UploadStatus.OK, keyB1.partNumber, true, null)
            ), null, emptyMap()
        )

        assertThat(status(chunkKeyA)).isEqualTo(UploadStatus.OK)
        assertThat(status(chunkKeyB)).isEqualTo(UploadStatus.OK)
    }
}
