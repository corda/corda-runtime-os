package net.corda.cpk.write.impl.services.kafka

import net.corda.data.chunking.Chunk

interface CpkChunksWriter {
    fun putAll(cpkChunks: List<Pair<AvroTypesTodo.CpkChunkIdAvro, Chunk>>)
}