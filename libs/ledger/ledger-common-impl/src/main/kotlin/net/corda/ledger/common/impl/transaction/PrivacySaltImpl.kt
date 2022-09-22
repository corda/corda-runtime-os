package net.corda.ledger.common.impl.transaction

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.types.OpaqueBytes
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.PrivacySalt

/**
 * A privacy salt is required to compute nonces per transaction component in order to ensure that an adversary cannot
 * use brute force techniques and reveal the content of a Merkle-leaf hashed value.
 * Because this salt serves the role of the seed to compute nonces, its size and entropy should be equal to the
 * underlying hash function used for Merkle tree generation, currently [SecureHash.SHA256], which has an output of 32 bytes.
 * The salt value needs to be pre-generated (agreed between transacting parties), but it is highlighted that one should always ensure
 * it has sufficient entropy.
 */
@CordaSerializable
class PrivacySaltImpl(bytes: ByteArray) : OpaqueBytes(bytes), PrivacySalt {

    init {
        require(bytes.any { it != 0.toByte() }) { "Privacy salt should not be all zeros." }
        require(bytes.size >= MINIMUM_SIZE) { "Privacy salt should be at least $MINIMUM_SIZE bytes." }
    }

    companion object {
        private const val MINIMUM_SIZE = 32
    }

    override fun equals(other: Any?): Boolean =
        other === this ||
            other is PrivacySaltImpl &&
            other.bytes contentEquals bytes

    override fun hashCode(): Int = bytes.hashCode()
}