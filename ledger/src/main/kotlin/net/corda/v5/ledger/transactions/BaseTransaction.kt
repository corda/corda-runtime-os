package net.corda.v5.ledger.transactions

import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.util.castIfPossible
import net.corda.v5.base.util.uncheckedCast
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.contracts.ClassInfo
import net.corda.v5.ledger.contracts.ContractState
import net.corda.v5.ledger.contracts.ContractStateData
import net.corda.v5.ledger.contracts.PackageIdWithDependencies
import net.corda.v5.ledger.contracts.StateAndRef
import net.corda.v5.ledger.contracts.StateInfo
import net.corda.v5.ledger.contracts.StateRef
import net.corda.v5.ledger.contracts.TransactionState
import net.corda.v5.ledger.identity.Party
import java.util.function.Predicate

/**
 * An abstract class defining fields shared by all transaction types in the system.
 */
@DoNotImplement
interface BaseTransaction {
    /** A list of reusable reference data states which can be referred to by other contracts in this transaction. */
    val references: List<*>
    /** The inputs of this transaction. Note that in BaseTransaction subclasses the type of this list may change! */
    val inputs: List<*>
    /** Ordered list of states defined by this transaction. */
    val outputs: List<*>
    /**
     * If present, the notary for this transaction. If absent then the transaction is not notarised at all.
     * This is intended for issuance/genesis transactions that don't consume any other states and thus can't
     * double spend anything.
     */
    val notary: Party?

    val transactionParameters: List<Pair<String, String>>

    val packages: List<PackageIdWithDependencies>

    val inputsMetaData: List<ClassInfo>

    val outputsData: List<StateInfo>

    val commandsMetaData: List<ClassInfo>

    val referencesMetaData: List<ClassInfo>

    val id: SecureHash

    /**
     * Check invariant properties of the class.
     */
    fun checkBaseInvariants()

    /**
     * Returns a [StateAndRef] for the given output index.
     */
    fun <T : ContractState> outRef(index: Int): StateAndRef<T> {
        val contractStateData = ContractStateData(asContractState(outputs[index]))
        val contractStateInfo = outputsData[index]
        val txnState = TransactionState<T>( uncheckedCast(contractStateData.data), contractStateInfo.contract, contractStateInfo.notary,
                contractStateInfo.encumbrance, contractStateInfo.constraint )
        return StateAndRef(txnState, StateRef(id, index))
    }

    /**
     * Returns a [StateAndRef] for the requested output state, or throws [IllegalArgumentException] if not found.
     */
    fun <T : ContractState> outRef(state: ContractState): StateAndRef<T> {
        val i = outputStates.indexOf(state)
        require(i != -1) { "No such element" }
        return outRef(i)
    }

    /**
     * Helper property to return a list of [ContractState] objects, rather than the often less convenient [TransactionState]
     */
    val outputStates: List<ContractState> get() = outputs.map {asContractState(it)}

    fun asContractState(output: Any?): ContractState {
        return when(output) {
            is ContractStateData<*> -> output.data
            is TransactionState<*> -> output.data
            else -> throw IllegalStateException("Unknown state encountered")
        }
    }

    /**
     * Helper to simplify getting an indexed output.
     * @param index the position of the item in the output.
     * @return The ContractState at the requested index
     */
    fun getOutput(index: Int): ContractState = asContractState(outputs[index])

    /**
     * Helper to simplify getting all output states of a particular class, interface, or base class.
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * Clazz must be an extension of [ContractState].
     * @return the possibly empty list of output states matching the clazz restriction.
     */
    fun <T : ContractState> outputsOfType(clazz: Class<T>): List<T> = outputs.mapNotNull { clazz.castIfPossible(asContractState(it)) }

    /**
     * Helper to simplify filtering outputs according to a [Predicate].
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * Clazz must be an extension of [ContractState].
     * @param predicate A filtering function taking a state of type T and returning true if it should be included in the list.
     * The class filtering is applied before the predicate.
     * @return the possibly empty list of output states matching the predicate and clazz restrictions.
     */
    fun <T : ContractState> filterOutputs(clazz: Class<T>, predicate: Predicate<T>): List<T> {
        return outputsOfType(clazz).filter { predicate.test(it) }
    }

    /**
     * Helper to simplify finding a single output matching a [Predicate].
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * Clazz must be an extension of [ContractState].
     * @param predicate A filtering function taking a state of type T and returning true if this is the desired item.
     * The class filtering is applied before the predicate.
     * @return the single item matching the predicate.
     * @throws IllegalArgumentException if no item, or multiple items are found matching the requirements.
     */
    fun <T : ContractState> findOutput(clazz: Class<T>, predicate: Predicate<T>): T {
        return outputsOfType(clazz).single { predicate.test(it) }
    }

    /**
     * Helper to simplify getting all output [StateAndRef] items of a particular state class, interface, or base class.
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * Clazz must be an extension of [ContractState].
     * @return the possibly empty list of output [StateAndRef<T>] states matching the clazz restriction.
     */
    fun <T : ContractState> outRefsOfType(clazz: Class<T>): List<StateAndRef<T>> {
        val stateAndData = outputs.zip(outputsData)
        val txnStates = stateAndData.map { pair ->
            TransactionState(asContractState(pair.first), pair.second.contract, pair.second.notary, pair.second.encumbrance, pair.second.constraint)
        }
        return txnStates.mapIndexedNotNull { index, state ->
            clazz.castIfPossible(state.data)?.let { StateAndRef<T>(uncheckedCast(state), StateRef(id, index)) }
        }
    }

    /**
     * Helper to simplify filtering output [StateAndRef] items according to a [Predicate].
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * Clazz must be an extension of [ContractState].
     * @param predicate A filtering function taking a state of type T and returning true if it should be included in the list.
     * The class filtering is applied before the predicate.
     * @return the possibly empty list of output [StateAndRef] states matching the predicate and clazz restrictions.
     */
    fun <T : ContractState> filterOutRefs(clazz: Class<T>, predicate: Predicate<T>): List<StateAndRef<T>> {
        return outRefsOfType(clazz).filter { predicate.test(it.state.data) }
    }

    /**
     * Helper to simplify finding a single output [StateAndRef] matching a [Predicate].
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * Clazz must be an extension of [ContractState].
     * @param predicate A filtering function taking a state of type T and returning true if this is the desired item.
     * The class filtering is applied before the predicate.
     * @return the single [StateAndRef] item matching the predicate.
     * @throws IllegalArgumentException if no item, or multiple items are found matching the requirements.
     */
    fun <T : ContractState> findOutRef(clazz: Class<T>, predicate: Predicate<T>): StateAndRef<T> {
        return outRefsOfType(clazz).single { predicate.test(it.state.data) }
    }
}

inline fun <reified T : ContractState> BaseTransaction.filterOutputs(crossinline predicate: (T) -> Boolean): List<T> {
    return filterOutputs(T::class.java) { predicate(it) }
}

inline fun <reified T : ContractState> BaseTransaction.outputsOfType(): List<T> = outputsOfType(T::class.java)

inline fun <reified T : ContractState> BaseTransaction.findOutput(crossinline predicate: (T) -> Boolean): T {
    return findOutput(T::class.java) { predicate(it) }
}

inline fun <reified T : ContractState> BaseTransaction.outRefsOfType(): List<StateAndRef<T>> = outRefsOfType(T::class.java)

inline fun <reified T : ContractState> BaseTransaction.filterOutRefs(crossinline predicate: (T) -> Boolean): List<StateAndRef<T>> {
    return filterOutRefs(T::class.java) { predicate(it) }
}

inline fun <reified T : ContractState> BaseTransaction.findOutRef(crossinline predicate: (T) -> Boolean): StateAndRef<T> {
    return findOutRef(T::class.java) { predicate(it) }
}
