package net.corda.v5.ledger.utxo

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.SecureHash

/**
 * Defines a reference to a [ContractState].
 *
 * @property index The index of the state in the transaction's outputs in which the referenced state was created.
 * @property transactionHash The hash of the transaction in which the referenced state was created.
 */
@CordaSerializable
class StateRef (
    val transactionHash: SecureHash,
    val index: Int
)
