package net.corda.libs.cpiupload.impl

import net.corda.data.ExceptionEnvelope
import net.corda.data.chunking.UploadFileStatus
import net.corda.data.chunking.UploadStatus
import net.corda.libs.cpiupload.CpiUploadStatus
import net.corda.messaging.api.records.Record
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

internal class UploadStatusProcessorTest {

    private lateinit var processor: UploadStatusProcessor
    private val emptyExceptionEnvelope = ExceptionEnvelope()
    private val topic = "don't care"

    @BeforeEach
    private fun beforeEach() {
        processor = UploadStatusProcessor()
    }

    @Test
    fun onSnapshot() {
        val expectedId = UUID.randomUUID().toString()
        val unexpectedId = UUID.randomUUID().toString()
        val currentData = mapOf(
            expectedId to UploadStatus(expectedId, UploadFileStatus.UPLOAD_IN_PROGRESS, emptyExceptionEnvelope)
        )
        processor.onSnapshot(currentData)

        assertThat(processor.status(expectedId)).isEqualTo(CpiUploadStatus.IN_PROGRESS)
        assertThat(processor.status(unexpectedId)).isEqualTo(CpiUploadStatus.NO_SUCH_REQUEST_ID)
    }

    @Test
    fun `onNext with in order chunks`() {
        val expectedId = UUID.randomUUID().toString()
        val currentData = mapOf(
            expectedId to UploadStatus(expectedId, UploadFileStatus.UPLOAD_IN_PROGRESS, emptyExceptionEnvelope)
        )
        processor.onSnapshot(currentData)

        assertThat(processor.status(expectedId)).isEqualTo(CpiUploadStatus.IN_PROGRESS)

        val statusOfLastChunk = UploadStatus(expectedId, UploadFileStatus.OK, emptyExceptionEnvelope)
        // Not quite sure about the current data, but we don't use it...
        processor.onNext(Record(topic, expectedId, statusOfLastChunk), statusOfLastChunk, emptyMap())

        assertThat(processor.status(expectedId)).isEqualTo(CpiUploadStatus.OK)
    }

    @Test
    fun `onNext with empty snapshot`() {
        val expectedId = UUID.randomUUID().toString()
        processor.onSnapshot(emptyMap())

        val firstChunk = UploadStatus(expectedId, UploadFileStatus.UPLOAD_IN_PROGRESS, emptyExceptionEnvelope)
        processor.onNext(Record(topic, expectedId, firstChunk), firstChunk, emptyMap())
        assertThat(CpiUploadStatus.IN_PROGRESS).isEqualTo(processor.status(expectedId))

        val statusOfLastChunk = UploadStatus(expectedId, UploadFileStatus.OK, emptyExceptionEnvelope)
        processor.onNext(Record(topic, expectedId, statusOfLastChunk), statusOfLastChunk, emptyMap())
        assertThat(CpiUploadStatus.OK).isEqualTo(processor.status(expectedId))
    }

    @Test
    fun `onNext with out of order chunks`() {
        val expectedId = UUID.randomUUID().toString()
        val currentData = mapOf(
            expectedId to UploadStatus(expectedId, UploadFileStatus.UPLOAD_IN_PROGRESS, emptyExceptionEnvelope)
        )
        processor.onSnapshot(currentData)
        assertThat(CpiUploadStatus.IN_PROGRESS).isEqualTo(processor.status(expectedId))

        // Still in progress with missing chunk
        val statusOfLastChunk = UploadStatus(expectedId, UploadFileStatus.UPLOAD_IN_PROGRESS, emptyExceptionEnvelope)
        processor.onNext(Record(topic, expectedId, statusOfLastChunk), statusOfLastChunk, emptyMap())
        assertThat(CpiUploadStatus.IN_PROGRESS).isEqualTo(processor.status(expectedId))

        val statusOfNextChunk = UploadStatus(expectedId, UploadFileStatus.OK, emptyExceptionEnvelope)
        processor.onNext(Record(topic, expectedId, statusOfNextChunk), statusOfNextChunk, emptyMap())
        assertThat(CpiUploadStatus.OK).isEqualTo(processor.status(expectedId))
    }

    @Test
    fun `status with exception in last uploaded chunk`() {
        val expectedId = UUID.randomUUID().toString()
        val currentData = mapOf(
            expectedId to UploadStatus(expectedId, UploadFileStatus.UPLOAD_IN_PROGRESS, emptyExceptionEnvelope)
        )
        processor.onSnapshot(currentData)
        assertThat(CpiUploadStatus.IN_PROGRESS).isEqualTo(processor.status(expectedId))

        // Still in progress with missing chunk
        val statusOfLastChunk = UploadStatus(expectedId, UploadFileStatus.UPLOAD_IN_PROGRESS, emptyExceptionEnvelope)
        processor.onNext(Record(topic, expectedId, statusOfLastChunk), statusOfLastChunk, emptyMap())
        assertThat(CpiUploadStatus.IN_PROGRESS).isEqualTo(processor.status(expectedId))

        val statusOfNextChunk = UploadStatus(
            expectedId,
            UploadFileStatus.FILE_INVALID,
            ExceptionEnvelope("SomeException", "some message")
        )
        processor.onNext(Record(topic, expectedId, statusOfNextChunk), statusOfNextChunk, emptyMap())
        assertThat(CpiUploadStatus.FILE_INVALID).isEqualTo(processor.status(expectedId))
    }

    @Test
    fun `remove chunks`() {
        // Not sure that we'll ever get this situation, but tests the code anyway.

        val expectedId = UUID.randomUUID().toString()
        val firstChunk = UploadStatus(expectedId, UploadFileStatus.UPLOAD_IN_PROGRESS, emptyExceptionEnvelope)
        processor.onSnapshot(emptyMap())

        processor.onNext(Record(topic, expectedId, firstChunk), firstChunk, emptyMap())
        assertThat(processor.status(expectedId)).isEqualTo(CpiUploadStatus.IN_PROGRESS)

        val statusOfLastChunk = UploadStatus(expectedId, UploadFileStatus.UPLOAD_IN_PROGRESS, emptyExceptionEnvelope)
        processor.onNext(Record(topic, expectedId, statusOfLastChunk), statusOfLastChunk, emptyMap())

        assertThat(processor.status(expectedId)).isEqualTo(CpiUploadStatus.IN_PROGRESS)

        processor.onNext(Record(topic, expectedId, null), statusOfLastChunk, emptyMap())
        assertThat(processor.status(expectedId)).isEqualTo(CpiUploadStatus.NO_SUCH_REQUEST_ID)

        processor.onNext(Record(topic, expectedId, null), firstChunk, emptyMap())
        assertThat(processor.status(expectedId)).isEqualTo(CpiUploadStatus.NO_SUCH_REQUEST_ID)
    }

    @Test
    fun `two different requestIds`() {
        val id1 = UUID.randomUUID().toString()
        val id2 = UUID.randomUUID().toString()
        processor.onSnapshot(emptyMap())

        val firstChunk2 = UploadStatus(id2, UploadFileStatus.UPLOAD_IN_PROGRESS, emptyExceptionEnvelope)
        processor.onNext(Record(topic, id2, firstChunk2), firstChunk2, emptyMap())
        assertThat(CpiUploadStatus.IN_PROGRESS).isEqualTo(processor.status(id2))

        val firstChunk1 = UploadStatus(id1, UploadFileStatus.UPLOAD_IN_PROGRESS, emptyExceptionEnvelope)
        processor.onNext(Record(topic, id1, firstChunk1), firstChunk1, emptyMap())
        assertThat(CpiUploadStatus.IN_PROGRESS).isEqualTo(processor.status(id1))

        val statusOfLastChunk2 = UploadStatus(id2, UploadFileStatus.OK, emptyExceptionEnvelope)
        processor.onNext(Record(topic, id2, statusOfLastChunk2), statusOfLastChunk2, emptyMap())
        assertThat(CpiUploadStatus.OK).isEqualTo(processor.status(id2))

        val statusOfLastChunk1 = UploadStatus(id1, UploadFileStatus.OK, emptyExceptionEnvelope)
        processor.onNext(Record(topic, id1, statusOfLastChunk1), statusOfLastChunk1, emptyMap())
        assertThat(CpiUploadStatus.OK).isEqualTo(processor.status(id1))
    }

    @Test
    fun `cannot overwrite status of a failed chunk`() {
        val expectedId = UUID.randomUUID().toString()
        processor.onSnapshot(emptyMap())

        val statusOfFirstChunk =
            UploadStatus(expectedId, UploadFileStatus.FILE_INVALID, ExceptionEnvelope("SomeException", "some message"))
        processor.onNext(Record(topic, expectedId, statusOfFirstChunk), statusOfFirstChunk, emptyMap())
        assertThat(CpiUploadStatus.FILE_INVALID).isEqualTo(processor.status(expectedId))

        val statusOfLastChunk = UploadStatus(expectedId, UploadFileStatus.UPLOAD_IN_PROGRESS, emptyExceptionEnvelope)
        assertThrows<CordaRuntimeException> {
            processor.onNext(Record(topic, expectedId, statusOfLastChunk), statusOfFirstChunk, emptyMap())
        }
    }
}
