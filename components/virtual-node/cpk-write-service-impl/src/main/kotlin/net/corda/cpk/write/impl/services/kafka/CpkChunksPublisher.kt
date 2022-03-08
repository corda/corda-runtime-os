package net.corda.cpk.write.impl.services.kafka

import net.corda.data.chunking.Chunk
import net.corda.data.chunking.CpkChunkId

interface CpkChunksPublisher : AutoCloseable {
    fun put(cpkChunkId: CpkChunkId, cpkChunk: Chunk)
}