package net.corda.ledger.utxo.impl.transaction

import net.corda.ledger.utxo.impl.timewindow.TimeWindowBetweenImpl
import net.corda.ledger.utxo.impl.timewindow.TimeWindowUntilImpl
import net.corda.ledger.utxo.impl.state.TransactionStateImpl
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.TimeWindow
import net.corda.v5.ledger.utxo.TransactionState
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder
import java.security.PublicKey
import java.time.Instant

data class UtxoTransactionBuilderImpl(
    override val notary: Party,
    private val timeWindow: TimeWindow,
    private val attachments: List<SecureHash> = emptyList(),
    private val commands: List<Command> = emptyList(),
    private val signatories: Set<PublicKey> = emptySet(),
    private val inputStateAndRefs: List<StateAndRef<*>> = emptyList(),
    private val referenceInputStateAndRefs: List<StateAndRef<*>> = emptyList(),
    private val outputTransactionStates: List<TransactionState<*>> = emptyList()
) : UtxoTransactionBuilder {


    override fun addAttachment(attachmentId: SecureHash): UtxoTransactionBuilder {
        return copy(attachments = attachments + attachmentId)
    }

    override fun addCommand(command: Command): UtxoTransactionBuilder {
        return copy(commands = commands + command)
    }

    override fun addSignatories(signatories: Iterable<PublicKey>): UtxoTransactionBuilder {
        return copy(signatories = this.signatories + signatories)
    }

    override fun addCommandAndSignatories(command: Command, signatories: Iterable<PublicKey>): UtxoTransactionBuilder {
        return addCommand(command).addSignatories(signatories)
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
        val transactionState = TransactionStateImpl(contractState, notary, encumbrance)
        return copy(outputTransactionStates = outputTransactionStates + transactionState)
    }

    override fun setTimeWindowUntil(until: Instant): UtxoTransactionBuilder {
        return copy(timeWindow = TimeWindowUntilImpl(until))
    }

    override fun setTimeWindowBetween(from: Instant, until: Instant): UtxoTransactionBuilder {
        return copy(timeWindow = TimeWindowBetweenImpl(from, until))
    }

    @Suspendable
    override fun sign(): UtxoSignedTransaction {
        TODO("Not yet implemented")
    }

    @Suspendable
    override fun sign(vararg signatories: PublicKey): UtxoSignedTransaction {
        TODO("Not yet implemented")
    }

    @Suspendable
    override fun sign(signatories: Iterable<PublicKey>): UtxoSignedTransaction {
        TODO("Not yet implemented")
    }
}
