package net.corda.chunking.db.impl

import net.corda.data.ExceptionEnvelope
import net.corda.data.chunking.Chunk
import net.corda.data.chunking.ChunkAck
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.v5.base.util.contextLogger
import java.util.concurrent.CompletableFuture

internal class ChunkWriteToDbProcessor(
    private val publisher: Publisher,
    private val queries: ChunkDbQueries
) : RPCResponderProcessor<Chunk, ChunkAck> {
    companion object {
        private val log = contextLogger()
        private val emptyExceptionEnvelope = ExceptionEnvelope("", "")
    }

    private enum class Status {
        VALID,
        INVALID,
        CHUNKS_MISSING,
        IGNORE
    }

    override fun onNext(request: Chunk, respFuture: CompletableFuture<ChunkAck>) {
        log.debug(
            "Processing chunk id = ${request.requestId} / filename = ${request.fileName} " +
                    "/ part = ${request.partNumber}"
        )

        var exceptionEnvelope = emptyExceptionEnvelope
        var successfullyPersistedChunk = false
        var binaryComplete = AllChunksReceived.NO

        try {
            binaryComplete = queries.persist(request)
            successfullyPersistedChunk = true
        } catch (e: Exception) {
            // If we fail to publish one chunk, we cannot easily recover so need to return some error.
            log.error(
                "Could not persist chunk id = ${request.requestId} / filename = ${request.fileName} " +
                        "/ part = ${request.partNumber}",
                e
            )
            exceptionEnvelope = ExceptionEnvelope(e::class.java.name, e.message)
        }

        publishAckResponseToKafka(request, respFuture, exceptionEnvelope, successfullyPersistedChunk)

        if (!successfullyPersistedChunk) {
            sendFailStatus(request)
        } else {
            validateBinary(request, binaryComplete)
        }
    }

    private fun sendFailStatus(request: Chunk) {
        log.error("One or more chunks could not be persisted - please check previous log messages")
        // we're done and broken.  The ack was sent with 'fail', but we need to do something else
        // the next PR where we switch to DurableQueues will change this code so not worth addressing
        sendStatusMessage(Status.CHUNKS_MISSING, request.requestId, request.fileName)
    }

    /** Checks the checksum and "other things" tbd. */
    private fun validateBinary(request: Chunk, allChunksReceived: AllChunksReceived) {
        val status = validateAllChunks(request.requestId, allChunksReceived)
        when (status) {
            Status.VALID -> validateCpi(request.requestId, request.fileName)
            Status.INVALID -> sendStatusMessage(status, request.requestId, request.fileName)
            else -> Unit // do nothing
        }
    }

    private fun validateAllChunks(requestId: String, allChunksReceived: AllChunksReceived): Status {
        log.debug("Validating chunks for $requestId")

        // Haven't received all the chunks yet?  That's ok, just return
        if (allChunksReceived == AllChunksReceived.NO) return Status.IGNORE

        // Check the checksum of the bytes match.  Further validation will occur after this
        // such as CPI unzipping, signature checks etc.
        if (!queries.checksumIsValid(requestId)) return Status.VALID

        return Status.INVALID
    }

    /**
     * We really do NOT want this method here - this service is generic for any binary of any type
     * we should pass the message on, but leave this here for this PR to show intent, and follow up
     * in next PR.
     */
    private fun validateCpi(requestId: String, fileName: String) {
        // Pass on the chunks to the next step in the next PR.
        // do something - assemble, unzip, check signatures etc.

        sendStatusMessage(Status.VALID, requestId, fileName)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun sendStatusMessage(status: Status, requestId: String, fileName: String) {
        log.warn("Not implemented in this PR")
    }

    private fun publishAckResponseToKafka(
        request: Chunk,
        respFuture: CompletableFuture<ChunkAck>,
        exceptionEnvelope: ExceptionEnvelope,
        success: Boolean
    ) {
        val chunkAck = ChunkAck(request.requestId, request.partNumber, success, exceptionEnvelope)
        publisher.publish(listOf(Record(Schemas.VirtualNode.CPI_UPLOAD_TOPIC, request.requestId, chunkAck)))
        respFuture.complete(chunkAck)
    }
}
