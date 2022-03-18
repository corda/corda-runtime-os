package net.corda.libs.cpiupload.impl

import net.corda.chunking.RequestId
import net.corda.data.ExceptionEnvelope
import net.corda.data.chunking.ChunkAck
import net.corda.data.chunking.ChunkAckKey
import net.corda.libs.cpiupload.CpiUploadManager
import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * This tracks the over state of an upload request.
 *
 * If any chunk received has "failed" for whatever reason, the request has failed.
 *
 * If all chunks are received and any chunk has status 'ok', the request has succeeded.
 *
 * Otherwise, the request must be in progress.
 *
 * The class is entirely dependent on its inputs, i.e. it only expects an [ExceptionEnvelope] for a [UploadStatus.FAILED]
 */
class UploadStatusTracker {
    inner class Part(val last:Boolean, val exceptionEnvelope: ExceptionEnvelope?)

    /** Track the ChunkAck for this request.
     *
     * "Collapses" the [UploadStatus] on each message to a single value.
     * We need to do this because we're receiving these messages on a Compacted Queue
     * and they may arrive out of order from multiple publishers, to us (multiple subscribers).
     **/
    inner class Request {
        private val parts = mutableSetOf<Part>()
        private var expectedParts = -1

        fun add(key: ChunkAckKey, ack: ChunkAck) {
            parts.add(Part(ack.last, ack.exception))
            if (ack.last) expectedParts = key.partNumber + 1
        }

        private val complete: Boolean get() = parts.size == expectedParts

        /**
         * Return the status of this request.
         * By default, the status is [UploadStatus.IN_PROGRESS].
         * If there is one or more [UploadStatus.FAILED] the upload has failed.
         * If there is one [UploadStatus.OK] and all messages received, the upload has succeeded.
         */
        fun status(): CpiUploadManager.UploadStatus {
            if (parts.count { it.last } > 1) {
                // We should never see this in non-test code.  If we're sent two ChunkAcks with
                // last = true, that means we've sent two zero size Chunks which means a failure
                // in either the chunking library or the ChunkAck publishing code.
                throw CordaRuntimeException("Cannot correctly determine number of chunks received")
            }

            if (parts.any { it.exceptionEnvelope != null }) {
                return CpiUploadManager.UploadStatus.FAILED
            }

            if (complete) {
                return CpiUploadManager.UploadStatus.OK
            }

            return CpiUploadManager.UploadStatus.IN_PROGRESS
        }

        /**
         * Return the exception envelope for the first failed chunk.
         *
         * No other chunks ("in progress" or "ok") should contain an exception envelope .
         */
        fun exceptionEnvelope(): ExceptionEnvelope? =
            parts.firstOrNull { it.exceptionEnvelope != null }?.exceptionEnvelope
    }

    private val requestById = mutableMapOf<RequestId, Request>()

    fun clear() = requestById.clear()

    fun remove(requestId: RequestId /* = kotlin.String */) = requestById.remove(requestId)

    fun add(key: ChunkAckKey, value: ChunkAck) =
        requestById.computeIfAbsent(key.requestId) { Request() }.add(key, value)

    fun status(requestId: RequestId): CpiUploadManager.UploadStatus =
        requestById[requestId]?.status() ?: CpiUploadManager.UploadStatus.FAILED

    fun exceptionEnvelope(requestId: RequestId): ExceptionEnvelope? {
        return if (requestById.containsKey(requestId)) {
            requestById[requestId]?.exceptionEnvelope()
        } else {
            ExceptionEnvelope(
                "NoSuchUpload",
                "No such upload for requestId=$requestId"
            )
        }
    }
}
