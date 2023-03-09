package net.corda.chunking.db.impl.persistence

import net.corda.crypto.core.toAvro
import net.corda.data.ExceptionEnvelope
import net.corda.data.chunking.UploadStatus
import net.corda.data.chunking.UploadStatusKey
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.v5.crypto.SecureHash
import org.slf4j.LoggerFactory

class StatusPublisher(private val statusTopic: String, private val publisher: Publisher) {
    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val sequenceNumberByRequestId = mutableMapOf<String, Int>()

    private fun nextSequenceNumber(requestId: String): Int {
        val next = sequenceNumberByRequestId.computeIfAbsent(requestId) { -1 } + 1
        sequenceNumberByRequestId[requestId] = next
        return next
    }

    private fun publish(sequenceNumber: Int, records: List<Record<UploadStatusKey, UploadStatus>>) {
        val requestId = records.first().key.requestId
        val message = records.first().value!!.message
        log.debug("Publishing ChunkStatus $sequenceNumber $requestId $message")
        publisher.publish(records)
    }

    /**
     * Initial update might be sent from 1 or *more* db processors and should always be "in progress",
     * and will always be the '0th' sequence number so we can overwrite it from multiple publishers
     * without affecting the CompactedQueue *snapshot*
     */
    fun initialStatus(requestId: String) {
        val sequenceNumber = 0
        publish(
            sequenceNumber, listOf(
                Record(
                    statusTopic,
                    UploadStatusKey(requestId, sequenceNumber),
                    UploadStatus(false, "Uploading", null, null)
                )
            )
        )
    }

    /**
     * Any intermediate update calculated after an upload is complete (i.e. all chunks are received).
     * We expect this method to be called be exactly 1 db processor.
     */
    fun update(requestId: String, message: String) {
        val sequenceNumber = nextSequenceNumber(requestId)
        publish(
            sequenceNumber,
            listOf(
                Record(
                    statusTopic,
                    UploadStatusKey(requestId, sequenceNumber),
                    UploadStatus(false, message, null, null)
                )
            )
        )
    }

    /**
     * Final update calculated after an upload is complete (i.e. all chunks are received).
     * We expect this method to be called be exactly 1 db processor.
     */
    fun complete(requestId: String, checksum: SecureHash) {
        val sequenceNumber = nextSequenceNumber(requestId)
        publish(
            sequenceNumber,
            listOf(
                Record(
                    statusTopic,
                    UploadStatusKey(requestId, sequenceNumber),
                    UploadStatus(true, "OK", checksum.toAvro(), null)
                )
            )
        )
    }

    /**
     * Any error is published here.
     *
     * @param requestId the request id being processed
     * @param exceptionEnvelope any exception thrown (non-null)
     * @param message some informational message
     */
    fun error(requestId: String, exceptionEnvelope: ExceptionEnvelope, message: String) {
        val sequenceNumber = nextSequenceNumber(requestId)

        publish(
            sequenceNumber,
            listOf(
                Record(
                    statusTopic,
                    UploadStatusKey(requestId, sequenceNumber),
                    UploadStatus(false, message, null, exceptionEnvelope)
                )
            )
        )
    }
}
