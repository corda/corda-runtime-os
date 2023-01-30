package net.corda.ledger.consensual.flow.impl.transaction

import net.corda.ledger.consensual.flow.impl.transaction.factory.ConsensualSignedTransactionFactory
import net.corda.ledger.consensual.flow.impl.transaction.verifier.ConsensualTransactionBuilderVerifier
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.consensual.ConsensualState
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder
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
    override fun toSignedTransaction(): ConsensualSignedTransaction =
        sign()

    @Suspendable
    fun sign(): ConsensualSignedTransaction {
        check(!alreadySigned) { "A transaction cannot be signed twice." }
        ConsensualTransactionBuilderVerifier(this).verify()
        return consensualSignedTransactionFactory.create(this).also {
            alreadySigned = true
        }
    }

    private fun copy(states: List<ConsensualState> = this.states): ConsensualTransactionBuilderImpl {
        return ConsensualTransactionBuilderImpl(
            consensualSignedTransactionFactory,
            states,
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ConsensualTransactionBuilderImpl) return false
        return other.states == states
    }

    override fun hashCode(): Int = Objects.hash(states)

    override fun toString(): String {
        return "ConsensualTransactionBuilderImpl(states=$states)"
    }
}
