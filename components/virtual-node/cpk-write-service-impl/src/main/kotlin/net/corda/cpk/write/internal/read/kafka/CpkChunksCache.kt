package net.corda.cpk.write.internal.read.kafka

import net.corda.cpk.write.types.CpkChunkId

interface CpkChunksCache {
    fun contains(checksum: CpkChunkId): Boolean
}