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
    override fun putAll(cpkChunks: List<Pair<AvroTypesTodo.CpkChunkIdAvro, Chunk>>) {
        val cpkChunksRecords =
            cpkChunks.map {
                val cpkChunkId = it.first
                val cpkChunk = it.second
                Record("TODO", cpkChunkId, cpkChunk)
            }

        putAllAndWaitForResponses(cpkChunksRecords)
    }

    private fun putAllAndWaitForResponses(cpkChunksRecords: List<Record<AvroTypesTodo.CpkChunkIdAvro, Chunk>>) {
        val responses = publisher.publish(cpkChunksRecords)

        responses.forEach {
            var intermittentException: Boolean
            do {
                try {
                    it.getOrThrow(timeout)
                    intermittentException = false
                } catch (e: CordaMessageAPIIntermittentException) {
                    intermittentException = true
                    logger.info("Caught an CordaMessageAPIIntermittentException. Will retry waiting on a future response.")
                }
            } while (intermittentException)
        }
    }
}