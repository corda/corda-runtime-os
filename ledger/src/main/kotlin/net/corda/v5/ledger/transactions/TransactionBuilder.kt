package net.corda.v5.ledger.transactions

import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.contracts.AutomaticPlaceholderCPKConstraint
import net.corda.v5.ledger.contracts.CPKConstraint
import net.corda.v5.ledger.contracts.Command
import net.corda.v5.ledger.contracts.CommandData
import net.corda.v5.ledger.contracts.ContractClassName
import net.corda.v5.ledger.contracts.ContractState
import net.corda.v5.ledger.contracts.ReferencedStateAndRef
import net.corda.v5.ledger.contracts.StateAndRef
import net.corda.v5.ledger.contracts.StateRef
import net.corda.v5.ledger.contracts.TimeWindow
import net.corda.v5.ledger.contracts.TransactionState
import net.corda.v5.ledger.contracts.TransactionVerificationException
import net.corda.v5.ledger.identity.Party
import net.corda.v5.ledger.services.TransactionService
import java.security.PrivateKey
import java.security.PublicKey
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * A TransactionBuilder is a transaction class that's mutable (unlike the others which are all immutable). It is
 * intended to be passed around contracts that may edit it by adding new states/commands. Then once the states
 * and commands are right, this class can be used as a holding bucket to gather signatures from multiple parties.
 *
 */
interface TransactionBuilder {

    /** Returns an immutable list of input [StateRef]s. */
    val inputStates: List<StateRef>

    /** Returns an immutable list of reference input [StateRef]s. */
    val referenceStates: List<StateRef>

    /** Returns an immutable list of attachment hashes. */
    val attachments: List<SecureHash>

    /** Returns an immutable list of output [TransactionState]s. */
    val outputStates: List<TransactionState<*>>

    /** Returns an immutable list of [Command]s. */
    val commands: List<Command<*>>

    /** Access to Notary, can be null. */
    val notary: Party?

    val lockId: UUID

    fun copy(): TransactionBuilder

    fun setNotary(notary: Party?): TransactionBuilder

    /** A more convenient way to add items to this transaction that calls the add* methods for you based on type */
    fun withItems(vararg items: Any): TransactionBuilder

    /**
     * Generates a [WireTransaction] from this builder.
     *
     * @returns A new [WireTransaction] that will be unaffected by further changes to this [TransactionBuilder].
     */
    fun toWireTransaction(): WireTransaction

    /**
     * Verifies this transaction and runs contract code. At this stage it is assumed that signatures have already been verified.
     *
     * The contract verification logic is run in a custom classloader created for the current transaction.
     *
     * @throws TransactionVerificationException if anything goes wrong.
     */
    fun verify()

    /**
     * Constructs an initial partially signed transaction from this [TransactionBuilder] using a set of keys all held in this node.
     *
     * @param signingPubKeys A list of [PublicKey]s used to lookup the matching [PrivateKey] and sign.
     *
     * @return Returns a [SignedTransaction] with the new node signature attached.
     *
     * @throws IllegalArgumentException If any keys are unavailable locally.
     *
     * @see TransactionService.sign
     */
    fun sign(signingPubKeys: Collection<PublicKey>): SignedTransaction

    /**
     * Constructs an initial partially signed transaction from this [TransactionBuilder] using keys stored inside the node. Signature
     * metadata is added automatically.
     *
     * @param publicKey The [PublicKey] matched to the internal [PrivateKey] to use in signing this transaction. If the passed in key is
     * actually a [CompositeKey], the code searches for the first child key hosted within this node to sign with.
     *
     * @return Returns a SignedTransaction with the new node signature attached.
     *
     * @see TransactionService.sign
     */
    fun sign(publicKey: PublicKey): SignedTransaction

    /**
     * Constructs an initial partially signed transaction from a [TransactionBuilder] using the default identity key contained in the node.
     * The legal identity key is used to sign.
     *
     * @return Returns a [SignedTransaction] with the new node signature attached.
     *
     * @see TransactionService.sign
     */
    fun sign(): SignedTransaction

    /** Adds an attachment with the specified hash to the TransactionBuilder. */
    fun addAttachment(attachmentId: SecureHash): TransactionBuilder

    /**
     * Adds an input [StateAndRef] to the transaction.
     */
    fun addInputState(stateAndRef: StateAndRef<*>): TransactionBuilder

    /**
     * Adds a reference input [ReferencedStateAndRef] to the transaction.
     */
    fun addReferenceState(referencedStateAndRef: ReferencedStateAndRef<*>): TransactionBuilder

    /** Adds an output state to the transaction. */
    fun addOutputState(state: TransactionState<*>): TransactionBuilder

    /** Adds an output state, with associated contract code (and constraints), and notary, to the transaction. */
    fun addOutputState(
        state: ContractState,
        contract: ContractClassName,
        notary: Party,
        encumbrance: Int?,
        constraint: CPKConstraint
    ) = addOutputState(TransactionState(state, contract, notary, encumbrance, constraint))

    /** Associated overloads for addOutputState */
    fun addOutputState(
        state: ContractState,
        contract: ContractClassName,
        notary: Party,
        encumbrance: Int?
    ) = addOutputState(state, contract, notary, encumbrance, AutomaticPlaceholderCPKConstraint)

    fun addOutputState(
        state: ContractState,
        contract: ContractClassName,
        notary: Party,
    ) = addOutputState(state, contract, notary, null, AutomaticPlaceholderCPKConstraint)

    fun addOutputState(
        state: ContractState,
        notary: Party
    ) = addOutputState(state, requireNotNullContractClassName(state), notary)

    /** Adds an output state. A default notary must be specified during builder construction to use this method */
    fun addOutputState(
        state: ContractState,
        contract: ContractClassName,
        constraint: CPKConstraint
    ): TransactionBuilder

    /** Adds an output state for the given ContracState */
    fun addOutputState(
        state: ContractState
    ) = addOutputState(state, requireNotNullContractClassName(state), AutomaticPlaceholderCPKConstraint)

    /** Adds an output state with the specified ContractClassName. */
    fun addOutputState(
        state: ContractState,
        contract: ContractClassName
    ) = addOutputState(state, contract, AutomaticPlaceholderCPKConstraint)

    /** Adds an output state with the specified CPKConstraint. */
    fun addOutputState(
        state: ContractState,
        constraint: CPKConstraint
    ) = addOutputState(state, requireNotNullContractClassName(state), constraint)

    /** Adds a [Command] to the transaction. */
    fun addCommand(arg: Command<*>): TransactionBuilder

    /**
     * Adds a [Command] to the transaction, specified by the encapsulated [CommandData} object and required list of
     * signing [PublicKey]s.
     */
    fun addCommand(data: CommandData, key: PublicKey, vararg keys: PublicKey): TransactionBuilder = addCommand(Command(data, listOf(*keys) + key))

    fun addCommand(data: CommandData, keys: List<PublicKey>): TransactionBuilder = addCommand(Command(data, keys))

    /**
     * Sets the [TimeWindow] for this transaction, replacing the existing [TimeWindow] if there is one. To be valid, the
     * transaction must then be signed by the notary service within this window of time. In this way, the notary acts as
     * the Timestamp Authority.
     */
    fun setTimeWindow(timeWindow: TimeWindow): TransactionBuilder

    /**
     * The [TimeWindow] for the transaction can also be defined as [time] +/- [timeTolerance]. The tolerance should be
     * chosen such that your code can finish building the transaction and sending it to the Timestamp Authority within
     * that window of time, taking into account factors such as network latency. Transactions being built by a group of
     * collaborating parties may therefore require a higher time tolerance than a transaction being built by a single
     * node.
     */
    fun setTimeWindow(time: Instant, timeTolerance: Duration) = setTimeWindow(TimeWindow.withTolerance(time, timeTolerance))

    fun setPrivacySalt(privacySalt: PrivacySalt): TransactionBuilder
}
