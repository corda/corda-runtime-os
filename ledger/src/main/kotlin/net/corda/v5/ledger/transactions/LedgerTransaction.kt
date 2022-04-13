package net.corda.v5.ledger.transactions

import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.util.castIfPossible
import net.corda.v5.base.util.uncheckedCast
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.contracts.Attachment
import net.corda.v5.ledger.contracts.Command
import net.corda.v5.ledger.contracts.CommandData
import net.corda.v5.ledger.contracts.ContractState
import net.corda.v5.ledger.contracts.StateAndRef
import net.corda.v5.ledger.contracts.TimeWindow
import net.corda.v5.ledger.contracts.TransactionState
import net.corda.v5.ledger.contracts.TransactionVerificationException
import net.corda.v5.ledger.identity.Party
import net.corda.v5.membership.GroupParameters
import java.util.function.Predicate

/**
 * A LedgerTransaction is derived from a [WireTransaction]. It is the result of doing the following operations:
 *
 * - Downloading and locally storing all the dependencies of the transaction.
 * - Resolving the input states and loading them into memory.
 * - Deserialising the output states.
 *
 * All the above refer to inputs using a (txhash, output index) pair.
 *
 * Usage notes:
 *
 * [LedgerTransaction] is an abstraction that is meant to be used during the transaction verification stage.
 * It needs full access to input states that might be in transactions that are encrypted and unavailable for code running outside the secure enclave.
 * Also, it might need to deserialize states with code that might not be available on the classpath.
 *
 * Because of this, trying to create or use a [LedgerTransaction] for any other purpose then transaction verification can result in unexpected exceptions,
 * which need de be handled.
 *
 * [LedgerTransaction]s should never be instantiated directly from client code, but rather via WireTransaction.toLedgerTransaction
 */
@DoNotImplement
@Suppress("LongParameterList")
interface LedgerTransaction : FullTransaction {

    /** The outputs created by the transaction. */
    override val outputs: List<TransactionState<*>>
    val inputStates: List<ContractState>
    val referenceStates: List<ContractState>
    val commands: List<Command<CommandData>>
    val attachments: List<Attachment>
    val timeWindow: TimeWindow?

    /**
     * A set of related inputs and outputs that are connected by some common attributes. An InOutGroup is calculated
     * using [groupStates] and is useful for handling cases where a transaction may contain similar but unrelated
     * state evolutions, for example, a transaction that moves cash in two different currencies. The numbers must add
     * up on both sides of the transaction, but the values must be summed independently per currency. Grouping can
     * be used to simplify this logic.
     */
    data class InOutGroup<out T : ContractState, out K : Any>(val inputs: List<T>, val outputs: List<T>, val groupingKey: K)

    /**
     * Verifies this transaction and runs contract code. At this stage it is assumed that signatures have already been verified.
     *
     * The contract verification logic is run in a custom classloader created for the current transaction.
     * This classloader is only used during verification and does not leak to the client code.
     *
     * The reason for this is that classes (contract states) deserialized in this classloader would actually be a different type from what
     * the calling code would expect.
     *
     * @throws TransactionVerificationException if anything goes wrong.
     */
    fun verify()

    /**
     * Returns the typed input StateAndRef at the specified index
     * @param index The index into the inputs.
     * @return The [StateAndRef]
     */
    fun <T : ContractState> inRef(index: Int): StateAndRef<T> {
        return uncheckedCast(inputs[index])
    }

    /**
     * Given a type and a function that returns a grouping key, associates inputs and outputs together so that they
     * can be processed as one. The grouping key is any arbitrary object that can act as a map key (so must implement
     * equals and hashCode).
     *
     * The purpose of this function is to simplify the writing of verification logic for transactions that may contain
     * similar but unrelated state evolutions which need to be checked independently. Consider a transaction that
     * simultaneously moves both dollars and euros (e.g. is an atomic FX trade). There may be multiple dollar inputs and
     * multiple dollar outputs, depending on things like how fragmented the owner's vault is and whether various privacy
     * techniques are in use. The quantity of dollars on the output side must sum to the same as on the input side, to
     * ensure no money is being lost track of. This summation and checking must be repeated independently for each
     * currency. To solve this, you would use groupStates with a type of Cash.State and a selector that returns the
     * currency field: the resulting list can then be iterated over to perform the per-currency calculation.
     */
    fun <T : ContractState, K : Any> groupStates(ofType: Class<T>, selector: (T) -> K): List<InOutGroup<T, K>> {
        val inputs = inputsOfType(ofType)
        val outputs = outputsOfType(ofType)

        val inGroups: Map<K, List<T>> = inputs.groupBy(selector)
        val outGroups: Map<K, List<T>> = outputs.groupBy(selector)

        val result = ArrayList<InOutGroup<T, K>>()

        for ((k, v) in inGroups.entries)
            result.add(InOutGroup(v, outGroups[k] ?: emptyList(), k))
        for ((k, v) in outGroups.entries) {
            if (inGroups[k] == null)
                result.add(InOutGroup(emptyList(), v, k))
        }

        return result
    }

    /** Utilities for contract writers to incorporate into their logic. */

    /**
     * Helper to simplify getting an indexed input [ContractState].
     * @param index the position of the item in the inputs.
     * @return The [StateAndRef] at the requested index
     */
    fun getInput(index: Int): ContractState {
        return inputs[index].state.data
    }

    /**
     * Helper to simplify getting an indexed reference input [ContractState].
     * @param index the position of the item in the references.
     * @return The [StateAndRef] at the requested index.
     */
    fun getReferenceInput(index: Int): ContractState {
        return references[index].state.data
    }

    /**
     * Helper to simplify getting all inputs states of a particular class, interface, or base class.
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * [clazz] must be an extension of [ContractState].
     * @return the possibly empty list of inputs matching the clazz restriction.
     */
    fun <T : ContractState> inputsOfType(clazz: Class<T>): List<T> {
        return inputs.mapNotNull { clazz.castIfPossible(it.state.data) }
    }

    /**
     * Helper to simplify getting all reference input states of a particular class, interface, or base class.
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * [clazz] must be an extension of [ContractState].
     * @return the possibly empty list of inputs matching the clazz restriction.
     */
    fun <T : ContractState> referenceInputsOfType(clazz: Class<T>): List<T> {
        return references.mapNotNull { clazz.castIfPossible(it.state.data) }
    }

    /**
     * Helper to simplify getting all inputs states of a particular class, interface, or base class.
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * [clazz] must be an extension of [ContractState].
     * @return the possibly empty list of inputs [StateAndRef] matching the clazz restriction.
     */
    fun <T : ContractState> inRefsOfType(clazz: Class<T>): List<StateAndRef<T>> {
        return inputs.mapNotNull {
            if (clazz.isInstance(it.state.data)) {
                uncheckedCast<StateAndRef<ContractState>, StateAndRef<T>>(it)
            } else {
                null
            }
        }
    }

    /**
     * Helper to simplify getting all reference input states of a particular class, interface, or base class.
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * [clazz] must be an extension of [ContractState].
     * @return the possibly empty list of reference inputs [StateAndRef] matching the clazz restriction.
     */
    fun <T : ContractState> referenceInputRefsOfType(clazz: Class<T>): List<StateAndRef<T>> {
        return references.mapNotNull {
            if (clazz.isInstance(it.state.data)) {
                uncheckedCast<StateAndRef<ContractState>, StateAndRef<T>>(it)
            } else {
                null
            }
        }
    }

    /**
     * Helper to simplify filtering inputs according to a [Predicate].
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * [clazz] must be an extension of [ContractState].
     * @param predicate A filtering function taking a state of type T and returning true if it should be included in the list.
     * The class filtering is applied before the predicate.
     * @return the possibly empty list of input states matching the predicate and clazz restrictions.
     */
    fun <T : ContractState> filterInputs(clazz: Class<T>, predicate: Predicate<T>): List<T> {
        return inputsOfType(clazz).filter { predicate.test(it) }
    }

    /**
     * Helper to simplify filtering reference inputs according to a [Predicate].
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * [clazz] must be an extension of [ContractState].
     * @param predicate A filtering function taking a state of type T and returning true if it should be included in the list.
     * The class filtering is applied before the predicate.
     * @return the possibly empty list of reference states matching the predicate and clazz restrictions.
     */
    fun <T : ContractState> filterReferenceInputs(clazz: Class<T>, predicate: Predicate<T>): List<T> {
        return referenceInputsOfType(clazz).filter { predicate.test(it) }
    }

    /**
     * Helper to simplify filtering inputs according to a [Predicate].
     * @param predicate A filtering function taking a state of type T and returning true if it should be included in the list.
     * The class filtering is applied before the predicate.
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * [clazz] must be an extension of [ContractState].
     * @return the possibly empty list of inputs [StateAndRef] matching the predicate and clazz restrictions.
     */
    fun <T : ContractState> filterInRefs(clazz: Class<T>, predicate: Predicate<T>): List<StateAndRef<T>> {
        return inRefsOfType(clazz).filter { predicate.test(it.state.data) }
    }

    /**
     * Helper to simplify filtering reference inputs according to a [Predicate].
     * @param predicate A filtering function taking a state of type T and returning true if it should be included in the list.
     * The class filtering is applied before the predicate.
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * [clazz] must be an extension of [ContractState].
     * @return the possibly empty list of references [StateAndRef] matching the predicate and clazz restrictions.
     */
    fun <T : ContractState> filterReferenceInputRefs(clazz: Class<T>, predicate: Predicate<T>): List<StateAndRef<T>> {
        return referenceInputRefsOfType(clazz).filter { predicate.test(it.state.data) }
    }

    /**
     * Helper to simplify finding a single input [ContractState] matching a [Predicate].
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * [clazz] must be an extension of ContractState.
     * @param predicate A filtering function taking a state of type T and returning true if this is the desired item.
     * The class filtering is applied before the predicate.
     * @return the single item matching the predicate.
     * @throws IllegalArgumentException if no item, or multiple items are found matching the requirements.
     */
    fun <T : ContractState> findInput(clazz: Class<T>, predicate: Predicate<T>): T {
        return inputsOfType(clazz).single { predicate.test(it) }
    }

    /**
     * Helper to simplify finding a single reference inputs [ContractState] matching a [Predicate].
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * [clazz] must be an extension of ContractState.
     * @param predicate A filtering function taking a state of type T and returning true if this is the desired item.
     * The class filtering is applied before the predicate.
     * @return the single item matching the predicate.
     * @throws IllegalArgumentException if no item, or multiple items are found matching the requirements.
     */
    fun <T : ContractState> findReference(clazz: Class<T>, predicate: Predicate<T>): T {
        return referenceInputsOfType(clazz).single { predicate.test(it) }
    }

    /**
     * Helper to simplify finding a single input matching a [Predicate].
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * [clazz] must be an extension of ContractState.
     * @param predicate A filtering function taking a state of type T and returning true if this is the desired item.
     * The class filtering is applied before the predicate.
     * @return the single item matching the predicate.
     * @throws IllegalArgumentException if no item, or multiple items are found matching the requirements.
     */
    fun <T : ContractState> findInRef(clazz: Class<T>, predicate: Predicate<T>): StateAndRef<T> {
        return inRefsOfType(clazz).single { predicate.test(it.state.data) }
    }

    /**
     * Helper to simplify finding a single reference input matching a [Predicate].
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * [clazz] must be an extension of ContractState.
     * @param predicate A filtering function taking a state of type T and returning true if this is the desired item.
     * The class filtering is applied before the predicate.
     * @return the single item matching the predicate.
     * @throws IllegalArgumentException if no item, or multiple items are found matching the requirements.
     */
    fun <T : ContractState> findReferenceInputRef(clazz: Class<T>, predicate: Predicate<T>): StateAndRef<T> {
        return referenceInputRefsOfType(clazz).single { predicate.test(it.state.data) }
    }

    /**
     * Helper to simplify getting an indexed command.
     * @param index the position of the item in the commands.
     * @return The Command at the requested index
     */
    fun <T : CommandData> getCommand(index: Int): Command<T> {
        return Command(uncheckedCast(commands[index].value), commands[index].signers)
    }

    /**
     * Helper to simplify getting all [Command] items with a [CommandData] of a particular class, interface, or base class.
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * [clazz] must be an extension of [CommandData].
     * @return the possibly empty list of commands with [CommandData] values matching the clazz restriction.
     */
    fun <T : CommandData> commandsOfType(clazz: Class<T>): List<Command<T>> {
        return commands.mapNotNull { (value, signers) -> clazz.castIfPossible(value)?.let { Command(it, signers) } }
    }

    /**
     * Helper to simplify filtering [Command] items according to a [Predicate].
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * [clazz] must be an extension of [CommandData].
     * @param predicate A filtering function taking a [CommandData] item of type T and returning true if it should be included in the list.
     * The class filtering is applied before the predicate.
     * @return the possibly empty list of [Command] items with [CommandData] values matching the predicate and clazz restrictions.
     */
    fun <T : CommandData> filterCommands(clazz: Class<T>, predicate: Predicate<T>): List<Command<T>> {
        return commandsOfType(clazz).filter { predicate.test(it.value) }
    }

    /**
     * Helper to simplify finding a single [Command] items according to a [Predicate].
     * @param clazz The class type used for filtering via an [Class.isInstance] check.
     * [clazz] must be an extension of [CommandData].
     * @param predicate A filtering function taking a [CommandData] item of type T and returning true if it should be included in the list.
     * The class filtering is applied before the predicate.
     * @return the [Command] item with [CommandData] values matching the predicate and clazz restrictions.
     * @throws IllegalArgumentException if no items, or multiple items matched the requirements.
     */
    fun <T : CommandData> findCommand(clazz: Class<T>, predicate: Predicate<T>): Command<T> {
        return commandsOfType(clazz).single { predicate.test(it.value) }
    }

    /**
     * Helper to simplify getting an indexed attachment.
     * @param index the position of the item in the attachments.
     * @return The Attachment at the requested index.
     */
    fun getAttachment(index: Int): Attachment {
        return attachments[index]
    }

    /**
     * Helper to simplify getting an indexed attachment.
     * @param id the SecureHash of the desired attachment.
     * @return The Attachment with the matching id.
     * @throws IllegalArgumentException if no item matches the id.
     */
    fun getAttachment(id: SecureHash): Attachment {
        return attachments.first { it.id == id }
    }

    operator fun component1(): List<StateAndRef<ContractState>>
    operator fun component2(): List<TransactionState<ContractState>>
    operator fun component3(): List<Command<CommandData>>
    operator fun component4(): List<Attachment>
    operator fun component5(): SecureHash
    operator fun component6(): Party?
    operator fun component7(): TimeWindow?
    operator fun component8(): PrivacySalt
    operator fun component9(): GroupParameters
    operator fun component10(): List<StateAndRef<ContractState>>
}

inline fun <reified T : ContractState, K : Any> LedgerTransaction.groupStates(noinline selector: (T) -> K): List<LedgerTransaction.InOutGroup<T, K>> {
    return groupStates(T::class.java, selector)
}

inline fun <reified T : ContractState> LedgerTransaction.inputsOfType(): List<T> = inputsOfType(T::class.java)

inline fun <reified T : ContractState> LedgerTransaction.referenceInputsOfType(): List<T> = referenceInputsOfType(T::class.java)

inline fun <reified T : ContractState> LedgerTransaction.inRefsOfType(): List<StateAndRef<T>> = inRefsOfType(T::class.java)

inline fun <reified T : ContractState> LedgerTransaction.referenceInputRefsOfType(): List<StateAndRef<T>> =
    referenceInputRefsOfType(T::class.java)

inline fun <reified T : ContractState> LedgerTransaction.filterInputs(crossinline predicate: (T) -> Boolean): List<T> {
    return filterInputs(T::class.java) { predicate(it) }
}

inline fun <reified T : ContractState> LedgerTransaction.filterReferenceInputs(crossinline predicate: (T) -> Boolean): List<T> {
    return filterReferenceInputs(T::class.java) { predicate(it) }
}

inline fun <reified T : ContractState> LedgerTransaction.filterInRefs(crossinline predicate: (T) -> Boolean): List<StateAndRef<T>> {
    return filterInRefs(T::class.java) { predicate(it) }
}

inline fun <reified T : ContractState> LedgerTransaction.filterReferenceInputRefs(crossinline predicate: (T) -> Boolean): List<StateAndRef<T>> {
    return filterReferenceInputRefs(T::class.java) { predicate(it) }
}

inline fun <reified T : ContractState> LedgerTransaction.findInput(crossinline predicate: (T) -> Boolean): T {
    return findInput(T::class.java) { predicate(it) }
}

inline fun <reified T : ContractState> LedgerTransaction.indReference(crossinline predicate: (T) -> Boolean): T {
    return findReference(T::class.java) { predicate(it) }
}

inline fun <reified T : ContractState> LedgerTransaction.findInRef(crossinline predicate: (T) -> Boolean): StateAndRef<T> {
    return findInRef(T::class.java) { predicate(it) }
}

inline fun <reified T : ContractState> LedgerTransaction.findReferenceInputRef(crossinline predicate: (T) -> Boolean): StateAndRef<T> {
    return findReferenceInputRef(T::class.java) { predicate(it) }
}

inline fun <reified T : CommandData> LedgerTransaction.commandsOfType(): List<Command<T>> = commandsOfType(T::class.java)

inline fun <reified T : CommandData> LedgerTransaction.filterCommands(crossinline predicate: (T) -> Boolean): List<Command<T>> {
    return filterCommands(T::class.java) { predicate(it) }
}

inline fun <reified T : CommandData> LedgerTransaction.findCommand(crossinline predicate: (T) -> Boolean): Command<T> {
    return findCommand(T::class.java) { predicate(it) }
}
