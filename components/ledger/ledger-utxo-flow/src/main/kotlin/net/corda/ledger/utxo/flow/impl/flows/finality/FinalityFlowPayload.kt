package net.corda.ledger.utxo.flow.impl.flows.finality

object FinalityFlowPayload {
    const val INITIAL_TRANSACTION = "INITIAL_TRANSACTION"
    const val WAIT_FOR_ADDITIONAL_SIGNATURES = "WAIT_FOR_ADDITIONAL_SIGNATURES"
}