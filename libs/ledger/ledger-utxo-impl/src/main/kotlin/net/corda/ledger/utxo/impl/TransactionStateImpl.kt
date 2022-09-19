package net.corda.ledger.utxo.impl

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.ledger.common.transaction.Party
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.CpkConstraint
import net.corda.v5.ledger.utxo.TransactionState
import net.corda.v5.ledger.utxo.TransactionStateInformation

/**
 * Represents a transaction state, composed of a [ContractState] and associated [TransactionStateInformation].
 *
 * @constructor Creates a new instance of the [TransactionState] data class.
 * @param T The underlying type of the [ContractState] instance.
 * @property contractState The [ContractState] component of the current [TransactionState] instance.
 * @property information The [TransactionStateInformation] component of the current [TransactionState] instance.
 */
@CordaSerializable
data class TransactionStateImpl<out T : ContractState>(
    override val contractState: T,
    override val information: TransactionStateInformation
) : TransactionState<T> {

    /**
     * Creates a new instance of the [TransactionState] data class.
     *
     * @param contractState The [ContractState] component of the current [TransactionState] instance.
     * @param contractId The class name of the contract associated with the transaction state.
     * @param notary The [Party] of the notary associated with the transaction state.
     * @param encumbrance The index of an associated, encumbered state, or null if no encumbrance applies to the associated transaction state.
     * @param constraint The [CpkConstraint] associated with the transaction state.
     */
    constructor(contractState: T, contractId: String, notary: Party, encumbrance: Int?, constraint: CpkConstraint) :
            this(contractState, TransactionStateInformationImpl(contractId, notary, encumbrance, constraint))

    /**
     * Creates a new instance of the [TransactionState] data class.
     *
     * @param contractState The [ContractState] component of the current [TransactionState] instance.
     * @param notary The [Party] of the notary associated with the transaction state.
     * @param encumbrance The index of an associated, encumbered state, or null if no encumbrance applies to the associated transaction state.
     * @param constraint The [CpkConstraint] associated with the transaction state.
     */
    constructor(contractState: T, notary: Party, encumbrance: Int?, constraint: CpkConstraint) :
            this(contractState, contractState.getContractClassNameOrThrow(), notary, encumbrance, constraint)

    /**
     * Creates a new instance of the [TransactionState] data class.
     *
     * @param contractState The [ContractState] component of the current [TransactionState] instance.
     * @param notary The [Party] of the notary associated with the transaction state.
     */
    constructor(contractState: T, notary: Party) :
            this(contractState, notary, null, AutomaticPlaceholderCpkConstraint)

    /**
     * Creates a new instance of the [TransactionState] data class.
     *
     * @param contractState The [ContractState] component of the current [TransactionState] instance.
     * @param notary The [Party] of the notary associated with the transaction state.
     * @param encumbrance The index of an associated, encumbered state, or null if no encumbrance applies to the associated transaction state.
     */
    constructor(contractState: T, notary: Party, encumbrance: Int) :
            this(contractState, notary, encumbrance, AutomaticPlaceholderCpkConstraint)

    /**
     * Creates a new instance of the [TransactionState] data class.
     *
     * @param contractState The [ContractState] component of the current [TransactionState] instance.
     * @param notary The [Party] of the notary associated with the transaction state.
     * @param constraint The [CpkConstraint] associated with the transaction state.
     */
    constructor(contractState: T, notary: Party, constraint: CpkConstraint) :
            this(contractState, notary, null, constraint)

    /**
     * Creates a new instance of the [TransactionState] data class.
     *
     * @param contractState The [ContractState] component of the current [TransactionState] instance.
     * @param contract The class name of the contract associated with the transaction state.
     * @param notary The [Party] of the notary associated with the transaction state.
     */
    constructor(contractState: T, contract: String, notary: Party) :
            this(contractState, contract, notary, null, AutomaticPlaceholderCpkConstraint)

    /**
     * Creates a new instance of the [TransactionState] data class.
     *
     * @param contractState The [ContractState] component of the current [TransactionState] instance.
     * @param contract The class name of the contract associated with the transaction state.
     * @param notary The [Party] of the notary associated with the transaction state.
     * @param encumbrance The index of an associated, encumbered state, or null if no encumbrance applies to the associated transaction state.
     */
    constructor(contractState: T, contract: String, notary: Party, encumbrance: Int) :
            this(contractState, contract, notary, encumbrance, AutomaticPlaceholderCpkConstraint)

    /**
     * Creates a new instance of the [TransactionState] data class.
     *
     * @param contractState The [ContractState] component of the current [TransactionState] instance.
     * @param contract The class name of the contract associated with the transaction state.
     * @param notary The [Party] of the notary associated with the transaction state.
     * @param constraint The [CpkConstraint] associated with the transaction state.
     */
    constructor(contractState: T, contract: String, notary: Party, constraint: CpkConstraint) :
            this(contractState, contract, notary, null, constraint)
}
