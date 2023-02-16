package net.corda.ledger.consensual.flow.impl.transaction

import net.corda.ledger.consensual.flow.impl.transaction.factory.ConsensualSignedTransactionFactory
import net.corda.ledger.consensual.flow.impl.transaction.verifier.ConsensualTransactionBuilderVerifier
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.consensual.ConsensualState
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder
import java.util.Objects

class ConsensualTransactionBuilderImpl(
    private val consensualSignedTransactionFactory: ConsensualSignedTransactionFactory,
    override val states: MutableList<ConsensualState> = mutableListOf(),
) : ConsensualTransactionBuilder, ConsensualTransactionBuilderInternal {

     override fun withStates(vararg states: ConsensualState): ConsensualTransactionBuilder {
        this.states += states
        return this
    }

    @Suspendable
    override fun toSignedTransaction(): ConsensualSignedTransactionInternal {
        ConsensualTransactionBuilderVerifier(this).verify()
        return consensualSignedTransactionFactory.create(this)
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
