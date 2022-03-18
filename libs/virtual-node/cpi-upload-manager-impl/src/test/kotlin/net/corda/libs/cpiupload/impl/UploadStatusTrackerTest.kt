package net.corda.libs.cpiupload.impl

import net.corda.data.ExceptionEnvelope
import net.corda.data.chunking.ChunkAck
import net.corda.data.chunking.ChunkAckKey
import net.corda.libs.cpiupload.CpiUploadManager
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID


internal class UploadStatusTrackerTest {
    @Test
    fun `upload tracker returns failed for unknown request`() {
        val requestId = UUID.randomUUID().toString()
        val tracker = UploadStatusTracker()
        assertThat(tracker.status(requestId)).isEqualTo(CpiUploadManager.UploadStatus.FAILED)
    }

    @Test
    fun `upload tracker returns exception envelope for unknown request`() {
        val requestId = UUID.randomUUID().toString()
        val tracker = UploadStatusTracker()
        assertThat(tracker.exceptionEnvelope(requestId)).isNotNull
    }

    @Test
    fun `upload tracker returns in progress for in progress request`() {
        val requestId = UUID.randomUUID().toString()
        val tracker = UploadStatusTracker()
        tracker.add(ChunkAckKey(requestId, 0), ChunkAck(false, null))
        assertThat(tracker.status(requestId)).isEqualTo(CpiUploadManager.UploadStatus.IN_PROGRESS)
    }

    @Test
    fun `upload tracker does not return exception envelope for in progress request`() {
        val requestId = UUID.randomUUID().toString()
        val tracker = UploadStatusTracker()
        tracker.add(ChunkAckKey(requestId, 0), ChunkAck(false, null))
        assertThat(tracker.exceptionEnvelope(requestId)).isNull()
    }

    @Test
    fun `upload tracker returns failed for any fail`() {
        val requestId = UUID.randomUUID().toString()
        val tracker = UploadStatusTracker()
        tracker.add(ChunkAckKey(requestId, 0), ChunkAck(false, null))
        tracker.add(ChunkAckKey(requestId, 1), ChunkAck(false, ExceptionEnvelope("error", "")))
        tracker.add(ChunkAckKey(requestId, 2), ChunkAck(false, null))
        assertThat(tracker.exceptionEnvelope(requestId)).isNotNull
    }

    @Test
    fun `upload tracker is in progress if last part not received`() {
        val requestId = UUID.randomUUID().toString()
        val tracker = UploadStatusTracker()
        tracker.add(ChunkAckKey(requestId, 0), ChunkAck(false, null))
        tracker.add(ChunkAckKey(requestId, 1), ChunkAck(false, null))
        tracker.add(ChunkAckKey(requestId, 2), ChunkAck(false, null))
        assertThat(tracker.exceptionEnvelope(requestId)).isNull()
    }

    @Test
    fun `upload tracker is in progress last chunk received but not all chunks`() {
        val requestId = UUID.randomUUID().toString()
        val tracker = UploadStatusTracker()
        tracker.add(ChunkAckKey(requestId, 0), ChunkAck(false, null))
        tracker.add(ChunkAckKey(requestId, 1), ChunkAck(false, null))
        // no 2
        tracker.add(ChunkAckKey(requestId, 3), ChunkAck(true, null))
        assertThat(tracker.status(requestId)).isEqualTo(CpiUploadManager.UploadStatus.IN_PROGRESS)
    }

    @Test
    fun `upload tracker is ok if all chunks received`() {
        val requestId = UUID.randomUUID().toString()
        val tracker = UploadStatusTracker()
        tracker.add(ChunkAckKey(requestId, 0), ChunkAck(false, null))
        tracker.add(ChunkAckKey(requestId, 1), ChunkAck(false, null))
        tracker.add(ChunkAckKey(requestId, 2), ChunkAck(false, null))
        tracker.add(ChunkAckKey(requestId, 3), ChunkAck(true, null))
        assertThat(tracker.status(requestId)).isEqualTo(CpiUploadManager.UploadStatus.OK)
    }

    @Test
    fun `upload tracker is ok if all chunks received and ok and last are different chunks`() {
        val requestId = UUID.randomUUID().toString()
        val tracker = UploadStatusTracker()
        // first chunk could be received last by one of the db-workers
        tracker.add(ChunkAckKey(requestId, 0), ChunkAck(false, null))
        tracker.add(ChunkAckKey(requestId, 1), ChunkAck(false, null))
        tracker.add(ChunkAckKey(requestId, 2), ChunkAck(false, null))
        tracker.add(ChunkAckKey(requestId, 3), ChunkAck(true, null))
        assertThat(tracker.status(requestId)).isEqualTo(CpiUploadManager.UploadStatus.OK)
    }

    @Test
    fun `upload tracker is ok with random ordering of chunks`() {
        val requestId = UUID.randomUUID().toString()
        val tracker = UploadStatusTracker()
        tracker.add(ChunkAckKey(requestId, 3), ChunkAck(true, null))
        tracker.add(ChunkAckKey(requestId, 1), ChunkAck(false, null))
        tracker.add(ChunkAckKey(requestId, 0), ChunkAck(false, null))
        tracker.add(ChunkAckKey(requestId, 2), ChunkAck(false, null))
        assertThat(tracker.status(requestId)).isEqualTo(CpiUploadManager.UploadStatus.OK)
    }

    @Test
    fun `upload tracker does not return exception envelope for ok request`() {
        val requestId = UUID.randomUUID().toString()
        val tracker = UploadStatusTracker()
        tracker.add(ChunkAckKey(requestId, 3), ChunkAck(true, null))
        tracker.add(ChunkAckKey(requestId, 1), ChunkAck(false, null))
        tracker.add(ChunkAckKey(requestId, 0), ChunkAck(false, null))
        tracker.add(ChunkAckKey(requestId, 2), ChunkAck(false, null))
        assertThat(tracker.exceptionEnvelope(requestId)).isNull()
    }

    @Test
    fun `upload tracker handles more than one request`() {
        val requestId = UUID.randomUUID().toString()
        val otherRequestId = UUID.randomUUID().toString()

        val tracker = UploadStatusTracker()
        tracker.add(ChunkAckKey(requestId, 3), ChunkAck(true, null))
        tracker.add(ChunkAckKey(requestId, 1), ChunkAck(false, null))

        tracker.add(ChunkAckKey(otherRequestId, 5), ChunkAck(false, null))

        tracker.add(ChunkAckKey(requestId, 0), ChunkAck(false, null))
        tracker.add(ChunkAckKey(requestId, 2), ChunkAck(false, null))

        assertThat(tracker.status(requestId)).isEqualTo(CpiUploadManager.UploadStatus.OK)
        assertThat(tracker.status(otherRequestId)).isEqualTo(CpiUploadManager.UploadStatus.IN_PROGRESS)
    }

    @Test
    fun `upload tracker throws exception on multiple last chunks`() {
        // This should never happen in a production environment
        val requestId = UUID.randomUUID().toString()

        val tracker = UploadStatusTracker()
        tracker.add(ChunkAckKey(requestId, 3), ChunkAck(true, null))
        tracker.add(ChunkAckKey(requestId, 1), ChunkAck(true, null))

        assertThrows<CordaRuntimeException> {
            tracker.status(requestId)
        }
    }
}
