package net.corda.v5.ledger.utxo

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.ledger.common.Party

/**
 * Defines a transaction state, composed of a [ContractState] and associated transaction state information.
 *
 * @param T The underlying type of the [ContractState] instance.
 * @property contractState The [ContractState] of the current [TransactionState] instance.
 * @property contractStateType The [ContractState] type of the current [TransactionState] instance.
 * @property contractType The [Contract] type of the current [TransactionState] instance.
 * @property notary The notary of the current [TransactionState] instance.
 * @property encumbrance The encumbrance of the current [TransactionState] instance.
 */
@DoNotImplement
@CordaSerializable
interface TransactionState<out T : ContractState> {
    val contractState: T
    val contractStateType: Class<out T>
    val contractType: Class<out Contract>
    val notary: Party
    val encumbrance: EncumbranceGroup?
}
