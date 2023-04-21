package net.corda.ledger.consensual.testkit

import net.corda.v5.ledger.consensual.ConsensualState
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
import java.security.PublicKey
import java.util.Objects

class ConsensualStateClassExample(val testField: String, private val participants: List<PublicKey>) : ConsensualState {

    override fun getParticipants(): List<PublicKey> {
        return participants
    }

    override fun verify(ledgerTransaction: ConsensualLedgerTransaction) {
        if (testField == "throw") {
            throw IllegalStateException("State verification failed")
        }
    }

    override fun equals(other: Any?): Boolean {
        return this === other
                || other is ConsensualStateClassExample
                && other.testField == testField
                && other.participants == participants
    }

    override fun hashCode(): Int = Objects.hash(testField, participants)
}
