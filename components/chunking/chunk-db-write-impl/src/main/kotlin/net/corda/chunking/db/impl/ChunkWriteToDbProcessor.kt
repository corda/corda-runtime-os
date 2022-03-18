package net.corda.chunking.db.impl

import net.corda.chunking.RequestId
import net.corda.chunking.db.impl.persistence.ChunkPersistence
import net.corda.chunking.db.impl.validation.CpiValidator
import net.corda.chunking.db.impl.validation.ValidationException
import net.corda.data.ExceptionEnvelope
import net.corda.data.chunking.Chunk
import net.corda.data.chunking.ChunkAckKey
import net.corda.data.chunking.ChunkAck
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.contextLogger

/**
 * Persist a [Chunk] to the database and send an [ChunkAck] message
 */
class ChunkWriteToDbProcessor(
    private val statusTopic: String,
    private val persistence: ChunkPersistence,
    private val validator: CpiValidator
) : DurableProcessor<RequestId, Chunk> {
    companion object {
        private val log = contextLogger()
    }

    private fun processChunk(request: Chunk): ChunkAck {
        log.debug("Processing chunk request id=${request.requestId} part=${request.partNumber}")

        val isLastChunk = (request.data.limit() == 0)

        try {
            val allChunksReceived = persistence.persistChunk(request)

            if (allChunksReceived == AllChunksReceived.NO) {
                return ChunkAck(isLastChunk, null)
            }

            if (!persistence.checksumIsValid(request.requestId)) {
                throw ValidationException("Checksum of CPI for ${request.requestId} does not match")
            }

            // We validate the CPI, persist it to the database, and publish CPI info.
            // Exceptions are used to communicate failure.
            // If we fail for any reason, we never send an OK message.
            validator.validate(request.requestId)
        } catch (e: Exception) {
            log.error("Could not persist chunk $request", e)

            return ChunkAck(isLastChunk, ExceptionEnvelope(e::class.java.name, e.message))
        }

        return ChunkAck( isLastChunk, null)
    }

    override fun onNext(events: List<Record<RequestId, Chunk>>): List<Record<*, *>> {
        return events.map {
            val chunk = it.value!!
            val chunkAck = processChunk(chunk)
            // We need a unique key for each request AND part number.
            // If we use request id only, we end up overwriting messages.
            Record(statusTopic, ChunkAckKey(chunk.requestId, chunk.partNumber), chunkAck)
        }
    }

    override val keyClass: Class<String> = String::class.java

    override val valueClass: Class<Chunk> = Chunk::class.java
}
