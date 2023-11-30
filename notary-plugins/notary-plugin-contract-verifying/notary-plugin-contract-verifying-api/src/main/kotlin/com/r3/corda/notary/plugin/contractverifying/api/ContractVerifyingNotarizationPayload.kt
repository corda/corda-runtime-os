package com.r3.corda.notary.plugin.contractverifying.api

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransaction

@CordaSerializable
data class ContractVerifyingNotarizationPayload(
    val initialTransaction: UtxoSignedTransaction,
    val filteredTransactionsAndSignatures: List<FilteredTransactionAndSignatures>,
)

@CordaSerializable
data class FilteredTransactionAndSignatures(
    val filteredTransaction: UtxoFilteredTransaction,
    val signatures: List<DigitalSignatureAndMetadata>
)
