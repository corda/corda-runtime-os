package net.corda.libs.cpiupload.impl

import net.corda.chunking.RequestId
import net.corda.data.chunking.UploadStatus
import net.corda.data.chunking.UploadStatusKey

/**
 * This tracks the over state of an upload request.
 */
class UploadStatusTracker {
    /** Track the status for this request.
     *
     * Records the [UploadStatus] on each message, and returns the one with the highest sequence number, i.e. the 'latest'.
     * We need to do this because we're receiving these messages on a Compacted Queue
     * and they may arrive out of order from multiple publishers, to us (multiple subscribers).
     *
     * We expect the '0' message to be repeatedly updated from multiple db-processors, but only ONE db-processor
     * will then go on to validate the upload because it will be the one (and only one) handling the final chunk,
     * and move into the validation code.
     *
     * This will then send (1..N) upload status messages from the same db-processor.  They may not necessarily
     * arrive in order but the db-processor part of the code will send `complete=true` in the (last)
     * *highest sequence number message*.
     **/
    inner class LatestStatus {
        // Can't be a List<Pair<Int,...>> because we might repeatedly overwrite with the same sequence number
        // particularly for '0' where we might receive updates from multiple (chunk) processors.
        private val status = mutableMapOf<Int, UploadStatus>()

        fun add(key: UploadStatusKey, value: UploadStatus) {
            status[key.sequenceNumber] = value
        }

        /**
         * Return the latest status of this request.
         */
        fun latest(): UploadStatus = status[status.keys.maxOrNull()!!]!!
    }

    private val statusById = mutableMapOf<RequestId, LatestStatus>()

    fun clear() = statusById.clear()

    fun remove(requestId: RequestId /* = kotlin.String */) = statusById.remove(requestId)

    /** Add an upload status for a given request */
    fun add(key: UploadStatusKey, value: UploadStatus) =
        statusById.computeIfAbsent(key.requestId) { LatestStatus() }.add(key, value)

    /** @return latest status for request id, or `null` if no such request */
    fun status(requestId: RequestId): UploadStatus? = statusById[requestId]?.latest()
}
