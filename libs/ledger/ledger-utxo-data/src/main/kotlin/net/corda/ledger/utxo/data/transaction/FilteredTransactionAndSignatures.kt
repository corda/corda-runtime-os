package net.corda.ledger.utxo.data.transaction

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransaction

@CordaSerializable
data class FilteredTransactionAndSignatures(
    val filteredTransaction: UtxoFilteredTransaction,
    val signatures: List<DigitalSignatureAndMetadata>
)
