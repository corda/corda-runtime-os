package net.corda.libs.cpiupload.impl

import net.corda.chunking.RequestId
import net.corda.data.ExceptionEnvelope
import net.corda.data.chunking.ChunkKey
import net.corda.data.chunking.ChunkReceived
import net.corda.data.chunking.UploadStatus
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger

/**
 * Receives [ChunkReceived] messages on a compacted queue from the db-processor / [net.corda.chunking.db.impl.ChunkWriteToDbProcessor]
 *
 * Key must match [ChunkWriteToDbProcessor], and that's just a string.  It is the request id *and* part number
 */
@Suppress("UNUSED")
class UploadStatusProcessor : CompactedProcessor<ChunkKey, ChunkReceived> {
    companion object {
        private val log = contextLogger()
        private val emptyExceptionEnvelope = ExceptionEnvelope("", "")
    }

    override val keyClass: Class<ChunkKey> = ChunkKey::class.java

    override val valueClass: Class<ChunkReceived> = ChunkReceived::class.java

    private val tracker = UploadStatusTracker()

    override fun onSnapshot(currentData: Map<ChunkKey, ChunkReceived>) {
        tracker.clear()
        currentData.forEach { tracker.add(it.value) }
    }

    override fun onNext(
        newRecord: Record<ChunkKey, ChunkReceived>,
        oldValue: ChunkReceived?,
        currentData: Map<ChunkKey, ChunkReceived>
    ) {
        if (newRecord.value != null) {
            val newValue = newRecord.value!!
            val newKey = newRecord.key
            if (newKey.requestId != newValue.requestId || newKey.partNumber != newValue.partNumber) {
                // This is meant to catch badly written tests.  In 'prod' code we set the key from the value in [ChunkWriteToDbProcessor]
                throw CordaRuntimeException("Mismatched requestid and partnumber in upload processor onNext:" +
                        " key(${newKey.requestId}, ${newKey.partNumber}) - value (${newValue.requestId}, ${newValue.partNumber})")
            }
            tracker.add(newValue)
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
    fun status(requestId: RequestId): Pair<UploadStatus, ExceptionEnvelope?> {
        log.debug("Getting status for $requestId")
        val status = tracker.status(requestId)
        val exceptionEnvelope = tracker.exceptionEnvelope(requestId)
        return Pair(status, exceptionEnvelope)
    }
}
