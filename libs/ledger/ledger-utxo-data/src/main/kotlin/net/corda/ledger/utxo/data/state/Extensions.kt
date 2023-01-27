package net.corda.ledger.utxo.data.state

import net.corda.ledger.utxo.data.transaction.UtxoOutputInfoComponent
import net.corda.v5.base.util.uncheckedCast
import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.EncumbranceGroup
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.TransactionState

/**
 * Casts the current [ContractState] to the specified type.
 *
 * @param T The underlying type of the [ContractState].
 * @param type The type of the [ContractState] to cast.
 * @return Returns a new [ContractState] instance cast to the specified type.
 * @throws IllegalArgumentException if the current [ContractState] cannot be cast to the specified type.
 */
fun <T : ContractState> ContractState.cast(type: Class<T>): T {
    return if (!javaClass.isAssignableFrom(type)) {
        throw IllegalArgumentException("ContractState of type ${javaClass.canonicalName} cannot be cast to type ${type.canonicalName}.")
    } else type.cast(this)
}

/**
 * Casts the current [TransactionState] to the specified type.
 *
 * @param T The underlying type of the [ContractState].
 * @param type The type of the [ContractState] to cast to.
 * @return Returns a new [TransactionState] instance cast to the specified type.
 * @throws IllegalArgumentException if the current [TransactionState] cannot be cast to the specified type.
 */
fun <T : ContractState> TransactionState<*>.cast(type: Class<T>): TransactionState<T> {
    return TransactionStateImpl(contractState.cast(type), notary, encumbrance)
}

/**
 * Casts the current [StateAndRef] to the specified type.
 *
 * @param T The underlying type of the [ContractState].
 * @param type The type of the [ContractState] to cast to.
 * @return Returns a new [StateAndRef] instance cast to the specified type.
 * @throws IllegalArgumentException if the current [StateAndRef] cannot be cast to the specified type.
 */
fun <T : ContractState> StateAndRef<*>.cast(type: Class<T>): StateAndRef<T> {
    return StateAndRefImpl(state.cast(type), ref)
}

/**
 * Filters a collection of [StateAndRef] and returns only the elements that match the specified [ContractState] type.
 *
 * @param T The underlying type of the [ContractState].
 * @param type The type of the [ContractState] to cast to.
 * @return Returns a collection of [StateAndRef] that match the specified [ContractState] type.
 */
fun <T : ContractState> Iterable<StateAndRef<*>>.filterIsContractStateInstance(type: Class<T>): List<StateAndRef<T>> {
    return filter { it.state.contractState.javaClass.isAssignableFrom(type) }.map { it.cast(type) }
}

/**
 * Gets the [Contract] class associated with the current [ContractState], or null if the class cannot be inferred.
 *
 * @return Returns the [Contract] class associated with the current [ContractState], or null if the class cannot be inferred.
 */
fun ContractState.getContractClass(): Class<out Contract>? {
    val annotation = javaClass.getAnnotation(BelongsToContract::class.java)

    if (annotation != null) {
        return annotation.value.java
    }

    val enclosingClass = javaClass.enclosingClass

    if (enclosingClass != null && Contract::class.java.isAssignableFrom(enclosingClass)) {
        return uncheckedCast(enclosingClass)
    }
    return null
}

fun ContractState.getContractClassOrThrow(): Class<out Contract> {
    return requireNotNull(getContractClass()) {
        """Unable to infer Contract class. ${javaClass.canonicalName} is not annotated with @BelongsToContract, 
            |or does not have an enclosing class which implements Contract.""".trimMargin()
    }
}

fun UtxoOutputInfoComponent.getEncumbranceGroup(): EncumbranceGroup? {
     return encumbrance?.let{
        require(encumbranceGroupSize != null)
        EncumbranceGroupImpl(encumbranceGroupSize, encumbrance)
    }
}