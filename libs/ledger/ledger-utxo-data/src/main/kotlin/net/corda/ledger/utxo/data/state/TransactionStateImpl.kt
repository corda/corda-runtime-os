package net.corda.ledger.utxo.data.state

import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.EncumbranceGroup
import net.corda.v5.ledger.utxo.TransactionState

/**
 * Represents a transaction state, composed of a [ContractState] and associated information.
 */
data class TransactionStateImpl<out T : ContractState>(
    override val contractState: T,
    override val notary: Party,
    override val encumbrance: EncumbranceGroup?,
) : TransactionState<T> {
    override val contractType: Class<out Contract> get() = contractState.getContractClassOrThrow()
    override val contractStateType: Class<out T> get() = contractState.javaClass
}
