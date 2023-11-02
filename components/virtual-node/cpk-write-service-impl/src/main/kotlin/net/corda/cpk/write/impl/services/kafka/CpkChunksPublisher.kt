package net.corda.cpk.write.impl.services.kafka

import net.corda.data.chunking.Chunk
import net.corda.data.chunking.CpkChunkId
import net.corda.libs.configuration.SmartConfig

interface CpkChunksPublisher : AutoCloseable {
    fun put(cpkChunkId: CpkChunkId, cpkChunk: Chunk)

    fun updatePublisherConfig(configuration: SmartConfig)
}