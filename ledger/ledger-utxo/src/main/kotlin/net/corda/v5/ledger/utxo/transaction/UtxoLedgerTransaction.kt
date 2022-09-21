package net.corda.v5.ledger.utxo.transaction

import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.TransactionVerificationException
import net.corda.v5.ledger.utxo.*

/**
 * Represents a UTXO ledger transaction.
 *
 * @property timeWindow The time window associated with the current [UtxoLedgerTransaction].
 * @property attachments The attachments associated with the current [UtxoLedgerTransaction].
 * @property commands The commands and signatories associated with the current [UtxoLedgerTransaction].
 * @property inputStateAndRefs The input states and state references to be consumed by the current [UtxoLedgerTransaction].
 * @property inputTransactionStates The input transaction states to be consumed by the current [UtxoLedgerTransaction].
 * @property inputContractStates The input contract states to be consumed by the current [UtxoLedgerTransaction].
 * @property referenceInputStateAndRefs The input states and state referenced to be referenced by the current [UtxoLedgerTransaction].
 * @property referenceInputTransactionStates The input transaction states to be referenced by the current [UtxoLedgerTransaction].
 * @property referenceInputContractStates The input contract states to be referenced by the current [UtxoLedgerTransaction].
 * @property outputStateAndRefs The output states and state references to be created by the current [UtxoLedgerTransaction].
 * @property outputTransactionStates The output transaction states to be created by the current [UtxoLedgerTransaction].
 * @property outputContractStates The output contract states to be created by the current [UtxoLedgerTransaction].
 */
@DoNotImplement
@Suppress("TooManyFunctions")
interface UtxoLedgerTransaction {

    val timeWindow: TimeWindow?
    val attachments: List<Attachment>
    val commands: List<CommandAndSignatories<*>>

    val inputStateAndRefs: List<StateAndRef<*>>
    val inputTransactionStates: List<TransactionState<*>> get() = inputStateAndRefs.map { it.state }
    val inputContractStates: List<ContractState> get() = inputStateAndRefs.map { it.state.contractState }

    val referenceInputStateAndRefs: List<StateAndRef<*>>
    val referenceInputTransactionStates: List<TransactionState<*>> get() = referenceInputStateAndRefs.map { it.state }
    val referenceInputContractStates: List<ContractState> get() = referenceInputTransactionStates.map { it.contractState }

    val outputStateAndRefs: List<StateAndRef<*>>
    val outputTransactionStates: List<TransactionState<*>> get() = outputStateAndRefs.map { it.state }
    val outputContractStates: List<ContractState> get() = outputTransactionStates.map { it.contractState }

    /**
     * Verifies the current [UtxoLedgerTransaction].
     *
     * @throws TransactionVerificationException if the current [UtxoLedgerTransaction] fails to verify correctly.
     */
    fun verify()

    // region Attachments

    /**
     * Obtains the ledger transaction [Attachment] with the specified id.
     *
     * @param id The id of the ledger transaction [Attachment] to obtain.
     * @return Returns the ledger transaction [Attachment] with the specified id.
     * @throws IllegalArgumentException if the ledger transaction [Attachment] with the specified id cannot be found.
     */
    fun getAttachment(id: SecureHash): Attachment

    // endregion

    // region Commands

    /**
     * Obtains all ledger transaction [CommandAndSignatories] that match the specified [Command] type.
     *
     * @param T The underlying type of the [Command].
     * @param type The type of the [Command].
     * @return Returns all ledger transaction [CommandAndSignatories] that match the specified [Command] type.
     */
    fun <T : Command> getCommandsAndSignatories(type: Class<T>): List<CommandAndSignatories<T>>

    /**
     * Obtains a single ledger transaction [CommandAndSignatories] that matches the specified [Command] type.
     *
     * @param T The underlying type of the [Command].
     * @param type The type of the [Command].
     * @return Returns a single ledger transaction [CommandAndSignatories] that matches the specified [Command] type.
     * @throws IllegalArgumentException if a single ledger transaction [CommandAndSignatories] that matches the specified [Command] type cannot be found.
     */
    fun <T : Command> getCommandAndSignatories(type: Class<T>): CommandAndSignatories<T>

    // endregion

    // region Input States

    /**
     * Obtains all ledger transaction [StateAndRef] inputs that match the specified [ContractState] type.
     *
     * @param T The underlying type of the [ContractState].
     * @param type The type of the [ContractState].
     * @return Returns all ledger transaction [StateAndRef] inputs that match the specified [ContractState] type.
     */
    fun <T : ContractState> getInputStateAndRefs(type: Class<T>): List<StateAndRef<T>>

    /**
     * Obtains a single ledger transaction [StateAndRef] input that match the specified [ContractState] type.
     *
     * @param T The underlying type of the [ContractState].
     * @param type The type of the [ContractState].
     * @return Returns a single ledger transaction [StateAndRef] input that match the specified [ContractState] type.
     * @throws IllegalArgumentException if a single ledger transaction [StateAndRef] input that matches the specified [ContractState] type cannot be found.
     */
    fun <T : ContractState> getInputStateAndRef(type: Class<T>): StateAndRef<T>

    /**
     * Obtains all ledger transaction [ContractState] inputs that match the specified [ContractState] type.
     *
     * @param T The underlying type of the [ContractState].
     * @param type The type of the [ContractState].
     * @return Returns all ledger transaction [ContractState] inputs that match the specified [ContractState] type.
     */
    fun <T : ContractState> getInputStates(type: Class<T>): List<T>

    /**
     * Obtains a single ledger transaction [ContractState] input that match the specified [ContractState] type.
     *
     * @param T The underlying type of the [ContractState].
     * @param type The type of the [ContractState].
     * @return Returns a single ledger transaction [ContractState] input that match the specified [ContractState] type.
     * @throws IllegalArgumentException if a single ledger transaction [ContractState] input that matches the specified [ContractState] type cannot be found.
     */
    fun <T : ContractState> getInputState(type: Class<T>): T

    // endregion

    // region Reference Input States

    /**
     * Obtains all ledger transaction [StateAndRef] reference inputs that match the specified [ContractState] type.
     *
     * @param T The underlying type of the [ContractState].
     * @param type The type of the [ContractState].
     * @return Returns all ledger transaction [StateAndRef] reference inputs that match the specified [ContractState] type.
     */
    fun <T : ContractState> getReferenceInputStateAndRefs(type: Class<T>): List<StateAndRef<T>>

    /**
     * Obtains a single ledger transaction [StateAndRef] reference input that match the specified [ContractState] type.
     *
     * @param T The underlying type of the [ContractState].
     * @param type The type of the [ContractState].
     * @return Returns a single ledger transaction [StateAndRef] reference input that match the specified [ContractState] type.
     * @throws IllegalArgumentException if a single ledger transaction [StateAndRef] reference input that matches the specified [ContractState] type cannot be found.
     */
    fun <T : ContractState> getReferenceInputStateAndRef(type: Class<T>): StateAndRef<T>

    /**
     * Obtains all ledger transaction [ContractState] reference inputs that match the specified [ContractState] type.
     *
     * @param T The underlying type of the [ContractState].
     * @param type The type of the [ContractState].
     * @return Returns all ledger transaction [ContractState] reference inputs that match the specified [ContractState] type.
     */
    fun <T : ContractState> getReferenceInputStates(type: Class<T>): List<T>

    /**
     * Obtains a single ledger transaction [ContractState] reference input that match the specified [ContractState] type.
     *
     * @param T The underlying type of the [ContractState].
     * @param type The type of the [ContractState].
     * @return Returns a single ledger transaction [ContractState] reference input that match the specified [ContractState] type.
     * @throws IllegalArgumentException if a single ledger transaction [ContractState] reference input that matches the specified [ContractState] type cannot be found.
     */
    fun <T : ContractState> getReferenceInputState(type: Class<T>): T

    // endregion

    // region Output States

    /**
     * Obtains all ledger transaction [StateAndRef] outputs that match the specified [ContractState] type.
     *
     * @param T The underlying type of the [ContractState].
     * @param type The type of the [ContractState].
     * @return Returns all ledger transaction [StateAndRef] outputs that match the specified [ContractState] type.
     */
    fun <T : ContractState> getOutputStateAndRefs(type: Class<T>): List<StateAndRef<T>>

    /**
     * Obtains a single ledger transaction [StateAndRef] output that match the specified [ContractState] type.
     *
     * @param T The underlying type of the [ContractState].
     * @param type The type of the [ContractState].
     * @return Returns a single ledger transaction [StateAndRef] output that match the specified [ContractState] type.
     * @throws IllegalArgumentException if a single ledger transaction [StateAndRef] output that matches the specified [ContractState] type cannot be found.
     */
    fun <T : ContractState> getOutputStateAndRef(type: Class<T>): StateAndRef<T>

    /**
     * Obtains all ledger transaction [ContractState] outputs that match the specified [ContractState] type.
     *
     * @param T The underlying type of the [ContractState].
     * @param type The type of the [ContractState].
     * @return Returns all ledger transaction [ContractState] outputs that match the specified [ContractState] type.
     */
    fun <T : ContractState> getOutputStates(type: Class<T>): List<T>

    /**
     * Obtains a single ledger transaction [ContractState] output that match the specified [ContractState] type.
     *
     * @param T The underlying type of the [ContractState].
     * @param type The type of the [ContractState].
     * @return Returns a single ledger transaction [ContractState] output that match the specified [ContractState] type.
     * @throws IllegalArgumentException if a single ledger transaction [ContractState] output that matches the specified [ContractState] type cannot be found.
     */
    fun <T : ContractState> getOutputState(type: Class<T>): T

    // endregion

    // region Grouped States

    /**
     * Obtains groups of ledger transaction [StateAndRef] inputs and outputs that match the specified [ContractState] type, grouped by the specified selector key.
     *
     * @param T The underlying type of the [ContractState].
     * @param K The underlying type of the selector key.
     * @param type The type of the [ContractState].
     * @param selector The selector that will be common to all grouped [StateAndRef] inputs and outputs.
     * @return Returns groups of ledger transaction [StateAndRef] inputs and outputs that match the specified [ContractState] type, grouped by the specified selector key.
     */
    fun <T : ContractState, K : Any> getGroupedStates(type: Class<T>, selector: (StateAndRef<T>) -> K): List<InputOutputGroup<T, K>>

    // endregion
}

// region Input States

/**
 * Obtains all ledger transaction [StateAndRef] inputs that match the specified [ContractState] type.
 *
 * @param T The underlying type of the [ContractState].
 * @return Returns all ledger transaction [StateAndRef] inputs that match the specified [ContractState] type.
 */
inline fun <reified T : ContractState> UtxoLedgerTransaction.getInputStateAndRefs(): List<StateAndRef<T>> {
    return getInputStateAndRefs(T::class.java)
}

/**
 * Obtains a single ledger transaction [StateAndRef] input that match the specified [ContractState] type.
 *
 * @param T The underlying type of the [ContractState].
 * @return Returns a single ledger transaction [StateAndRef] input that match the specified [ContractState] type.
 * @throws IllegalArgumentException if a single ledger transaction [StateAndRef] input that matches the specified [ContractState] type cannot be found.
 */
inline fun <reified T : ContractState> UtxoLedgerTransaction.getInputStateAndRef(): StateAndRef<T> {
    return getInputStateAndRef(T::class.java)
}

/**
 * Obtains all ledger transaction [ContractState] inputs that match the specified [ContractState] type.
 *
 * @param T The underlying type of the [ContractState].
 * @return Returns all ledger transaction [ContractState] inputs that match the specified [ContractState] type.
 */
inline fun <reified T : ContractState> UtxoLedgerTransaction.getInputStates(): List<T> {
    return getInputStates(T::class.java)
}

/**
 * Obtains a single ledger transaction [ContractState] input that match the specified [ContractState] type.
 *
 * @param T The underlying type of the [ContractState].
 * @return Returns a single ledger transaction [ContractState] input that match the specified [ContractState] type.
 * @throws IllegalArgumentException if a single ledger transaction [ContractState] input that matches the specified [ContractState] type cannot be found.
 */
inline fun <reified T : ContractState> UtxoLedgerTransaction.getInputState(): T {
    return getInputState(T::class.java)
}

// endregion

// region Reference Input States

/**
 * Obtains all ledger transaction [StateAndRef] reference inputs that match the specified [ContractState] type.
 *
 * @param T The underlying type of the [ContractState].
 * @return Returns all ledger transaction [StateAndRef] reference inputs that match the specified [ContractState] type.
 */
inline fun <reified T : ContractState> UtxoLedgerTransaction.getReferenceInputStateAndRefs(): List<StateAndRef<T>> {
    return getReferenceInputStateAndRefs(T::class.java)
}

/**
 * Obtains a single ledger transaction [StateAndRef] reference input that match the specified [ContractState] type.
 *
 * @param T The underlying type of the [ContractState].
 * @return Returns a single ledger transaction [StateAndRef] reference input that match the specified [ContractState] type.
 * @throws IllegalArgumentException if a single ledger transaction [StateAndRef] reference input that matches the specified [ContractState] type cannot be found.
 */
inline fun <reified T : ContractState> UtxoLedgerTransaction.getReferenceInputStateAndRef(): StateAndRef<T> {
    return getReferenceInputStateAndRef(T::class.java)
}

/**
 * Obtains all ledger transaction [ContractState] reference inputs that match the specified [ContractState] type.
 *
 * @param T The underlying type of the [ContractState].
 * @return Returns all ledger transaction [ContractState] reference inputs that match the specified [ContractState] type.
 */
inline fun <reified T : ContractState> UtxoLedgerTransaction.getReferenceInputStates(): List<T> {
    return getReferenceInputStates(T::class.java)
}

/**
 * Obtains a single ledger transaction [ContractState] reference input that match the specified [ContractState] type.
 *
 * @param T The underlying type of the [ContractState].
 * @return Returns a single ledger transaction [ContractState] reference input that match the specified [ContractState] type.
 * @throws IllegalArgumentException if a single ledger transaction [ContractState] reference input that matches the specified [ContractState] type cannot be found.
 */
inline fun <reified T : ContractState> UtxoLedgerTransaction.getReferenceInputState(): T {
    return getReferenceInputState(T::class.java)
}

// endregion

// region Output States

/**
 * Obtains all ledger transaction [StateAndRef] outputs that match the specified [ContractState] type.
 *
 * @param T The underlying type of the [ContractState].
 * @return Returns all ledger transaction [StateAndRef] outputs that match the specified [ContractState] type.
 */
inline fun <reified T : ContractState> UtxoLedgerTransaction.getOutputStateAndRefs(): List<StateAndRef<T>> {
    return getOutputStateAndRefs(T::class.java)
}

/**
 * Obtains a single ledger transaction [StateAndRef] output that match the specified [ContractState] type.
 *
 * @param T The underlying type of the [ContractState].
 * @return Returns a single ledger transaction [StateAndRef] output that match the specified [ContractState] type.
 * @throws IllegalArgumentException if a single ledger transaction [StateAndRef] output that matches the specified [ContractState] type cannot be found.
 */
inline fun <reified T : ContractState> UtxoLedgerTransaction.getOutputStateAndRef(): StateAndRef<T> {
    return getOutputStateAndRef(T::class.java)
}

/**
 * Obtains all ledger transaction [ContractState] outputs that match the specified [ContractState] type.
 *
 * @param T The underlying type of the [ContractState].
 * @return Returns all ledger transaction [ContractState] outputs that match the specified [ContractState] type.
 */
inline fun <reified T : ContractState> UtxoLedgerTransaction.getOutputStates(): List<T> {
    return getOutputStates(T::class.java)
}

/**
 * Obtains a single ledger transaction [ContractState] output that match the specified [ContractState] type.
 *
 * @param T The underlying type of the [ContractState].
 * @return Returns a single ledger transaction [ContractState] output that match the specified [ContractState] type.
 * @throws IllegalArgumentException if a single ledger transaction [ContractState] output that matches the specified [ContractState] type cannot be found.
 */
inline fun <reified T : ContractState> UtxoLedgerTransaction.getOutputState(): T {
    return getOutputState(T::class.java)
}

// endregion

// region Grouped States

/**
 * Obtains groups of ledger transaction [StateAndRef] inputs and outputs that match the specified [ContractState] type, grouped by the specified selector key.
 *
 * @param T The underlying type of the [ContractState].
 * @param K The underlying type of the selector key.
 * @param selector The selector that will be common to all grouped [StateAndRef] inputs and outputs.
 * @return Returns groups of ledger transaction [StateAndRef] inputs and outputs that match the specified [ContractState] type, grouped by the specified selector key.
 */
inline fun <reified T : ContractState, K : Any> UtxoLedgerTransaction.getGroupedStates(
    noinline selector: (StateAndRef<T>) -> K
): List<InputOutputGroup<T, K>> {
    return getGroupedStates(T::class.java, selector)
}

// endregion
