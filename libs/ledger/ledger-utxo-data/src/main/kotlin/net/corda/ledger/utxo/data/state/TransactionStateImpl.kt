package net.corda.ledger.utxo.data.state

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.EncumbranceGroup
import net.corda.v5.ledger.utxo.TransactionState
import java.security.PublicKey

/**
 * Represents a transaction state, composed of a [ContractState] and associated information.
 */
@CordaSerializable
data class TransactionStateImpl<out T : ContractState>(
    private val contractState: T,
    private val notaryName: MemberX500Name,
    private val notaryKey: PublicKey,
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

    override fun getNotaryName(): MemberX500Name {
        return notaryName
    }

    override fun getNotaryKey(): PublicKey {
        return notaryKey
    }

    override fun getEncumbranceGroup(): EncumbranceGroup? {
        return encumbranceGroup
    }
}
