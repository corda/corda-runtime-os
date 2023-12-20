package net.corda.ledger.utxo.flow.impl.flows.finality

import net.corda.v5.base.annotations.CordaSerializable

@CordaSerializable
data class DependencyPayload(
    val filteredTransactionsAndSignatures: List<FilteredTransactionAndSignatures>
)
