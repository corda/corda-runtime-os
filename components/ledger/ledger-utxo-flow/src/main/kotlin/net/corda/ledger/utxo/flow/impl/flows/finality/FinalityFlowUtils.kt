package net.corda.ledger.utxo.flow.impl.flows.finality

import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.ledger.utxo.VisibilityChecker

fun UtxoSignedTransactionInternal.getVisibleStateIndexes(checker: VisibilityChecker): List<Int> {
    return outputStateAndRefs.withIndex().filter { (_, stateAndRef) ->
        val contract = stateAndRef.state.contractType.getConstructor().newInstance()
        contract.isVisible(checker, stateAndRef.state.contractState)
    }.map { it.index }
}

fun MemberLookup.getMyLedgerKeys() =
    this.myInfo()
        .ledgerKeys
        .toSet()