package net.corda.ledger.consensual.flow.impl.transaction.verifier

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction

class ConsensualLedgerTransactionVerifier(private val transaction: ConsensualLedgerTransaction) : ConsensualTransactionVerifier() {

    fun verify() {
        verifyStateStructure(transaction.states)
        verifySignatories()
        verifyStates()
    }

    private fun verifyStates() {
        transaction.states.map { it.verify(transaction) }
    }

    private fun verifySignatories() {
        val requiredSignatoriesFromStates = transaction.states.flatMap { it.participants }.toSet()
        require(transaction.requiredSignatories == requiredSignatoriesFromStates) {
            "Deserialized required signatories from ${WireTransaction::class.java.simpleName} do not match with the ones derived " +
                    "from the states."
        }
    }
}