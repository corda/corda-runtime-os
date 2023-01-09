package net.corda.v5.application.uniqueness.model

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.SecureHash

/**
 * Representation of a state reference. This type might also be attached to some of the error types
 * and returned through the client service. This representation does not depend on any specific ledger
 * model and is agnostic to both the message bus API and any DB schema that may be used to persist data
 * by the backing store.
 *
 * Please note that this representation of a state ref is entirely different from the Ledger specific
 * [StateRef][net.corda.v5.ledger.utxo.StateRef] class. This class represents a state ref that the
 * uniqueness checker will process and may differ from the UTXO ledger data model.
 */
@CordaSerializable
interface UniquenessCheckStateRef {
    val txHash: SecureHash
    val stateIndex: Int
}
