package net.corda.libs.cpiupload.impl

import net.corda.chunking.RequestId
import net.corda.data.ExceptionEnvelope
import net.corda.data.chunking.UploadFileStatus
import net.corda.data.chunking.UploadStatus
import net.corda.libs.cpiupload.CpiUploadStatus
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger

/**
 * Receives [UploadStatus] messages on a compacted queue from the db-processor / [net.corda.chunking.db.impl.ChunkWriteToDbProcessor]
 */
@Suppress("UNUSED")
class UploadStatusProcessor : CompactedProcessor<RequestId, UploadStatus> {
    companion object {
        private val log = contextLogger()
        private val emptyExceptionEnvelope = ExceptionEnvelope("", "")
    }

    private val status = mutableMapOf<RequestId, UploadStatus>()

    override val keyClass: Class<String> = String::class.java

    override val valueClass: Class<UploadStatus> = UploadStatus::class.java

    override fun onSnapshot(currentData: Map<RequestId, UploadStatus>) {
        status.clear()

        currentData.forEach {
            status.putIfAbsent(it.key, it.value)
        }
    }

    override fun onNext(
        newRecord: Record<RequestId, UploadStatus>,
        oldValue: UploadStatus?,
        currentData: Map<RequestId, UploadStatus>
    ) {
        // The sender of these messages *should* guarantee that once a non- IN-PROGRESS message is sent, it is never changed
        // (i.e. it's a final state), but can be resent
        if (newRecord.value != null) {
            val newValue = newRecord.value!!

            // Are we trying to change a non IN-PROGRESS status?  This shouldn't happen because we've guarded against it in
            // net.corda.chunking.db.impl.ChunkWriteToDbProcessor where we only send in-progress, or "end states"
            // ok, failed, invalid - this guarantees that the snapshot is always correct.
            if (oldValue != null
                && oldValue.uploadFileStatus != UploadFileStatus.UPLOAD_IN_PROGRESS
                && oldValue.uploadFileStatus != newValue.uploadFileStatus
            ) {
                log.error("Unexpected state change for UploadStatus - see logs for more details")
                // This should not happen - the logic in net.corda.chunking.db.impl.ChunkWriteToDbProcessor should guard for this.
                throw CordaRuntimeException(
                    "Unexpected state change for request id '${newValue.requestId}' " +
                            "from ${oldValue.uploadFileStatus} to ${newValue.uploadFileStatus}"
                )
            }

            status[newRecord.key] = newRecord.value!!
        } else {
            status.remove(newRecord.key)
        }
    }

    /**
     * Return the chunk upload status of the given request id
     *
     * @param requestId request id, partial or complete, if partial, we use the first match
     * @return returns the status of the specified chunk upload request
     */
    fun status(requestId: RequestId): CpiUploadStatus {
        log.debug("Getting status for $requestId")

        if (!status.containsKey(requestId)) return CpiUploadStatus.NO_SUCH_REQUEST_ID

        return when (status[requestId]!!.uploadFileStatus) {
            UploadFileStatus.OK -> CpiUploadStatus.OK
            UploadFileStatus.UPLOAD_FAILED -> CpiUploadStatus.FAILED
            UploadFileStatus.UPLOAD_IN_PROGRESS -> CpiUploadStatus.IN_PROGRESS
            UploadFileStatus.FILE_INVALID -> CpiUploadStatus.FILE_INVALID
            else -> CpiUploadStatus.NO_SUCH_REQUEST_ID
        }
    }
}
