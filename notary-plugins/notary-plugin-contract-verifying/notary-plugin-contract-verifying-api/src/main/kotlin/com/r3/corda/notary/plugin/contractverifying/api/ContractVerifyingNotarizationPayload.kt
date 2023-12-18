package com.r3.corda.notary.plugin.contractverifying.api

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction

@CordaSerializable
data class ContractVerifyingNotarizationPayload(
    val initialTransaction: UtxoSignedTransaction,
    val filteredTransactionsAndSignatures: List<FilteredTransactionAndSignatures>,
)
