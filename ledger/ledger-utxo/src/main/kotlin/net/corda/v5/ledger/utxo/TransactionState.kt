package net.corda.v5.ledger.utxo

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.ledger.common.transaction.Party

/**
 * Defines a transaction state, composed of a [ContractState] and associated transaction state information.
 *
 * @param T The underlying type of the [ContractState] instance.
 * @property contractState The [ContractState] component of the current [TransactionState] instance.
 * @property contractType The
 */
@DoNotImplement
@CordaSerializable
interface TransactionState<out T : ContractState> {
    val contractState: T
    val contractType: Class<out Contract>
    val notary: Party
    val encumbrance: Int?
}