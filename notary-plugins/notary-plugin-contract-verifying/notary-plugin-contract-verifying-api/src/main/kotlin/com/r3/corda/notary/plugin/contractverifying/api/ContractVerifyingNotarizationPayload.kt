package com.r3.corda.notary.plugin.contractverifying.api

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransaction
import java.security.PublicKey

@CordaSerializable
data class ContractVerifyingNotarizationPayload(
    val initialFilteredTransaction: UtxoFilteredTransaction,
    val filteredTransactionsAndSignatures: List<FilteredTransactionAndSignatures>,
    val notaryKey: PublicKey // TODO Do we even need this? Could be fetched from the filtered TX
)

@CordaSerializable
data class FilteredTransactionAndSignatures(
    val filteredTransaction: UtxoFilteredTransaction,
    val signatures: List<DigitalSignatureAndMetadata>
)
