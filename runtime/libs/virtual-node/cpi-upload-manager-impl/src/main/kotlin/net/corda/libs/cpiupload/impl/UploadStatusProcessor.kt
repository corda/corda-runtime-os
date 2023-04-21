package net.corda.libs.cpiupload.impl

import net.corda.chunking.RequestId
import net.corda.data.ExceptionEnvelope
import net.corda.data.chunking.UploadStatus
import net.corda.data.chunking.UploadStatusKey
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import org.slf4j.LoggerFactory

/**
 * Receives [UploadStatus] messages on a compacted queue from the db-processor / [net.corda.chunking.db.impl.ChunkWriteToDbProcessor]
 *
 * Key must match [ChunkWriteToDbProcessor], and that's just a string.  It is the request id *and* part number
 */
@Suppress("UNUSED")
class UploadStatusProcessor : CompactedProcessor<UploadStatusKey, UploadStatus> {
    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private val emptyExceptionEnvelope = ExceptionEnvelope("", "")
    }

    override val keyClass: Class<UploadStatusKey> = UploadStatusKey::class.java

    override val valueClass: Class<UploadStatus> = UploadStatus::class.java

    private val tracker = UploadStatusTracker()

    override fun onSnapshot(currentData: Map<UploadStatusKey, UploadStatus>) {
        tracker.clear()
        currentData.forEach { tracker.add(it.key, it.value) }
    }

    override fun onNext(
        newRecord: Record<UploadStatusKey, UploadStatus>,
        oldValue: UploadStatus?,
        currentData: Map<UploadStatusKey, UploadStatus>
    ) {
        if (newRecord.value != null) {
            tracker.add(newRecord.key, newRecord.value!!)
        } else {
            tracker.remove(newRecord.key.requestId)
        }
    }

    /**
     * Return the chunk upload status of the given request id
     *
     * @param requestId request id, partial or complete, if partial, we use the first match
     * @return returns the status of the specified chunk upload request
     */
    fun status(requestId: RequestId): UploadStatus? {
        log.debug("Getting status for $requestId")
        return tracker.status(requestId)
    }
}
