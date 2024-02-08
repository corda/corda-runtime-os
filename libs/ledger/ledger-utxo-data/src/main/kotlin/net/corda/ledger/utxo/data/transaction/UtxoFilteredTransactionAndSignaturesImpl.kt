package net.corda.ledger.utxo.data.transaction

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.ledger.utxo.NotarySignatureVerificationService
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransaction
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransactionAndSignatures
import net.corda.v5.membership.NotaryInfo

data class UtxoFilteredTransactionAndSignaturesImpl(
    private val filteredTransaction: UtxoFilteredTransaction,
    private val signatures: List<DigitalSignatureAndMetadata>
) : UtxoFilteredTransactionAndSignatures {
    override fun getFilteredTransaction(): UtxoFilteredTransaction = filteredTransaction
    override fun getSignatures(): List<DigitalSignatureAndMetadata> = signatures
}

fun UtxoFilteredTransactionAndSignatures.verifyFilteredTransactionAndSignatures(
    notary: NotaryInfo,
    notarySignatureVerificationService: NotarySignatureVerificationService
) {
    require(signatures.isNotEmpty()) { "No notary signatures were received" }

    filteredTransaction.verify()

    require(notary.name == filteredTransaction.notaryName) {
        "Notary name of filtered transaction \"${filteredTransaction.notaryName}\" doesn't match with " +
            "notary name of initial transaction \"${notary.name}\""
    }

    notarySignatureVerificationService.verifyNotarySignatures(
        filteredTransaction,
        notary.publicKey,
        signatures,
        mutableMapOf()
    )
}
