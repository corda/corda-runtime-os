package net.corda.ledger.utxo.flow.impl.flows.finality

import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.v5.application.membership.MemberLookup
import java.security.PublicKey

fun UtxoSignedTransactionInternal.getRelevantStatesIndexes(keys: Set<PublicKey>): List<Int> {
    return this.outputStateAndRefs.withIndex().filter { (_, stateAndRef) ->
        val contract = stateAndRef.state.contractType.getConstructor().newInstance()
        contract.isRelevant(stateAndRef.state.contractState, keys)
    }.map { it.index }
}

fun MemberLookup.getMyLedgerKeys() =
    this.myInfo()
        .ledgerKeys
        .toSet()