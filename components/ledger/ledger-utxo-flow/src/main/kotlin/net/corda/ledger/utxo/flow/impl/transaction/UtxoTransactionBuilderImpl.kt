package net.corda.ledger.utxo.flow.impl.transaction

import net.corda.ledger.utxo.flow.impl.timewindow.TimeWindowBetweenImpl
import net.corda.ledger.utxo.flow.impl.timewindow.TimeWindowUntilImpl
import net.corda.ledger.utxo.flow.impl.transaction.factory.UtxoSignedTransactionFactory
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.TimeWindow
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder
import java.security.PublicKey
import java.time.Instant
import java.util.Objects

@Suppress("TooManyFunctions")
data class UtxoTransactionBuilderImpl(
    private val utxoSignedTransactionFactory: UtxoSignedTransactionFactory,
    // cpi defines what type of signing/hashing is used (related to the digital signature signing and verification stuff)
    override val notary: Party? = null,
    override val timeWindow: TimeWindow? = null,
    override val attachments: List<SecureHash> = emptyList(),
    override val commands: List<Command> = emptyList(),
    override val signatories: List<PublicKey> = emptyList(),
    override val inputStateAndRefs: List<StateAndRef<*>> = emptyList(),
    override val referenceInputStateAndRefs: List<StateAndRef<*>> = emptyList(),

    // We cannot use TransactionStates without notary which may be available only later
    override val outputStates: List<Pair<ContractState, Int?>> = emptyList()
) : UtxoTransactionBuilder, UtxoTransactionBuilderInternal {

    private var alreadySigned = false
    override fun setNotary(notary: Party): UtxoTransactionBuilder {
        return copy(notary = notary)
    }

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

    override fun addReferenceInputState(stateAndRef: StateAndRef<*>): UtxoTransactionBuilder {
        return copy(referenceInputStateAndRefs = referenceInputStateAndRefs + stateAndRef)
    }

    override fun addOutputState(contractState: ContractState): UtxoTransactionBuilder {
        return addOutputState(contractState, null)
    }

    override fun addOutputState(contractState: ContractState, encumbrance: Int?): UtxoTransactionBuilder {
        return copy(outputStates = outputStates + Pair(contractState, encumbrance))
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
        require(signatories.toList().isNotEmpty()) {
            "At least one key needs to be provided in order to create a signed Transaction!"
        }
        verifyIfReady()
        val tx = utxoSignedTransactionFactory.create(this, signatories)
        alreadySigned = true
        return tx
    }

    @Suppress("ComplexMethod")
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UtxoTransactionBuilderImpl) return false
        if (other.notary != notary) return false
        if (other.attachments != attachments) return false
        if (other.commands != commands) return false
        if (other.inputStateAndRefs != inputStateAndRefs) return false
        if (other.referenceInputStateAndRefs != referenceInputStateAndRefs) return false
        if (other.outputStates != outputStates) return false
        if (other.signatories != signatories) return false
        return true
    }

    override fun hashCode(): Int = Objects.hash(
        notary,
        timeWindow,
        attachments,
        commands,
        signatories,
        inputStateAndRefs,
        referenceInputStateAndRefs,
        outputStates,
    )

    private fun verifyIfReady() {
        check(!alreadySigned) { "A transaction cannot be signed twice." }
        UtxoTransactionVerification.verifyNotary(notary)
        UtxoTransactionVerification.verifyStructures(timeWindow, inputStateAndRefs, outputStates.map { it.first })
    }
}
