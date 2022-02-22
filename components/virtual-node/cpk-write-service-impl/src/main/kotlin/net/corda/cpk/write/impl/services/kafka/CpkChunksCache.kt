package net.corda.cpk.write.impl.services.kafka

import net.corda.cpk.write.impl.CpkChunkId
import net.corda.lifecycle.Lifecycle

// TODO Maybe we need to allow cache entries invalidation to allow re-writing a Kafka record?
/**
 * CPK chunks cache holding [CpkChunkId]s.
 */
interface CpkChunksCache : Lifecycle {
    fun contains(checksum: CpkChunkId): Boolean

    fun add(cpkChunkId: CpkChunkId)
}