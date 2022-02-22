package net.corda.cpk.write.impl.services.kafka

import net.corda.lifecycle.Lifecycle
import net.corda.v5.crypto.SecureHash

// TODO Maybe we need to allow cache entries invalidation to allow re-writing a Kafka record?
/**
 * Cache holding CPK checksums.
 */
interface CpkChecksumsCache : Lifecycle {
    fun contains(cpkChecksum: SecureHash): Boolean

    fun add(cpkChecksum: SecureHash)
}