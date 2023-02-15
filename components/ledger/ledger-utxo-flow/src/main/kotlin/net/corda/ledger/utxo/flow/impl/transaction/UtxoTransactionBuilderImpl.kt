package net.corda.ledger.utxo.flow.impl.transaction

import net.corda.ledger.utxo.flow.impl.timewindow.TimeWindowBetweenImpl
import net.corda.ledger.utxo.flow.impl.timewindow.TimeWindowUntilImpl
import net.corda.ledger.utxo.flow.impl.transaction.factory.UtxoSignedTransactionFactory
import net.corda.ledger.utxo.flow.impl.transaction.verifier.UtxoTransactionBuilderVerifier
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TimeWindow
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder
import java.security.PublicKey
import java.time.Instant
import java.util.Objects

@Suppress("TooManyFunctions")
class UtxoTransactionBuilderImpl(
    private val utxoSignedTransactionFactory: UtxoSignedTransactionFactory,
    override var notary: Party? = null,
    override var timeWindow: TimeWindow? = null,
    override val attachments: MutableList<SecureHash> = mutableListOf(),
    override val commands: MutableList<Command> = mutableListOf(),
    override val signatories: MutableList<PublicKey> = mutableListOf(),
    override val inputStateRefs: MutableList<StateRef> = mutableListOf(),
    override val referenceStateRefs: MutableList<StateRef> = mutableListOf(),
    override val outputStates: MutableList<ContractStateAndEncumbranceTag> = mutableListOf()
) : UtxoTransactionBuilder, UtxoTransactionBuilderInternal {

    // TODO : Review implementation...
    // 1. Introduces mutability to what is effectively an immutable builder.
    // 2. Calling toSignedTransaction is an idempotent call, but results in signed transactions with different privacy salt.
    // 3. Probably won't be needed if we move to an implementation where the developer passes a transaction builder directly to finality.
    // 4. Consider the same implementation for the consensual transaction builder.
    private var alreadySigned = false

    override fun addAttachment(attachmentId: SecureHash): UtxoTransactionBuilder {
        this.attachments += attachmentId
        return this
    }

    override fun addCommand(command: Command): UtxoTransactionBuilder {
        this.commands += command
        return this
    }

    override fun addSignatories(signatories: Iterable<PublicKey>): UtxoTransactionBuilder {
        this.signatories += signatories
        return this
    }

    override fun addInputState(stateRef: StateRef): UtxoTransactionBuilder {
        this.inputStateRefs += stateRef
        return this
    }

    override fun addInputStates(stateRefs: Iterable<StateRef>): UtxoTransactionBuilder {
        this.inputStateRefs += stateRefs
        return this
    }

    override fun addInputStates(vararg stateRefs: StateRef): UtxoTransactionBuilder {
        return addInputStates(stateRefs.toList())
    }

    override fun addReferenceState(stateRef: StateRef): UtxoTransactionBuilder {
        this.referenceStateRefs += stateRef
        return this
    }

    override fun addReferenceStates(stateRefs: Iterable<StateRef>): UtxoTransactionBuilder {
        this.referenceStateRefs += stateRefs
        return this
    }

    override fun addReferenceStates(vararg stateRefs: StateRef): UtxoTransactionBuilder {
        return addReferenceStates(stateRefs.toList())
    }

    override fun addOutputState(contractState: ContractState): UtxoTransactionBuilder {
        this.outputStates += contractState.withEncumbrance(null)
        return this
    }

    override fun addOutputStates(contractStates: Iterable<ContractState>): UtxoTransactionBuilder {
        this.outputStates += contractStates.map { it.withEncumbrance(null) }
        return this
    }

    override fun addOutputStates(vararg contractStates: ContractState): UtxoTransactionBuilder {
        return addOutputStates(contractStates.toList())
    }

    override fun addEncumberedOutputStates(
        tag: String,
        contractStates: Iterable<ContractState>
    ): UtxoTransactionBuilder {
        this.outputStates += contractStates.map { it.withEncumbrance(tag) }
        return this
    }

    override fun addEncumberedOutputStates(tag: String, vararg contractStates: ContractState): UtxoTransactionBuilder {
        return addEncumberedOutputStates(tag, contractStates.toList())
    }

    override fun getEncumbranceGroup(tag: String): List<ContractState> {
        return requireNotNull(getEncumbranceGroups()[tag]) {
            "Encumbrance group with the specified tag does not exist: $tag."
        }
    }

    override fun getEncumbranceGroups(): Map<String, List<ContractState>> {
        return outputStates
            .filter { outputState -> outputState.encumbranceTag != null }
            .groupBy { outputState -> outputState.encumbranceTag }
            .map { entry -> entry.key!! to entry.value.map { items -> items.contractState } }
            .toMap()
    }

    override fun setNotary(notary: Party): UtxoTransactionBuilder {
        this.notary = notary
        return this
    }

    override fun setTimeWindowUntil(until: Instant): UtxoTransactionBuilder {
        this.timeWindow = TimeWindowUntilImpl(until)
        return this
    }

    override fun setTimeWindowBetween(from: Instant, until: Instant): UtxoTransactionBuilder {
        this.timeWindow = TimeWindowBetweenImpl(from, until)
        return this
    }

    @Suspendable
    override fun toSignedTransaction(): UtxoSignedTransaction {
        return sign()
    }

    @Suspendable
    private fun sign(): UtxoSignedTransaction {
        check(!alreadySigned) { "The transaction cannot be signed twice." }
        UtxoTransactionBuilderVerifier(this).verify()
        return utxoSignedTransactionFactory.create(this, signatories).also {
            alreadySigned = true
        }
    }

    private fun ContractState.withEncumbrance(tag: String?): ContractStateAndEncumbranceTag {
        return ContractStateAndEncumbranceTag(this, tag)
    }

    @Suppress("ComplexMethod")
    override fun equals(other: Any?): Boolean {
        return this === other
                || other is UtxoTransactionBuilderImpl
                && other.notary == notary
                && other.timeWindow == timeWindow
                && other.attachments == attachments
                && other.commands == commands
                && other.inputStateRefs == inputStateRefs
                && other.referenceStateRefs == referenceStateRefs
                && other.outputStates == outputStates
                && other.signatories == signatories
    }

    override fun hashCode(): Int = Objects.hash(
        notary,
        timeWindow,
        attachments,
        commands,
        signatories,
        inputStateRefs,
        referenceStateRefs,
        outputStates,
    )

    override fun toString(): String {
        return "UtxoTransactionBuilderImpl(" +
                "notary=$notary, " +
                "timeWindow=$timeWindow, " +
                "attachments=$attachments, " +
                "commands=$commands, " +
                "signatories=$signatories, " +
                "inputStateRefs=$inputStateRefs, " +
                "referenceStateRefs=$referenceStateRefs, " +
                "outputStates=$outputStates" +
                ")"
    }
}
