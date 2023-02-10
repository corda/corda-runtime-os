package net.corda.ledger.consensual.flow.impl.transaction.verifier

import net.corda.v5.ledger.consensual.ConsensualState

abstract class ConsensualTransactionVerifier {

    protected fun verifyStateStructure(states: List<ConsensualState>) {
        check(states.isNotEmpty()) { "At least one consensual state is required." }
        check(states.all { it.participants.isNotEmpty() }) { "All consensual states must have participants." }
    }
}