package net.corda.libs.cpiupload.impl

import net.corda.chunking.RequestId
import net.corda.data.ExceptionEnvelope
import net.corda.data.chunking.UploadStatus
import net.corda.data.chunking.ChunkReceived
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
    /** Track the ChunkReceived for this request.
     *
     * "Collapses" the [UploadStatus] on each message to a single value.
     * We need to do this because we're receiving these messages on a Compacted Queue
     * and they may arrive out of order from multiple publishers, to us (multiple subscribers).
     **/
    inner class Request {
        private val parts = mutableSetOf<ChunkReceived>()
        private var expectedParts = -1

        fun add(uploadStatus: ChunkReceived) {
            parts.add(uploadStatus)
            if (uploadStatus.last) expectedParts = uploadStatus.partNumber + 1
        }

        private val complete: Boolean get() = parts.size == expectedParts

        /**
         * Return the status of this request.
         * By default, the status is [UploadStatus.IN_PROGRESS].
         * If there is one or more [UploadStatus.FAILED] the upload has failed.
         * If there is one [UploadStatus.OK] and all messages received, the upload has succeeded.
         */
        fun status(): UploadStatus {
            if (parts.any { it.uploadStatus == UploadStatus.OK } && parts.any { it.uploadStatus == UploadStatus.FAILED }) {
                // This should never happen, and should only be triggered by a careful crafted test case.
                // In a production setting, this would be because one chunk was 'OK' (i.e. we actually wrote the full
                // upload to the database) and one FAILED.  Yet we could
                // never send an OK if we previously had a fail, and if sent a FAILED, we should have halted.
                throw CordaRuntimeException("At least one part and but the whole request was OK")
            }

            // One fail is a definite fail.
            if (parts.any { it.uploadStatus == UploadStatus.FAILED }) {
                return UploadStatus.FAILED
            }

            if (parts.count { it.last } > 1) {
                // This exception should only ever happen in testing, otherwise something has really gone wrong.
                // In a production setting, this would be because two "zero sized chunks" were sent for the
                // given request, which shouldn't happen.
                throw CordaRuntimeException("Two or more parts received for uploaded request id=${parts.first().requestId}")
            }

            // Entirely possible to receive a message with status = 'OK', but not received all messages
            // due to network issues, ordering etc.  So we must also check if we have also received all chunks.
            if (parts.any { it.uploadStatus == UploadStatus.OK } && complete) {
                return UploadStatus.OK
            }

            return UploadStatus.IN_PROGRESS
        }

        /**
         * Return the exception envelope for the first failed chunk.
         *
         * No other chunks ("in progress" or "ok") should contain an exception envelope .
         */
        fun exceptionEnvelope(): ExceptionEnvelope? =
            parts.firstOrNull{ it.uploadStatus == UploadStatus.FAILED }?.exception
    }

    private val requestById = mutableMapOf<RequestId, Request>()

    fun clear() = requestById.clear()

    fun remove(requestId: RequestId /* = kotlin.String */) = requestById.remove(requestId)

    fun add(uploadStatus: ChunkReceived) =
        requestById.computeIfAbsent(uploadStatus.requestId) { Request() }.add(uploadStatus)

    fun status(requestId: RequestId): UploadStatus =
        requestById[requestId]?.status() ?: UploadStatus.FAILED

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
