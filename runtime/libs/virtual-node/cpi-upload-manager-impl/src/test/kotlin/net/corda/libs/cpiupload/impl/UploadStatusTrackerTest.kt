package net.corda.libs.cpiupload.impl

import net.corda.data.ExceptionEnvelope
import net.corda.data.chunking.UploadStatus
import net.corda.data.chunking.UploadStatusKey
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID


internal class UploadStatusTrackerTest {
    @Test
    fun `upload tracker returns null unknown request`() {
        val requestId = UUID.randomUUID().toString()
        val tracker = UploadStatusTracker()
        assertThat(tracker.status(requestId)).isNull()
    }

    @Test
    fun `upload tracker returns in progress for in progress request`() {
        val requestId = UUID.randomUUID().toString()
        val tracker = UploadStatusTracker()
        tracker.add(UploadStatusKey(requestId, 0), UploadStatus(false, "", null, null))
        assertThat(tracker.status(requestId)!!.complete).isEqualTo(false)
    }

    @Test
    fun `upload tracker does not return exception envelope for in progress request`() {
        val requestId = UUID.randomUUID().toString()
        val tracker = UploadStatusTracker()
        tracker.add(UploadStatusKey(requestId, 0), UploadStatus(false, "", null, null))
        assertThat(tracker.status(requestId)?.exception).isNull()
    }

    @Test
    fun `upload tracker is in progress if last status message is neither complete nor an exception`() {
        val requestId = UUID.randomUUID().toString()
        val tracker = UploadStatusTracker()
        tracker.add(UploadStatusKey(requestId, 0), UploadStatus(false, "", null, null))
        tracker.add(UploadStatusKey(requestId, 1), UploadStatus(false, "", null, null))
        tracker.add(UploadStatusKey(requestId, 2), UploadStatus(false, "", null, null))
        assertThat(tracker.status(requestId)?.exception).isNull()
    }

    @Test
    fun `upload tracker is complete on receipt of complete message in last sequence number`() {
        val requestId = UUID.randomUUID().toString()
        val tracker = UploadStatusTracker()
        tracker.add(UploadStatusKey(requestId, 0), UploadStatus(false, "", null, null))
        tracker.add(UploadStatusKey(requestId, 1), UploadStatus(false, "", null, null))
        // no 2
        tracker.add(UploadStatusKey(requestId, 3), UploadStatus(true, "", null, null))
        assertThat(tracker.status(requestId)!!.complete).isTrue
    }

    @Test
    fun `upload tracker is ok if last message is marked complete`() {
        val requestId = UUID.randomUUID().toString()
        val tracker = UploadStatusTracker()
        tracker.add(UploadStatusKey(requestId, 0), UploadStatus(false, "", null, null))
        tracker.add(UploadStatusKey(requestId, 1), UploadStatus(false, "", null, null))
        tracker.add(UploadStatusKey(requestId, 2), UploadStatus(false, "", null, null))
        tracker.add(UploadStatusKey(requestId, 3), UploadStatus(true, "", null, null))
        assertThat(tracker.status(requestId)!!.complete).isTrue
    }

    @Test
    fun `upload tracker returns exception in last message`() {
        val requestId = UUID.randomUUID().toString()
        val tracker = UploadStatusTracker()
        tracker.add(UploadStatusKey(requestId, 0), UploadStatus(false, "", null, null))
        tracker.add(UploadStatusKey(requestId, 1), UploadStatus(false, "", null, null))
        tracker.add(UploadStatusKey(requestId, 2), UploadStatus(false, "", null, null))
        tracker.add(UploadStatusKey(requestId, 3), UploadStatus(false, "", null, ExceptionEnvelope("", "")))
        assertThat(tracker.status(requestId)!!.exception). isNotNull
    }

    @Test
    fun `upload tracker handles more than one request`() {
        val requestId = UUID.randomUUID().toString()
        val otherRequestId = UUID.randomUUID().toString()

        val tracker = UploadStatusTracker()
        tracker.add(UploadStatusKey(requestId, 3), UploadStatus(true, "", null, null))
        tracker.add(UploadStatusKey(requestId, 1), UploadStatus(false, "", null, null))

        tracker.add(UploadStatusKey(otherRequestId, 0), UploadStatus(false, "", null, null))

        tracker.add(UploadStatusKey(requestId, 0), UploadStatus(false, "", null, null))
        tracker.add(UploadStatusKey(requestId, 2), UploadStatus(false, "", null, null))

        assertThat(tracker.status(requestId)!!.complete).isTrue
        assertThat(tracker.status(otherRequestId)!!.complete).isFalse
    }
}
