package net.corda.v5.ledger.obsolete.transactions

import net.corda.v5.ledger.obsolete.contracts.BelongsToContract
import net.corda.v5.ledger.obsolete.contracts.Contract
import net.corda.v5.ledger.obsolete.contracts.ContractState

/**
 * Default contract argument on TransactionBuilder interface.
 */
fun requireNotNullContractClassName(state: ContractState) = requireNotNull(state.requiredContractClassName) {
    // TODO: add link to docsite page, when there is one.
    """
        Unable to infer Contract class name because state class ${state::class.java.name} is not annotated with
        @BelongsToContract, and does not have an enclosing class which implements Contract. Either annotate ${state::class.java.name}
        with @BelongsToContract, or supply an explicit contract parameter to addOutputState().
        """.trimIndent().replace('\n', ' ')
}

/**
 * Obtain the typename of the required [ContractClass] associated with the target [ContractState], using the
 * [BelongsToContract] annotation by default, but falling through to checking the state's enclosing class if there is
 * one and it inherits from [Contract].
 */
val ContractState.requiredContractClassName: String? get() {
    val annotation = javaClass.getAnnotation(BelongsToContract::class.java)
    if (annotation != null) {
        return annotation.value.java.typeName
    }
    val enclosingClass = javaClass.enclosingClass ?: return null
    return if (Contract::class.java.isAssignableFrom(enclosingClass)) enclosingClass.typeName else null
}

/**
 * The maximum number of keys in a signature constraint that the platform supports.
 *
 * Attention: this value affects consensus, so it requires a minimum platform version bump in order to be changed.
 */
const val MAX_NUMBER_OF_KEYS_IN_SIGNATURE_CONSTRAINT = 20

/**
 * Contract version and flow versions are integers.
 */
typealias Version = Int
