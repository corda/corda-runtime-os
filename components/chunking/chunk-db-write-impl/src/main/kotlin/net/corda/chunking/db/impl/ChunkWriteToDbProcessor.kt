package net.corda.chunking.db.impl

import net.corda.chunking.RequestId
import net.corda.data.ExceptionEnvelope
import net.corda.data.chunking.Chunk
import net.corda.data.chunking.UploadFileStatus
import net.corda.data.chunking.UploadStatus
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.contextLogger

/**
 * Persist a [Chunk] to the database and send an [UploadStatus] message
 */
class ChunkWriteToDbProcessor(
    private val statusTopic: String,
    private val queries: DatabaseQueries,
    private val validator: CpiValidator
) : DurableProcessor<RequestId, Chunk> {
    companion object {
        private val log = contextLogger()
        private val emptyExceptionEnvelope = ExceptionEnvelope("", "")
    }

    /**
     * We need to "guard" against "mutating" a final state (anything that's not 'in progress').
     *
     * This ensures the snapshot of the compacted queue is accurate.
     */
    private class InternalStatus {
        private val statusByRequestId = mutableMapOf<String, UploadFileStatus>()

        /**
         * Return the actual status. Prevents us from changing an errored upload to an OK one
         * and an OK one to an errored one.
         *
         * Adds the new status, but only overwrite IN_PROGRESS - if we've errored, or completed
         * we don't want to change the status.
         *
         * @param requestId the request id
         * @param newStatus the proposed new status
         * @return the actual status
         */
        fun setAndReturnActualStatus(requestId: RequestId, newStatus: UploadFileStatus): UploadFileStatus {
            val currentStatus = statusByRequestId.putIfAbsent(requestId, newStatus) ?: return newStatus

            // Only in-progress can change state to something else.
            if (currentStatus != UploadFileStatus.UPLOAD_IN_PROGRESS) return currentStatus

            // change in-progress to [in-progress, ok, failed, invalid]
            statusByRequestId[requestId] = newStatus
            return newStatus
        }
    }

    private val internalStatus = InternalStatus()

    private fun processChunk(request: Chunk): UploadStatus {
        log.debug(
            "Processing chunk id = ${request.requestId} / filename = ${request.fileName} " +
                    "/ part = ${request.partNumber}"
        )

        var exceptionEnvelope = emptyExceptionEnvelope
        var uploadComplete = AllChunksReceived.NO

        try {
            uploadComplete = queries.persistChunk(request)
            internalStatus.setAndReturnActualStatus(request.requestId, UploadFileStatus.UPLOAD_IN_PROGRESS)
        } catch (e: Exception) {
            // If we fail to publish one chunk, we cannot easily recover so need to return some error.
            log.error(
                "Could not persist chunk id = ${request.requestId} / filename = ${request.fileName} " +
                        "/ part = ${request.partNumber}",
                e
            )
            exceptionEnvelope = ExceptionEnvelope(e::class.java.name, e.message)
        }

        val newStatus: UploadFileStatus =
            if (!exceptionEnvelope.errorMessage.isNullOrEmpty() && !exceptionEnvelope.errorType.isNullOrEmpty()) {
                UploadFileStatus.UPLOAD_FAILED
            } else {
                // either ok, still in-progress, or invalid
                validateBinary(request.requestId, uploadComplete)
            }

        val actualStatus = internalStatus.setAndReturnActualStatus(request.requestId, newStatus)

        return UploadStatus(request.requestId, actualStatus, exceptionEnvelope)
    }

    /** Checks the checksum and "other things" tbd. */
    private fun validateBinary(requestId: RequestId, uploadComplete: AllChunksReceived): UploadFileStatus {
        log.debug("Validating chunks for $requestId")

        // Haven't received all the chunks yet?  That's ok, just return
        if (uploadComplete == AllChunksReceived.NO) return UploadFileStatus.UPLOAD_IN_PROGRESS

        // Check the checksum of the bytes match.  It should be "impossible" for this to happen.
        if (!queries.checksumIsValid(requestId)) return UploadFileStatus.UPLOAD_FAILED

        // Once we're here, we have the full set of chunks, so now we validate - it's either ok, or file_invalid
        return validator.validate(requestId)
    }

    override fun onNext(events: List<Record<RequestId, Chunk>>): List<Record<*, *>> {
        return events.map {
            val uploadStatus = processChunk(it.value!!)
            Record(statusTopic, it.key, uploadStatus)
        }
    }

    override val keyClass: Class<String> = String::class.java

    override val valueClass: Class<Chunk> = Chunk::class.java
}
