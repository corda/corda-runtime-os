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
    override fun toSignedTransaction(vararg signatories: PublicKey): ConsensualSignedTransaction =
        sign(signatories.toList())

    @Suspendable
    @Deprecated("Temporary function until the argumentless version gets available")
    override fun toSignedTransaction(signatories: Iterable<PublicKey>): ConsensualSignedTransaction =
        sign(signatories)

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
        // TODO(CORE-5982 more verifications)
        // TODO(CORE-5982 ? metadata verifications: nulls, order of CPKs, at least one CPK?))
        check(!alreadySigned) { "A transaction cannot be signed twice." }
        require(states.isNotEmpty()) { "At least one consensual state is required" }
        require(states.all { it.participants.isNotEmpty() }) { "All consensual states must have participants" }
    }
}
