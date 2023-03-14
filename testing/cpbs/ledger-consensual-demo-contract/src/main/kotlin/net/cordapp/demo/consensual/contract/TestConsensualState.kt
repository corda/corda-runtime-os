package net.cordapp.demo.consensual.contract

import net.corda.v5.ledger.consensual.ConsensualState
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
import java.security.PublicKey

class TestConsensualState(val testField: String, private val participants: List<PublicKey>) : ConsensualState {

    override fun getParticipants(): List<PublicKey> {
        return participants
    }

    override fun verify(ledgerTransaction: ConsensualLedgerTransaction) {}
}
