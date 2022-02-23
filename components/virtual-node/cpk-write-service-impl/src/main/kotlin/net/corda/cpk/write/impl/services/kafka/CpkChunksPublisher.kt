package net.corda.cpk.write.impl.services.kafka

import net.corda.data.chunking.Chunk

interface CpkChunksPublisher {
    fun put(idToCpkChunk: Pair<AvroTypesTodo.CpkChunkIdAvro, Chunk>)
}