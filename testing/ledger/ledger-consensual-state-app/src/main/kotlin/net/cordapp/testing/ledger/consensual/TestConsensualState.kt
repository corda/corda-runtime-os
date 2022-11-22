package net.cordapp.testing.ledger.consensual

import net.corda.v5.ledger.consensual.ConsensualState
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
import java.security.PublicKey
import java.util.Objects

@Suppress("Unused")
class TestConsensualState(
    val testField: String,
    override val participants: List<PublicKey>
) : ConsensualState {
    override fun verify(ledgerTransaction: ConsensualLedgerTransaction) {}
    override fun equals(other: Any?): Boolean =
        (this === other) || (
            (other is TestConsensualState) &&
                (other.testField == testField) &&
                (other.participants.size == participants.size) &&
                other.participants.containsAll(participants)
            )

    override fun hashCode(): Int = Objects.hash(testField, participants)
}
