package net.corda.cpk.write.impl.services.kafka.impl

import net.corda.crypto.core.toCorda
import net.corda.cpk.write.impl.services.kafka.CpkChunksPublisher
import net.corda.data.chunking.Chunk
import net.corda.data.chunking.CpkChunkId
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.utilities.concurrent.getOrThrow
import net.corda.utilities.debug
import org.slf4j.LoggerFactory
import java.time.Duration

class KafkaCpkChunksPublisher(
    private val publisher: Publisher,
    private val timeout: Duration,
    private val topicName: String
) : CpkChunksPublisher {
    companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun put(cpkChunkId: CpkChunkId, cpkChunk: Chunk) {
        logger.debug {
            "Putting CPK chunk cpkChecksum: ${cpkChunkId.cpkChecksum.toCorda()} partNumber: ${cpkChunkId.cpkChunkPartNumber}"
        }
        val cpkChunksRecord = Record(topicName, cpkChunkId, cpkChunk)
        val responses = publisher.publish(listOf(cpkChunksRecord))

        // responses should be on size 1
        responses.forEach {
            it.getOrThrow(timeout)
        }
    }

    override fun close() {
        publisher.close()
    }
}