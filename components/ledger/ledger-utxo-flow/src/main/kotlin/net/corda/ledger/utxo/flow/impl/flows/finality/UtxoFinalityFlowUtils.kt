package net.corda.ledger.utxo.flow.impl.flows.finality

import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.v5.base.annotations.Suspendable
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
