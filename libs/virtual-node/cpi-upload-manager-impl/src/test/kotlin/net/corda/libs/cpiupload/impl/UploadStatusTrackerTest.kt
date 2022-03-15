package net.corda.libs.cpiupload.impl

import net.corda.data.chunking.ChunkReceived
import net.corda.data.chunking.UploadStatus
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
        assertThat(tracker.status(requestId)).isEqualTo(UploadStatus.FAILED)
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
        tracker.add(ChunkReceived(requestId, UploadStatus.IN_PROGRESS, 0, false, null))
        assertThat(tracker.status(requestId)).isEqualTo(UploadStatus.IN_PROGRESS)
    }

    @Test
    fun `upload tracker does not return exception envelope for in progress request`() {
        val requestId = UUID.randomUUID().toString()
        val tracker = UploadStatusTracker()
        tracker.add(ChunkReceived(requestId, UploadStatus.IN_PROGRESS, 0, false, null))
        assertThat(tracker.exceptionEnvelope(requestId)).isNull()
    }

    @Test
    fun `upload tracker returns failed for any fail`() {
        val requestId = UUID.randomUUID().toString()
        val tracker = UploadStatusTracker()
        tracker.add(ChunkReceived(requestId, UploadStatus.IN_PROGRESS, 0, false, null))
        tracker.add(ChunkReceived(requestId, UploadStatus.FAILED, 1, false, null))
        tracker.add(ChunkReceived(requestId, UploadStatus.OK, 2, false, null))
        assertThat(tracker.exceptionEnvelope(requestId)).isNull()
    }

    @Test
    fun `upload tracker is in progress if last part not received`() {
        val requestId = UUID.randomUUID().toString()
        val tracker = UploadStatusTracker()
        tracker.add(ChunkReceived(requestId, UploadStatus.IN_PROGRESS, 0, false, null))
        tracker.add(ChunkReceived(requestId, UploadStatus.IN_PROGRESS, 1, false, null))
        tracker.add(ChunkReceived(requestId, UploadStatus.OK, 2, false, null))
        assertThat(tracker.exceptionEnvelope(requestId)).isNull()
    }

    @Test
    fun `upload tracker is in progress last chunk received but not all chunks`() {
        val requestId = UUID.randomUUID().toString()
        val tracker = UploadStatusTracker()
        tracker.add(ChunkReceived(requestId, UploadStatus.IN_PROGRESS, 0, false, null))
        tracker.add(ChunkReceived(requestId, UploadStatus.IN_PROGRESS, 1, false, null))
        // no 2
        tracker.add(ChunkReceived(requestId, UploadStatus.OK, 3, true, null))
        assertThat(tracker.status(requestId)).isEqualTo(UploadStatus.IN_PROGRESS)
    }

    @Test
    fun `upload tracker is ok if all chunks received`() {
        val requestId = UUID.randomUUID().toString()
        val tracker = UploadStatusTracker()
        tracker.add(ChunkReceived(requestId, UploadStatus.IN_PROGRESS, 0, false, null))
        tracker.add(ChunkReceived(requestId, UploadStatus.IN_PROGRESS, 1, false, null))
        tracker.add(ChunkReceived(requestId, UploadStatus.IN_PROGRESS, 2, false, null))
        tracker.add(ChunkReceived(requestId, UploadStatus.OK, 3, true, null))
        assertThat(tracker.status(requestId)).isEqualTo(UploadStatus.OK)
    }

    @Test
    fun `upload tracker is ok if all chunks received and ok and last are different chunks`() {
        val requestId = UUID.randomUUID().toString()
        val tracker = UploadStatusTracker()
        // first chunk could be received last by one of the db-workers
        tracker.add(ChunkReceived(requestId, UploadStatus.OK, 0, false, null))
        tracker.add(ChunkReceived(requestId, UploadStatus.IN_PROGRESS, 1, false, null))
        tracker.add(ChunkReceived(requestId, UploadStatus.IN_PROGRESS, 2, false, null))
        tracker.add(ChunkReceived(requestId, UploadStatus.IN_PROGRESS, 3, true, null))
        assertThat(tracker.status(requestId)).isEqualTo(UploadStatus.OK)
    }

    @Test
    fun `upload tracker is ok with random ordering of chunks`() {
        val requestId = UUID.randomUUID().toString()
        val tracker = UploadStatusTracker()
        tracker.add(ChunkReceived(requestId, UploadStatus.OK, 3, true, null))
        tracker.add(ChunkReceived(requestId, UploadStatus.IN_PROGRESS, 1, false, null))
        tracker.add(ChunkReceived(requestId, UploadStatus.IN_PROGRESS, 0, false, null))
        tracker.add(ChunkReceived(requestId, UploadStatus.IN_PROGRESS, 2, false, null))
        assertThat(tracker.status(requestId)).isEqualTo(UploadStatus.OK)
    }

    @Test
    fun `upload tracker does not return exception envelope for ok request`() {
        val requestId = UUID.randomUUID().toString()
        val tracker = UploadStatusTracker()
        tracker.add(ChunkReceived(requestId, UploadStatus.OK, 3, true, null))
        tracker.add(ChunkReceived(requestId, UploadStatus.IN_PROGRESS, 1, false, null))
        tracker.add(ChunkReceived(requestId, UploadStatus.IN_PROGRESS, 0, false, null))
        tracker.add(ChunkReceived(requestId, UploadStatus.IN_PROGRESS, 2, false, null))
        assertThat(tracker.exceptionEnvelope(requestId)).isNull()
    }

    @Test
    fun `upload tracker handles more than one request`() {
        val requestId = UUID.randomUUID().toString()
        val otherRequestId = UUID.randomUUID().toString()

        val tracker = UploadStatusTracker()
        tracker.add(ChunkReceived(requestId, UploadStatus.OK, 3, true, null))
        tracker.add(ChunkReceived(requestId, UploadStatus.IN_PROGRESS, 1, false, null))

        tracker.add(ChunkReceived(otherRequestId, UploadStatus.IN_PROGRESS, 5, false, null))

        tracker.add(ChunkReceived(requestId, UploadStatus.IN_PROGRESS, 0, false, null))
        tracker.add(ChunkReceived(requestId, UploadStatus.IN_PROGRESS, 2, false, null))

        assertThat(tracker.status(requestId)).isEqualTo(UploadStatus.OK)
        assertThat(tracker.status(otherRequestId)).isEqualTo(UploadStatus.IN_PROGRESS)
    }

    @Test
    fun `upload tracker throws exception on multiple last chunks`() {
        val requestId = UUID.randomUUID().toString()

        val tracker = UploadStatusTracker()
        tracker.add(ChunkReceived(requestId, UploadStatus.OK, 3, true, null))
        tracker.add(ChunkReceived(requestId, UploadStatus.IN_PROGRESS, 1, true, null))

        assertThrows<CordaRuntimeException> {
            tracker.status(requestId)
        }
    }

    @Test
    fun `upload tracker throws exception on ok and failed chunks`() {
        // This should not happen in a production environment
        val requestId = UUID.randomUUID().toString()

        val tracker = UploadStatusTracker()
        tracker.add(ChunkReceived(requestId, UploadStatus.OK, 3, true, null))
        tracker.add(ChunkReceived(requestId, UploadStatus.FAILED, 1, true, null))

        assertThrows<CordaRuntimeException> {
            tracker.status(requestId)
        }
    }
}
