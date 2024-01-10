package net.corda.ledger.consensual.flow.impl.transaction.verifier

import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder

class ConsensualTransactionBuilderVerifier(private val transactionBuilder: ConsensualTransactionBuilder) : ConsensualTransactionVerifier() {

    fun verify() {
        verifyStateStructure(transactionBuilder.states)
    }
}