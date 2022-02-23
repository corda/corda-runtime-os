package net.corda.cpk.write.impl.services.kafka.impl

import net.corda.cpk.write.impl.services.kafka.AvroTypesTodo
import net.corda.cpk.write.impl.services.kafka.CpkChunksPublisher
import net.corda.data.chunking.Chunk
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.util.contextLogger
import java.time.Duration

class KafkaCpkChunksPublisher(
    private val publisher: Publisher,
    private val timeout: Duration
) : CpkChunksPublisher {
    companion object {
        val logger = contextLogger()
    }

    // This needs to be transactional i.e. write all of cpk chunks to Kafka or nothing
    override fun put(cpkIdToCpkChunk: Pair<AvroTypesTodo.CpkChunkIdAvro, Chunk>) {
        val cpkChunkId = cpkIdToCpkChunk.first
        val cpkChunk = cpkIdToCpkChunk.second
        val cpkChunksRecord = Record("TODO", cpkChunkId, cpkChunk)
        putAllAndWaitForResponses(cpkChunksRecord)
    }

    private fun putAllAndWaitForResponses(cpkChunksRecord: Record<AvroTypesTodo.CpkChunkIdAvro, Chunk>) {
        val response = publisher.publish(listOf(cpkChunksRecord)).single()

        var intermittentException: Boolean
        do {
            try {
                response.getOrThrow(timeout)
                intermittentException = false
            } catch (e: CordaMessageAPIIntermittentException) {
                intermittentException = true
                logger.info("Caught an CordaMessageAPIIntermittentException. Will retry waiting on a future response.")
            }
        } while (intermittentException)
    }
}