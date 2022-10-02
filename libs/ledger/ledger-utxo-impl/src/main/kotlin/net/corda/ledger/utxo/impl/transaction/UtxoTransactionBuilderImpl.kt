package net.corda.ledger.utxo.impl.transaction

import net.corda.ledger.utxo.impl.TimeWindowBetweenImpl
import net.corda.ledger.utxo.impl.TimeWindowFromImpl
import net.corda.ledger.utxo.impl.TimeWindowUntilImpl
import net.corda.ledger.utxo.impl.TransactionStateImpl
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.*
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder
import java.security.PublicKey
import java.time.Duration
import java.time.Instant

data class UtxoTransactionBuilderImpl(
    override val notary: Party,
    override val timeWindow: TimeWindow,
    override val attachments: List<SecureHash> = emptyList(),
    override val commands: List<Command> = emptyList(),
    override val signatories: Set<PublicKey> = emptySet(),
    override val inputStateAndRefs: List<StateAndRef<*>> = emptyList(),
    override val referenceInputStateAndRefs: List<StateAndRef<*>> = emptyList(),
    override val outputTransactionStates: List<TransactionState<*>> = emptyList()
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

    override fun setTimeWindowFrom(from: Instant): UtxoTransactionBuilder {
        return copy(timeWindow = TimeWindowFromImpl(from))
    }

    override fun setTimeWindowUntil(until: Instant): UtxoTransactionBuilder {
        return copy(timeWindow = TimeWindowUntilImpl(until))
    }

    override fun setTimeWindowBetween(from: Instant, until: Instant): UtxoTransactionBuilder {
        return copy(timeWindow = TimeWindowBetweenImpl(from, until))
    }

    override fun setTimeWindowBetween(midpoint: Instant, tolerance: Duration): UtxoTransactionBuilder {
        val half = tolerance.dividedBy(2)
        return setTimeWindowBetween(midpoint - half, midpoint + half)
    }

    override fun sign(): UtxoSignedTransaction {
        TODO("Not yet implemented")
    }

    override fun sign(vararg signatories: PublicKey): UtxoSignedTransaction {
        TODO("Not yet implemented")
    }

    override fun sign(signatories: Iterable<PublicKey>): UtxoSignedTransaction {
        TODO("Not yet implemented")
    }

    override fun verify() {
        TODO("Not yet implemented")
    }

    override fun verifyAndSign(): UtxoSignedTransaction {
        TODO("Not yet implemented")
    }

    override fun verifyAndSign(vararg signatories: PublicKey): UtxoSignedTransaction {
        TODO("Not yet implemented")
    }

    override fun verifyAndSign(signatories: Iterable<PublicKey>): UtxoSignedTransaction {
        TODO("Not yet implemented")
    }
}
