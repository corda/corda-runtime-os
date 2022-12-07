package net.corda.ledger.utxo.flow.impl.transaction

import net.corda.ledger.utxo.flow.impl.timewindow.TimeWindowBetweenImpl
import net.corda.ledger.utxo.flow.impl.timewindow.TimeWindowUntilImpl
import net.corda.ledger.utxo.flow.impl.transaction.factory.UtxoSignedTransactionFactory
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
import java.util.*

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
    // We cannot use TransactionStates without notary which may be available only later
    override val outputStates: List<Pair<ContractState, Int?>> = emptyList()
    override val inputStateAndRefs: List<StateAndRef<*>> = emptyList(),
    override val referenceInputStateAndRefs: List<StateAndRef<*>> = emptyList(),
    override val outputStates: List<ContractStateAndEncumbranceTag> = emptyList()
) : UtxoTransactionBuilder, UtxoTransactionBuilderInternal {

    override fun addAttachment(attachmentId: SecureHash): UtxoTransactionBuilder {
        return copy(attachments = attachments + attachmentId)
    }

    override fun addCommand(command: Command): UtxoTransactionBuilder {
        return copy(commands = commands + command)
    }

    override fun addSignatories(signatories: Iterable<PublicKey>): UtxoTransactionBuilder {
        return copy(signatories = this.signatories + signatories)
    }

    override fun addInputState(stateAndRef: StateAndRef<*>): UtxoTransactionBuilder {
        return copy(inputStateAndRefs = inputStateAndRefs + stateAndRef)
    }

    override fun addReferenceInputState(stateRef: StateRef): UtxoTransactionBuilder {
        return copy(referenceInputStateRefs = referenceInputStateRefs + stateRef)
    }

    override fun addOutputState(contractState: ContractState): UtxoTransactionBuilder {
        return copy(outputStates = outputStates + ContractStateAndEncumbranceTag(contractState, null))
    }

    override fun addEncumberedOutputStates(tag: String, contractStates: Iterable<ContractState>): UtxoTransactionBuilder {
        return copy(outputStates = outputStates + contractStates.map { ContractStateAndEncumbranceTag(it, tag) })
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
    @Deprecated("Temporary function until the parameterless version gets available")
    override fun toSignedTransaction(signatory: PublicKey): UtxoSignedTransaction {
        UtxoTransactionBuilderVerifier(this).verify()
        return utxoSignedTransactionFactory.create(this, signatories)
    }

    @Suspendable
    override fun toSignedTransaction(): UtxoSignedTransaction {
        TODO("Not yet implemented")
    }

    @Suppress("ComplexMethod")
    override fun equals(other: Any?): Boolean {
        return this === other
                || other is UtxoTransactionBuilderImpl
                && other.notary == notary
                && other.attachments == attachments
                && other.commands == commands
                && other.inputStateAndRefs == inputStateAndRefs
                && other.referenceInputStateAndRefs == referenceInputStateAndRefs
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
}
