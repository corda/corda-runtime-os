package net.corda.ledger.utxo.flow.impl.flows.finality

import net.corda.flow.state.asFlowContext
import net.corda.ledger.lib.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.utilities.MDC_LOGGED_PREFIX
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.VisibilityChecker

@Suspendable
fun UtxoSignedTransactionInternal.getVisibleStateIndexes(checker: VisibilityChecker): List<Int> {
    val result = mutableListOf<Int>()

    for (index in outputStateAndRefs.indices) {
        val stateAndRef = outputStateAndRefs[index]
        val contract = stateAndRef.state.contractType.getConstructor().newInstance()

        if (contract.isVisible(stateAndRef.state.contractState, checker)) {
            result.add(index)
        }
    }

    return result
}

@Suspendable
fun addTransactionIdToFlowContext(flowEngine: FlowEngine, transactionId: SecureHash) {
    flowEngine.flowContextProperties
        .asFlowContext
        .platformProperties["$MDC_LOGGED_PREFIX.transactionId"] = transactionId.toString()
}
