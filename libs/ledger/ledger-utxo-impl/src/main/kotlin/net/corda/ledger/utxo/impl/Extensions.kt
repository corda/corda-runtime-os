package net.corda.ledger.utxo.impl

import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.ContractState

/**
 * Gets the class name of the contract associated with the current state, or null if the class name cannot be inferred.
 *
 * @return Returns the class name of the contract associated with the current state, or null if the class name cannot be inferred.
 */
fun ContractState.getContractClassName(): String? {
    val annotation = javaClass.getAnnotation(BelongsToContract::class.java)

    if (annotation != null) {
        return annotation.value.java.canonicalName
    }

    val enclosingClass = javaClass.enclosingClass

    if (enclosingClass != null && Contract::class.java.isAssignableFrom(enclosingClass)) {
        return enclosingClass.canonicalName
    }

    return null
}

/**
 * Gets the class name of the contract associated with the current state.
 *
 * @return Returns the class name of the contract associated with the current state.
 * @throws IllegalArgumentException if the contract class name could not be inferred from the current contract state.
 */
fun ContractState.getContractClassNameOrThrow(): String = requireNotNull(getContractClassName()) {
    """
        Unable to infer Contract class name because state class ${javaClass.canonicalName} is not annotated with
        @BelongsToContract, and does not have an enclosing class which implements Contract. Either annotate ${javaClass.canonicalName}
        with @BelongsToContract, or supply an explicit contract parameter to addOutputState().
        """.trimIndent().replace('\n', ' ')
}
