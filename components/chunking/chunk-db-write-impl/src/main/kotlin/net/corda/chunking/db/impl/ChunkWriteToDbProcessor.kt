package net.corda.chunking.db.impl

import net.corda.chunking.RequestId
import net.corda.chunking.db.impl.persistence.ChunkPersistence
import net.corda.chunking.db.impl.validation.CpiValidator
import net.corda.data.chunking.Chunk
import net.corda.libs.cpiupload.ValidationException
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.contextLogger

/**
 * Persist a [Chunk] to the database and let the validator know what's going on
 */
class ChunkWriteToDbProcessor(
    private val persistence: ChunkPersistence,
    private val validator: CpiValidator
) : DurableProcessor<RequestId, Chunk> {
    companion object {
        private val log = contextLogger()
    }

    private fun processChunk(request: Chunk) {
        log.debug("Processing chunk request id=${request.requestId} part=${request.partNumber} offset=${request.offset}")

        try {
            validator.notifyChunkReceived(request.requestId)

            val allChunksReceived = persistence.persistChunk(request)

            if (allChunksReceived == AllChunksReceived.NO) return

            if (!persistence.checksumIsValid(request.requestId)) {
                throw ValidationException("Checksum of CPI for ${request.requestId} does not match")
            }

            // We validate the CPI, persist it to the database, and publish CPI info.
            // Exceptions are used to communicate failure.
            // If we fail for any reason, we never send an OK message.
            validator.validate(request.requestId)
        } catch (e: Exception) {
            log.error("Could not persist chunk $request", e)
            validator.notifyChunkError(request.requestId, e)
        }
    }

    override fun onNext(events: List<Record<RequestId, Chunk>>): List<Record<*, *>> {
        // We're not publishing using onNext - we're using the publisher directly
        // because we can publish many-to-1 status messages.
        events.forEach { processChunk(it.value!!) }
        return emptyList()
    }

    override val keyClass: Class<String> = String::class.java

    override val valueClass: Class<Chunk> = Chunk::class.java
}
