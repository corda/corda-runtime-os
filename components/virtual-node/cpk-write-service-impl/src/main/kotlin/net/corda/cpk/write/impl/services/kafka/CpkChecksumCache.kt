package net.corda.cpk.write.impl.services.kafka

import net.corda.cpk.write.impl.CpkChunkId
import net.corda.lifecycle.Lifecycle
import net.corda.v5.crypto.SecureHash

// TODO Maybe we need to allow cache entries invalidation to allow re-writing a Kafka record?
/**
 * CPK chunks cache holding CPK checksums.
 */
interface CpkChecksumCache : Lifecycle {
    fun contains(cpkChecksum: SecureHash): Boolean

    fun add(cpkChecksum: SecureHash)
}