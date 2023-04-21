package net.corda.simulator.runtime.ledger.utxo

import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.EncumbranceGroup
import net.corda.v5.ledger.utxo.TransactionState
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.BelongsToContract
import java.security.PublicKey

/**
 * Represents a transaction state, composed of a [ContractState] and associated information.
 */
data class SimTransactionState<out T : ContractState>(
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

/**
 * Gets the [Contract] class associated with the current [ContractState], or null if the class cannot be inferred.
 *
 * @return Returns the [Contract] class associated with the current [ContractState], or null if the class cannot be inferred.
 */
private fun ContractState.getContractClass(): Class<out Contract>? {
    val annotation = javaClass.getAnnotation(BelongsToContract::class.java)

    if (annotation != null) {
        return annotation.value.java
    }

    val enclosingClass = javaClass.enclosingClass

    if (enclosingClass != null && Contract::class.java.isAssignableFrom(enclosingClass)) {
        @Suppress("unchecked_cast")
        return enclosingClass as Class<out Contract>
    }
    return null
}

private fun ContractState.getContractClassOrThrow(): Class<out Contract> {
    return requireNotNull(getContractClass()) {
        """Unable to infer Contract class. ${javaClass.canonicalName} is not annotated with @BelongsToContract, 
            |or does not have an enclosing class which implements Contract.""".trimMargin()
    }
}