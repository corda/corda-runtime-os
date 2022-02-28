package net.corda.cpk.write.impl.services.kafka.impl

import net.corda.cpk.write.impl.services.kafka.CpkChunksPublisher
import net.corda.data.chunking.Chunk
import net.corda.data.chunking.CpkChunkId
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import java.time.Duration

class KafkaCpkChunksPublisher(
    private val publisher: Publisher,
    private val timeout: Duration,
    private val topicName: String
) : CpkChunksPublisher {
    companion object {
        val logger = contextLogger()
    }

    override fun put(cpkChunkId: CpkChunkId, cpkChunk: Chunk) {
        logger.debug { "Putting CPK chunk cpkChecksum= ${cpkChunkId.cpkChecksum} partNumber= ${cpkChunkId.cpkChunkPartNumber}" }
        val cpkChunksRecord = Record(topicName, cpkChunkId, cpkChunk)
        putAllAndWaitForResponses(cpkChunksRecord)
    }

    private fun putAllAndWaitForResponses(cpkChunksRecord: Record<CpkChunkId, Chunk>) {
        val response = publisher.publish(listOf(cpkChunksRecord)).single()

        var intermittentException: Boolean
        do {
            try {
                response.getOrThrow(timeout)
                intermittentException = false
            } catch (e: CordaMessageAPIIntermittentException) {
                intermittentException = true
                logger.info("Caught a CordaMessageAPIIntermittentException. Will retry waiting on future response.")
            }
        } while (intermittentException)
    }
}