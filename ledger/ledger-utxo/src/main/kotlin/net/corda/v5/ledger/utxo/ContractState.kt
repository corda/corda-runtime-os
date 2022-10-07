package net.corda.v5.ledger.utxo

import net.corda.v5.base.annotations.CordaSerializable
import java.security.PublicKey

/**
 * Defines a contract state.
 *
 * A contract state (or just "state") contains opaque data used by a contract program. It can be thought of as a disk
 * file that the program can use to persist data across transactions.
 *
 * States are immutable. Once created they are never updated, instead, any changes must generate a new successor state.
 * States can be updated (consumed) only once. The notary is responsible for ensuring there is no "double spending" by
 * only signing a transaction if the input states are all free.
 *
 * @property participants The public keys of any participants associated with the current contract state.
 */
@CordaSerializable
interface ContractState {
    val participants: List<PublicKey>
}
