package net.cordapp.testing.ledger.consensual

import net.corda.v5.ledger.consensual.ConsensualState
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
import java.security.PublicKey
import java.util.Objects

@Suppress("Unused")
class TestConsensualState(val testField: String, private val participants: List<PublicKey>) : ConsensualState {

    override fun getParticipants(): List<PublicKey> {
        return participants
    }

    override fun verify(ledgerTransaction: ConsensualLedgerTransaction) {}

    override fun equals(other: Any?): Boolean {
        return this === other
                || other is TestConsensualState
                && other.testField == testField
                && other.participants == participants
    }

    override fun hashCode(): Int = Objects.hash(testField, participants)
}
