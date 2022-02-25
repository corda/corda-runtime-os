package net.corda.cpk.write.impl.services.kafka

import net.corda.data.chunking.Chunk
import net.corda.data.chunking.CpkChunkId

interface CpkChunksPublisher {
    fun put(cpkChunkId: CpkChunkId, cpkChunk: Chunk)
}