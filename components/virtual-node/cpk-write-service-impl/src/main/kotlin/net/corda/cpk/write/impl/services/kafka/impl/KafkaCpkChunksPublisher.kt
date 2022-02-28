package net.corda.cpk.write.impl.services.kafka.impl

import net.corda.chunking.toCorda
import net.corda.cpk.write.impl.services.kafka.CpkChunksPublisher
import net.corda.data.chunking.Chunk
import net.corda.data.chunking.CpkChunkId
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

        private fun CpkChunkId.toIdString() =
            "cpkChecksum= ${cpkChecksum.toCorda()} partNumber= $cpkChunkPartNumber"
    }

    override fun put(cpkChunkId: CpkChunkId, cpkChunk: Chunk) {
        logger.debug { "Putting CPK chunk ${cpkChunkId.toIdString()}" }
        val cpkChunksRecord = Record(topicName, cpkChunkId, cpkChunk)
        val responses = publisher.publish(listOf(cpkChunksRecord))

        // responses should be on size 1
        responses.forEach {
            it.getOrThrow(timeout)
        }
    }
}