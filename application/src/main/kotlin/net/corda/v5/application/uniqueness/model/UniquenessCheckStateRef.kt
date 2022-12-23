package net.corda.v5.application.uniqueness.model

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.SecureHash

/**
 * Representation of a state reference. This type might also be attached to some of the error types
 * and returned through the client service. This representation does not depend on any specific ledger
 * model and is agnostic to both the message bus API and any DB schema that may be used to persist data
 * by the backing store.
 *
 * T0DO CORE-6629: This class can be removed once the UTXO ledger model gets merged
 */
@CordaSerializable
interface UniquenessCheckStateRef {
    val txHash: SecureHash
    val stateIndex: Int
}
