package net.corda.cpk.read.impl.services

import net.corda.data.chunking.Chunk
import net.corda.data.chunking.CpkChunkId
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record

// Needs to take the cache directory
class CpkChunksKafkaReader : CompactedProcessor<CpkChunkId, Chunk> {
    override val keyClass: Class<CpkChunkId>
        get() = CpkChunkId::class.java
    override val valueClass: Class<Chunk>
        get() = Chunk::class.java

    override fun onSnapshot(currentData: Map<CpkChunkId, Chunk>) {

    }

    override fun onNext(newRecord: Record<CpkChunkId, Chunk>, oldValue: Chunk?, currentData: Map<CpkChunkId, Chunk>) {
    }
}