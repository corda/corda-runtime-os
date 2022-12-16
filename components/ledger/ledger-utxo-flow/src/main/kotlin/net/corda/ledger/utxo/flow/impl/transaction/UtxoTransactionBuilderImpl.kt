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
data class UtxoTransactionBuilderImpl(
    private val utxoSignedTransactionFactory: UtxoSignedTransactionFactory,
    override val notary: Party? = null,
    override val timeWindow: TimeWindow? = null,
    override val attachments: List<SecureHash> = emptyList(),
    override val commands: List<Command> = emptyList(),
    override val signatories: List<PublicKey> = emptyList(),
    override val inputStateRefs: List<StateRef> = emptyList(),
    override val referenceInputStateRefs: List<StateRef> = emptyList(),
    override val outputStates: List<ContractStateAndEncumbranceTag> = emptyList()
) : UtxoTransactionBuilder, UtxoTransactionBuilderInternal {

    // TODO : Review implementation...
    // 1. Introduces mutability to what is effectively an immutable builder.
    // 2. Calling toSignedTransaction is an idempotent call, but results in signed transactions with different privacy salt.
    // 3. Probably won't be needed if we move to an implementation where the developer passes a transaction builder directly to finality.
    // 4. Consider the same implementation for the consensual transaction builder.
    private var alreadySigned = false

    override fun addAttachment(attachmentId: SecureHash): UtxoTransactionBuilder {
        return copy(attachments = attachments + attachmentId)
    }

    override fun addCommand(command: Command): UtxoTransactionBuilder {
        return copy(commands = commands + command)
    }

    override fun addSignatories(signatories: Iterable<PublicKey>): UtxoTransactionBuilder {
        return copy(signatories = this.signatories + signatories)
    }

    override fun addInputState(stateRef: StateRef): UtxoTransactionBuilder {
        return copy(inputStateRefs = inputStateRefs + stateRef)
    }

    override fun addInputStates(stateRefs: Iterable<StateRef>): UtxoTransactionBuilder {
        return copy(inputStateRefs = inputStateRefs + stateRefs)
    }

    override fun addInputStates(vararg stateRefs: StateRef): UtxoTransactionBuilder {
        return addInputStates(stateRefs.toList())
    }

    override fun addReferenceInputState(stateRef: StateRef): UtxoTransactionBuilder {
        return copy(referenceInputStateRefs = referenceInputStateRefs + stateRef)
    }

    override fun addReferenceInputStates(stateRefs: Iterable<StateRef>): UtxoTransactionBuilder {
        return copy(referenceInputStateRefs = referenceInputStateRefs + stateRefs)
    }

    override fun addReferenceInputStates(vararg stateRefs: StateRef): UtxoTransactionBuilder {
        return addReferenceInputStates(stateRefs.toList())
    }

    override fun addOutputState(contractState: ContractState): UtxoTransactionBuilder {
        return copy(outputStates = outputStates + contractState.withEncumbrance(null))
    }

    override fun addOutputStates(contractStates: Iterable<ContractState>): UtxoTransactionBuilder {
        return copy(outputStates = outputStates + contractStates.map { it.withEncumbrance(null) })
    }

    override fun addOutputStates(vararg contractStates: ContractState): UtxoTransactionBuilder {
        return addOutputStates(contractStates.toList())
    }

    override fun addEncumberedOutputStates(
        tag: String,
        contractStates: Iterable<ContractState>
    ): UtxoTransactionBuilder {
        return copy(outputStates = outputStates + contractStates.map { it.withEncumbrance(tag) })
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
        return copy(notary = notary)
    }

    override fun setTimeWindowUntil(until: Instant): UtxoTransactionBuilder {
        return copy(timeWindow = TimeWindowUntilImpl(until))
    }

    override fun setTimeWindowBetween(from: Instant, until: Instant): UtxoTransactionBuilder {
        return copy(timeWindow = TimeWindowBetweenImpl(from, until))
    }

    @Suspendable
    override fun toSignedTransaction(): UtxoSignedTransaction {
        TODO("Not yet implemented")
    }

    @Suspendable
    @Deprecated("Temporary function until the argumentless version gets available")
    override fun toSignedTransaction(signatory: PublicKey): UtxoSignedTransaction =
        sign(listOf(signatory))

    @Suspendable
    fun sign(): UtxoSignedTransaction {
        TODO("Not yet implemented")
    }

    @Suspendable
    fun sign(vararg signatories: PublicKey): UtxoSignedTransaction =
        sign(signatories.toList())

    @Suspendable
    fun sign(signatories: Iterable<PublicKey>): UtxoSignedTransaction {
        check(!alreadySigned) { "The transaction cannot be signed twice." }
        require(signatories.toList().isNotEmpty()) {
            "At least one key needs to be provided in order to create a signed Transaction!"
        }
        UtxoTransactionBuilderVerifier(this).verify()
        val tx = utxoSignedTransactionFactory.create(this, signatories)
        alreadySigned = true
        return tx
    }

    @Suppress("ComplexMethod")
    override fun equals(other: Any?): Boolean {
        return this === other
                || other is UtxoTransactionBuilderImpl
                && other.notary == notary
                && other.attachments == attachments
                && other.commands == commands
                && other.inputStateRefs == inputStateRefs
                && other.referenceInputStateRefs == referenceInputStateRefs
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
        referenceInputStateRefs,
        outputStates,
    )

    private fun ContractState.withEncumbrance(tag: String?): ContractStateAndEncumbranceTag {
        return ContractStateAndEncumbranceTag(this, tag)
    }
}
