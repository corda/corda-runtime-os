package net.corda.cpk.write.impl.services.kafka.impl

import net.corda.cpk.write.impl.CpkChunk
import net.corda.cpk.write.impl.services.kafka.AvroTypesTodo
import net.corda.cpk.write.impl.services.kafka.CpkChunkWriter
import net.corda.cpk.write.impl.services.kafka.toAvro
import net.corda.cpk.write.impl.services.kafka.toCpkChunkAvro
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.util.contextLogger
import java.time.Duration

class KafkaCpkChunkWriter(
    private val publisher: Publisher,
    private val timeout: Duration
) : CpkChunkWriter {
    companion object {
        val logger = contextLogger()
    }

    // This needs to be transactional i.e. write all of cpk chunks to Kafka or nothing
    override fun putAll(cpkChunks: List<CpkChunk>) {
        val cpkChunksRecords =
            cpkChunks.map { cpkChunk ->
                Record("TODO", cpkChunk.id.toAvro(), cpkChunk.bytes.toCpkChunkAvro(cpkChunk.id))
            }

        putAllAndWaitForResponses(cpkChunksRecords)
    }

    private fun putAllAndWaitForResponses(cpkChunksRecords: List<Record<AvroTypesTodo.CpkChunkIdAvro, AvroTypesTodo.CpkChunkAvro>>) {
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