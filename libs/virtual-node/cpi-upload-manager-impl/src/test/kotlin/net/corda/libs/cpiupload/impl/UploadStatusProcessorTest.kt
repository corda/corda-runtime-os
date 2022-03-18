package net.corda.libs.cpiupload.impl

import net.corda.data.ExceptionEnvelope
import net.corda.data.chunking.UploadStatus
import net.corda.data.chunking.UploadStatusKey
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
    private fun isComplete(key: IncrementingChunkKey) = processor.status(key.requestId)?.complete ?: false
    private fun exceptionEnvelope(key: IncrementingChunkKey) = processor.status(key.requestId)!!.exception

    /** Creates a new key with a request id, and [next()] returns the next key and partnumber, starting at 0 */
    class IncrementingChunkKey {
        val requestId = UUID.randomUUID().toString()
        private var sequenceNumber = 0
        fun next(): UploadStatusKey {
            val key = UploadStatusKey(requestId, sequenceNumber)
            ++sequenceNumber
            return key
        }
    }

    @Test
    fun `processor returns incomplete status for in progress request in snapshot`() {
        val chunkAckKey = incrementingChunkKey()
        UploadStatus(false, "", null, null)
        processor.onSnapshot(mapOf(chunkAckKey.next() to UploadStatus(false, "", null, null)))

        assertThat(isComplete(chunkAckKey)).isEqualTo(false)
    }

    @Test
    fun `processor returns null for unknown request not in snapshot`() {
        val chunkAckKey = incrementingChunkKey()
        val unexpectedChunkKey = incrementingChunkKey()

        processor.onSnapshot(mapOf(chunkAckKey.next() to UploadStatus(false, "", null, null)))

        assertThat(isComplete(unexpectedChunkKey)).isEqualTo(false)
        assertThat(processor.status(unexpectedChunkKey.requestId)).isNull()
    }

    @Test
    fun `processor returns status OK on next chunk received in order`() {
        val chunkAckKey = incrementingChunkKey()
        processor.onSnapshot(mapOf(chunkAckKey.next() to UploadStatus(false, "", null, null)))

        val nextChunkReceived = UploadStatus(true, "", null, null)
        processor.onNext(Record(topic, chunkAckKey.next(), nextChunkReceived), nextChunkReceived, emptyMap())

        assertThat(isComplete(chunkAckKey)).isEqualTo(true)
    }

    @Test
    fun `processor returns failed status and exception in failed chunk received`() {
        val chunkAckKey = incrementingChunkKey()
        processor.onSnapshot(mapOf(chunkAckKey.next() to UploadStatus(false, "", null, null)))

        // Still in progress with missing chunk
        val statusOfLastChunk = UploadStatus(false, "", null, null)
        processor.onNext(Record(topic, chunkAckKey.next(), statusOfLastChunk), statusOfLastChunk, emptyMap())

        val statusOfNextChunk = UploadStatus(false, "", null, ExceptionEnvelope("SomeException", "some message"))
        processor.onNext(Record(topic, chunkAckKey.next(), statusOfNextChunk), statusOfNextChunk, emptyMap())

        assertThat(isComplete(chunkAckKey)).isEqualTo(false)
        assertThat(exceptionEnvelope(chunkAckKey)).isNotNull
    }

    @Test
    fun `processor removes chunks received if necessary`() {
        // Not sure that we'll ever get this situation, but tests the code anyway.

        val chunkAckKey = incrementingChunkKey()
        processor.onSnapshot(emptyMap())

        val firstChunk = UploadStatus(false, "", null, null)
        processor.onNext(Record(topic, chunkAckKey.next(), firstChunk), firstChunk, emptyMap())

        val nextChunkReceived = UploadStatus(false, "", null, null)
        processor.onNext(Record(topic, chunkAckKey.next(), nextChunkReceived), nextChunkReceived, emptyMap())

        assertThat(isComplete(chunkAckKey)).isEqualTo(false)

        processor.onNext(Record(topic, chunkAckKey.next(), null), nextChunkReceived, emptyMap())

        assertThat(processor.status(chunkAckKey.requestId)).isNull()
    }

    @Test
    fun `processor handles two different requests`() {
        val chunkKeyA = incrementingChunkKey()
        val chunkKeyB = incrementingChunkKey()
        processor.onSnapshot(emptyMap())

        processor.onNext(Record(topic, chunkKeyA.next(), UploadStatus(false, "", null, null)), null, emptyMap())
        processor.onNext(Record(topic, chunkKeyB.next(), UploadStatus(false, "", null, null)), null, emptyMap())

        assertThat(isComplete(chunkKeyA)).isEqualTo(false)
        assertThat(isComplete(chunkKeyB)).isEqualTo(false)

        processor.onNext(Record(topic, chunkKeyA.next(), UploadStatus(true, "", null, null)), null, emptyMap())
        processor.onNext(Record(topic, chunkKeyB.next(), UploadStatus(true, "", null, null)), null, emptyMap())

        assertThat(isComplete(chunkKeyA)).isEqualTo(true)
        assertThat(isComplete(chunkKeyB)).isEqualTo(true)
    }
}
