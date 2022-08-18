package net.corda.v5.ledger.common.transaction

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.annotations.DoNotImplement

/**
 * A privacy salt is required to compute nonces per transaction component in order to ensure that an adversary cannot
 * use brute force techniques and reveal the content of a Merkle-leaf hashed value.
 */
@CordaSerializable
@DoNotImplement
interface PrivacySalt{
    /**
     * @property bytes Privacy salt value.
     */
    val bytes: ByteArray
}
