package net.corda.libs.cpiupload.impl

import net.corda.chunking.RequestId
import net.corda.data.ExceptionEnvelope
import net.corda.data.chunking.ChunkAckKey
import net.corda.data.chunking.ChunkAck
import net.corda.libs.cpiupload.CpiUploadManager
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.contextLogger

/**
 * Receives [ChunkAck] messages on a compacted queue from the db-processor / [net.corda.chunking.db.impl.ChunkWriteToDbProcessor]
 *
 * Key must match [ChunkWriteToDbProcessor], and that's just a string.  It is the request id *and* part number
 */
@Suppress("UNUSED")
class UploadStatusProcessor : CompactedProcessor<ChunkAckKey, ChunkAck> {
    companion object {
        private val log = contextLogger()
        private val emptyExceptionEnvelope = ExceptionEnvelope("", "")
    }

    override val keyClass: Class<ChunkAckKey> = ChunkAckKey::class.java

    override val valueClass: Class<ChunkAck> = ChunkAck::class.java

    private val tracker = UploadStatusTracker()

    override fun onSnapshot(currentData: Map<ChunkAckKey, ChunkAck>) {
        tracker.clear()
        currentData.forEach { tracker.add(it.key, it.value) }
    }

    override fun onNext(
        newRecord: Record<ChunkAckKey, ChunkAck>,
        oldValue: ChunkAck?,
        currentData: Map<ChunkAckKey, ChunkAck>
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
    fun status(requestId: RequestId): Pair<CpiUploadManager.UploadStatus, ExceptionEnvelope?> {
        log.debug("Getting status for $requestId")
        val status = tracker.status(requestId)
        val exceptionEnvelope = tracker.exceptionEnvelope(requestId)
        return Pair(status, exceptionEnvelope)
    }
}
