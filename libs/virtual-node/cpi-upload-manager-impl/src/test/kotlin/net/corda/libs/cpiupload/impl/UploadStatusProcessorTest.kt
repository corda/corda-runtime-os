package net.corda.libs.cpiupload.impl

import net.corda.data.ExceptionEnvelope
import net.corda.data.chunking.ChunkAck
import net.corda.data.chunking.ChunkAckKey
import net.corda.libs.cpiupload.CpiUploadManager
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

    private fun incrementingChunkKey() = IncrementingChunkKey()
    private fun status(key: IncrementingChunkKey) = processor.status(key.requestId).first
    private fun exceptionEnvelope(key: IncrementingChunkKey) = processor.status(key.requestId).second

    /** Creates a new key with a request id, and [next()] returns the next key and partnumber, starting at 0 */
    class IncrementingChunkKey {
        val requestId = UUID.randomUUID().toString()
        private var partNumber = 0
        fun next(): ChunkAckKey {
            val key = ChunkAckKey(requestId, partNumber)
            ++partNumber
            return key
        }
    }

    @Test
    fun `processor returns in progress status for in progress request in snapshot`() {
        val chunkAckKey = incrementingChunkKey()

        processor.onSnapshot(mapOf(chunkAckKey.next() to ChunkAck(false, null)))

        assertThat(status(chunkAckKey)).isEqualTo(CpiUploadManager.UploadStatus.IN_PROGRESS)
    }

    @Test
    fun `processor returns failed status for unknown request not in snapshot`() {
        val chunkAckKey = incrementingChunkKey()
        val unexpectedChunkKey = incrementingChunkKey()

        processor.onSnapshot(mapOf(chunkAckKey.next() to ChunkAck(false, null)))

        assertThat(status(unexpectedChunkKey)).isEqualTo(CpiUploadManager.UploadStatus.FAILED)
    }

    @Test
    fun `processor returns status OK on next chunk received in order`() {
        val chunkAckKey = incrementingChunkKey()
        processor.onSnapshot(mapOf(chunkAckKey.next() to ChunkAck(false, null)))

        val nextChunkReceived = ChunkAck(true, null)
        processor.onNext(Record(topic, chunkAckKey.next(), nextChunkReceived), nextChunkReceived, emptyMap())

        assertThat(status(chunkAckKey)).isEqualTo(CpiUploadManager.UploadStatus.OK)
    }

    @Test
    fun `processor returns status OK on next chunk received out of order`() {
        val chunkAckKey = incrementingChunkKey()
        // Out of order - don't inline these.
        val key0 = chunkAckKey.next()
        val key1 = chunkAckKey.next()
        val key2 = chunkAckKey.next()

        processor.onSnapshot(mapOf(key0 to ChunkAck(false, null)))

        val nextChunkReceived = ChunkAck(true, null)
        processor.onNext(Record(topic, key2, nextChunkReceived), nextChunkReceived, emptyMap())
        assertThat(status(chunkAckKey)).isEqualTo(CpiUploadManager.UploadStatus.IN_PROGRESS)

        val finalChunkReceived = ChunkAck(false, null)
        processor.onNext(Record(topic, key1, finalChunkReceived), finalChunkReceived, emptyMap())
        assertThat(status(chunkAckKey)).isEqualTo(CpiUploadManager.UploadStatus.OK)
    }

    @Test
    fun `processor returns failed status and exception in failed chunk received`() {
        val chunkAckKey = incrementingChunkKey()
        processor.onSnapshot(mapOf(chunkAckKey.next() to ChunkAck(false, null)))

        // Still in progress with missing chunk
        val statusOfLastChunk = ChunkAck(false, null)
        processor.onNext(Record(topic, chunkAckKey.next(), statusOfLastChunk), statusOfLastChunk, emptyMap())

        val statusOfNextChunk = ChunkAck(false, ExceptionEnvelope("SomeException", "some message"))
        processor.onNext(Record(topic, chunkAckKey.next(), statusOfNextChunk), statusOfNextChunk, emptyMap())

        assertThat(status(chunkAckKey)).isEqualTo(CpiUploadManager.UploadStatus.FAILED)
        assertThat(exceptionEnvelope(chunkAckKey)).isNotNull
    }

    @Test
    fun `processor removes chunks received if necessary`() {
        // Not sure that we'll ever get this situation, but tests the code anyway.

        val chunkAckKey = incrementingChunkKey()
        processor.onSnapshot(emptyMap())

        val firstChunk = ChunkAck(false, null)
        processor.onNext(Record(topic, chunkAckKey.next(), firstChunk), firstChunk, emptyMap())

        val nextChunkReceived = ChunkAck(false, null)
        processor.onNext(Record(topic, chunkAckKey.next(), nextChunkReceived), nextChunkReceived, emptyMap())

        assertThat(status(chunkAckKey)).isEqualTo(CpiUploadManager.UploadStatus.IN_PROGRESS)

        processor.onNext(Record(topic, chunkAckKey.next(), null), nextChunkReceived, emptyMap())
        assertThat(status(chunkAckKey)).isEqualTo(CpiUploadManager.UploadStatus.FAILED)
    }

    @Test
    fun `processor handles two different requests`() {
        val chunkKeyA = incrementingChunkKey()
        val chunkKeyB = incrementingChunkKey()
        processor.onSnapshot(emptyMap())

        processor.onNext(Record(topic, chunkKeyA.next(), ChunkAck(false, null)), null, emptyMap())
        processor.onNext(Record(topic, chunkKeyB.next(), ChunkAck(false, null)), null, emptyMap())

        assertThat(status(chunkKeyA)).isEqualTo(CpiUploadManager.UploadStatus.IN_PROGRESS)
        assertThat(status(chunkKeyB)).isEqualTo(CpiUploadManager.UploadStatus.IN_PROGRESS)

        processor.onNext(Record(topic, chunkKeyA.next(), ChunkAck(true, null)), null, emptyMap())
        processor.onNext(Record(topic, chunkKeyB.next(), ChunkAck(true, null)), null, emptyMap())

        assertThat(status(chunkKeyA)).isEqualTo(CpiUploadManager.UploadStatus.OK)
        assertThat(status(chunkKeyB)).isEqualTo(CpiUploadManager.UploadStatus.OK)
    }
}
