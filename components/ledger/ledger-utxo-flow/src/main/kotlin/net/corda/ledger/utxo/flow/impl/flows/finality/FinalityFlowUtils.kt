package net.corda.ledger.utxo.flow.impl.flows.finality

import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.ledger.utxo.Contract
import java.security.PublicKey

internal fun UtxoSignedTransactionInternal.getContracts(): Set<Contract> {
    val transaction = this.toLedgerTransaction()
    val allTransactionStateAndRefs = transaction.inputStateAndRefs + transaction.outputStateAndRefs
    return allTransactionStateAndRefs
        .mapTo(HashSet()) { it.state.contractType.getConstructor().newInstance() }
}

internal fun UtxoSignedTransactionInternal.getRelevantStatesIndexes(keys: Set<PublicKey>): List<Int> {
    val contracts = this.getContracts()
    return this.outputStateAndRefs.withIndex().filter { (_, stateAndRef) ->
        contracts.any { it.isRelevant(stateAndRef.state.contractState, keys) }
    }.map { it.index }
}

internal fun MemberLookup.getMyLedgerKeys() =
    this.myInfo()
        .ledgerKeys
        .toSet()