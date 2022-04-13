package net.corda.v5.ledger.contracts

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.util.loggerFor
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.UniqueIdentifier
import net.corda.v5.ledger.identity.AbstractParty
import net.corda.v5.ledger.identity.Party
import net.corda.v5.ledger.transactions.requireNotNullContractClassName
import net.corda.v5.ledger.transactions.requiredContractClassName

/**
 * A contract state (or just "state") contains opaque data used by a contract program. It can be thought of as a disk
 * file that the program can use to persist data across transactions. States are immutable: once created they are never
 * updated, instead, any changes must generate a new successor state. States can be updated (consumed) only once: the
 * notary is responsible for ensuring there is no "double spending" by only signing a transaction if the input states
 * are all free.
 */
@CordaSerializable
interface ContractState {
    /**
     * A _participant_ is any party that should be notified when the state is created or consumed.
     *
     * The list of participants is required for certain types of transactions. For example, when changing the notary
     * for this state, every participant has to be involved and approve the transaction
     * so that they receive the updated state, and don't end up in a situation where they can no longer use a state
     * they possess, since someone consumed that state during the notary change process.
     *
     * The participants list should normally be derived from the contents of the state.
     */
    val participants: List<AbstractParty>
}

/**
 * A state that evolves by superseding itself, all of which share the common "linearId".
 *
 * This simplifies the job of tracking the current version of certain types of state in e.g. a vault.
 */
interface LinearState : ContractState {
    /**
     * Unique id shared by all LinearState states throughout history within the vaults of all parties.
     * Verify methods should check that one input and one output share the id in a transaction,
     * except at issuance/termination.
     */
    val linearId: UniqueIdentifier
}

/**
 * Interface to represent things which are fungible, this means that there is an expectation that these things can
 * be split and merged. That's the only assumption made by this interface.
 *
 * The expectation is that this interface should be combined with the other core state interfaces such as
 * [OwnableState] and others created at the application layer.
 *
 * @param T a type that represents the fungible thing in question. This should describe the basic type of the asset
 * (GBP, USD, oil, shares in company <X>, etc.) and any additional metadata (issuer, grade, class, etc.). An
 * upper-bound is not specified for [T] to ensure flexibility. Typically, a class would be provided that implements
 * [TokenizableAssetInfo].
 */
interface FungibleState<T : Any> : ContractState {
    /**
     * Amount represents a positive quantity of some token which can be cash, tokens, stock, agreements, or generally
     * anything else that's quantifiable with integer quantities. See [Amount] for more details.
     */
    val amount: Amount<T>
}

/**
 * A contract state that can have a single owner.
 */
interface OwnableState : ContractState {
    /** There must be a MoveCommand signed by this key to claim the amount. */
    val owner: AbstractParty

    /** Copies the underlying data structure, replacing the owner field with this new value and leaving the rest alone. */
    fun withNewOwner(newOwner: AbstractParty): CommandAndState
}

/**
 * Return structure for [OwnableState.withNewOwner]
 */
data class CommandAndState(val command: CommandData, val ownableState: OwnableState)

/**
 * A stateref is a pointer (reference) to a state, this is an equivalent of an "outpoint" in Bitcoin. It records which
 * transaction defined the state and where in that transaction it was.
 */
@CordaSerializable
data class StateRef(val txhash: SecureHash, val index: Int) {
    override fun toString() = "$txhash($index)"
}

/** A StateAndRef is simply a (state, ref) pair. For instance, a vault (which holds available assets) contains these. */
@CordaSerializable
data class StateAndRef<out T : ContractState>(val state: TransactionState<T>, val ref: StateRef) {
    /** For adding [StateAndRef]s as references to a [TransactionBuilder][net.corda.v5.ledger.transactions.TransactionBuilder]. */
    fun referenced() = ReferencedStateAndRef(this)
}

/** A wrapper for a [StateAndRef] indicating that it should be added to a transaction as a reference input state. */
data class ReferencedStateAndRef<out T : ContractState>(val stateAndRef: StateAndRef<T>)

/** Filters a list of [StateAndRef] objects according to the type of the states */
inline fun <reified T : ContractState> Iterable<StateAndRef<ContractState>>.filterStatesOfType(): List<StateAndRef<T>> {
    return mapNotNull { if (it.state.data is T) StateAndRef(TransactionState(it.state.data, it.state.contract, it.state.notary), it.ref) else null }
}

/**
 * A convenience class for passing around a state and it's contract
 *
 * @property state A state
 * @property contract The contract that should verify the state
 */
data class StateAndContract(val state: ContractState, val contract: ContractClassName)

/**
 * A wrapper for [ContractState] containing additional platform-level state information and contract information. This is the definitive
 * state that is stored on the ledger and used in transaction outputs.
 *
 * @param data The custom contract state.
 *
 * @param contract The contract class name that will verify this state that will be created via reflection. The attachment containing this
 * class will be automatically added to the transaction at transaction creation time.
 *
 * Currently these are loaded from the classpath of the node which includes the cordapp directory - at some point these will also be loaded
 * and run from the attachment store directly, allowing contracts to be sent across, and run, from the network from within a sandbox
 * environment.
 *
 * @param notary Identity of the notary that ensures the state is not used as an input to a transaction more than once.
 *
 * @param encumbrance All contract states may be _encumbered_ by up to one other state. The encumbrance state, if present, forces additional
 * controls over the encumbered state, since the platform checks that the encumbrance state is present as an input in the same transaction
 * that consumes the encumbered state, and the contract code and rules of the encumbrance state will also be verified during the execution
 * of the transaction. For example, a cash contract state could be encumbered with a time-lock contract state; the cash state is then only
 * processable in a transaction that verifies that the time specified in the encumbrance time-lock has passed.
 *
 * The encumbered state refers to another by index, and the referred encumbrance state is an output state in a particular position on the
 * same transaction that created the encumbered state. An alternative implementation would be encumbering by reference to a [StateRef],
 * which would allow the specification of encumbrance by a state created in a prior transaction.
 *
 * Note that an encumbered state that is being consumed must have its encumbrance consumed in the same transaction, otherwise the
 * transaction is not valid.
 *
 * @param constraint A validator for the contract attachments on the transaction.
 */
@CordaSerializable
data class TransactionState<out T : ContractState>(
    val data: T,
    val contract: ContractClassName,
    val notary: Party,
    val encumbrance: Int?,
    val constraint: CPKConstraint
) {

    private companion object {
        val logger = loggerFor<TransactionState<*>>()
    }

    constructor(data: T, contract: ContractClassName, notary: Party) : this(data, contract, notary, encumbrance = null, constraint = AutomaticPlaceholderCPKConstraint)
    constructor(data: T, contract: ContractClassName, notary: Party, encumbrance: Int) : this(data, contract, notary, encumbrance, constraint = AutomaticPlaceholderCPKConstraint)
    constructor(data: T, contract: ContractClassName, notary: Party, constraint: CPKConstraint) : this(data, contract, notary, encumbrance = null, constraint)

    constructor(data: T, notary: Party) : this(data, contract = requireNotNullContractClassName(data), notary, encumbrance = null, constraint = AutomaticPlaceholderCPKConstraint)
    constructor(data: T, notary: Party, encumbrance: Int): this(data, contract = requireNotNullContractClassName(data), notary, encumbrance, constraint = AutomaticPlaceholderCPKConstraint)
    constructor(data: T, notary: Party, constraint: CPKConstraint): this(data, contract = requireNotNullContractClassName(data), notary, encumbrance = null, constraint)
    constructor(data: T, notary: Party, encumbrance: Int, constraint: CPKConstraint) : this(data, contract = requireNotNullContractClassName(data), notary, encumbrance, constraint)

    init {
        when {
            data.requiredContractClassName == null -> logger.warn(
                """
        State class ${data::class.java.name} is not annotated with @BelongsToContract,
        and does not have an enclosing class which implements Contract. Annotate ${data::class.java.simpleName}
        with @BelongsToContract(${contract.split("\\.\\$").last()}.class) to remove this warning.
        """.trimIndent().replace('\n', ' ')
            )
            data.requiredContractClassName != contract -> logger.warn(
                """
        State class ${data::class.java.name} belongs to contract ${data.requiredContractClassName},
        but is bundled with contract $contract in TransactionState. Annotate ${data::class.java.simpleName}
        with @BelongsToContract(${contract.split("\\.\\$").last()}.class) to remove this warning.
        """.trimIndent().replace('\n', ' ')
            )
        }
    }
}

@CordaSerializable
data class StateInfo (
        val contract: ContractClassName,
        val notary: Party,
        val encumbrance: Int?,
        val constraint: CPKConstraint
) {

    private companion object {
        val logger = loggerFor<TransactionState<*>>()
    }
    constructor(contract: ContractClassName, notary: Party) : this(contract, notary, encumbrance = null, constraint = AutomaticPlaceholderCPKConstraint)
    constructor(contract: ContractClassName, notary: Party, encumbrance: Int) : this(contract, notary, encumbrance, constraint = AutomaticPlaceholderCPKConstraint)
    constructor(contract: ContractClassName, notary: Party, constraint: CPKConstraint) : this(contract, notary, encumbrance = null, constraint)
}

@CordaSerializable
data class ContractStateData<out T : ContractState>( val data: T)

