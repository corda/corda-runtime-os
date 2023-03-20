package net.corda.ledger.utxo.data.state

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.EncumbranceGroup
import net.corda.v5.ledger.utxo.TransactionState

/**
 * Represents a transaction state, composed of a [ContractState] and associated information.
 */
@CordaSerializable
data class TransactionStateImpl<out T : ContractState>(
    private val contractState: T,
    private val notary: Party,
    private val encumbranceGroup: EncumbranceGroup?,
) : TransactionState<@UnsafeVariance T> {

    override fun getContractState(): T {
        return contractState
    }

    override fun getContractStateType(): Class<@UnsafeVariance T> {
        return contractState.javaClass
    }

    override fun getContractType(): Class<out Contract> {
        return contractState.getContractClassOrThrow()
    }

    override fun getNotary(): Party {
        return notary
    }

    override fun getEncumbranceGroup(): EncumbranceGroup? {
        return encumbranceGroup
    }
}
