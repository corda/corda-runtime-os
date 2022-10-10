package net.corda.v5.ledger.utxo

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.annotations.DoNotImplement

/**
 * Represents a composition of a [TransactionState] and a [StateRef].
 *
 * @property state The [TransactionState] component of the current [StateAndRef].
 * @property ref The [StateRef] component of the current [StateAndRef].
 */
@DoNotImplement
@CordaSerializable
interface StateAndRef<out T : ContractState> {
    val state: TransactionState<T>
    val ref: StateRef
}
