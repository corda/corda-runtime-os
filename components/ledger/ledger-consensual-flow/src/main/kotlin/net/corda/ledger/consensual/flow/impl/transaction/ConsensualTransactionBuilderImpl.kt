package net.corda.ledger.consensual.flow.impl.transaction

import net.corda.ledger.consensual.flow.impl.transaction.factory.ConsensualSignedTransactionFactory
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.consensual.ConsensualState
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder
import java.security.PublicKey
import java.util.Objects

class ConsensualTransactionBuilderImpl(
    private val consensualSignedTransactionFactory: ConsensualSignedTransactionFactory,
    // cpi defines what type of signing/hashing is used (related to the digital signature signing and verification stuff)
    override val states: List<ConsensualState> = emptyList(),
) : ConsensualTransactionBuilder {

    private var alreadySigned = false

    override fun withStates(vararg states: ConsensualState): ConsensualTransactionBuilder =
        copy(states = this.states + states)

    @Suspendable
    override fun toSignedTransaction(): ConsensualSignedTransaction {
        TODO("Not yet implemented")
    }

    @Suspendable
    @Deprecated("Temporary function until the argumentless version gets available")
    override fun toSignedTransaction(signatory: PublicKey): ConsensualSignedTransaction =
        sign(listOf(signatory))

    @Suspendable
    fun sign(): ConsensualSignedTransaction {
        TODO("Not yet implemented")
    }

    @Suspendable
    fun sign(signatories: Iterable<PublicKey>): ConsensualSignedTransaction{
        require(signatories.toList().isNotEmpty()) {
            "At least one key needs to be provided in order to create a signed Transaction!"
        }
        verifyIfReady()
        val tx = consensualSignedTransactionFactory.create(this, signatories)
        alreadySigned = true
        return tx
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ConsensualTransactionBuilderImpl) return false
        return other.states == states
    }

    override fun hashCode(): Int = Objects.hash(states)

    private fun copy(states: List<ConsensualState> = this.states): ConsensualTransactionBuilderImpl {
        return ConsensualTransactionBuilderImpl(
            consensualSignedTransactionFactory,
            states,
        )
    }

    private fun verifyIfReady() {
        check(!alreadySigned) { "A transaction cannot be signed twice." }
        ConsensualTransactionVerification.verifyStatesStructure(states)
        // The metadata, states will get verified in the [ConsensualSignedTransactionFactoryImpl.create()] since the whole
        // transaction is assembled there.
    }
}
