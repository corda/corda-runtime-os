package net.corda.ledger.utxo.flow.impl.flows.finality

import net.corda.ledger.common.data.transaction.PrivacySalt
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransaction

@CordaSerializable
data class FilteredTransactionAndSignatures(
    val filteredTransaction: UtxoFilteredTransaction,
    val privacySalt: PrivacySalt,
    val signatures: List<DigitalSignatureAndMetadata>
)