package net.corda.ledger.lib.utxo.flow.impl.transaction

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import java.security.PublicKey

interface UtxoSignedTransactionSignatureVerificationService {
    fun getMissingSignatories(transaction: UtxoSignedTransaction): Set<PublicKey>
    fun verifySignatorySignatures(transaction: UtxoSignedTransaction)
    fun verifyAttachedNotarySignature(transaction: UtxoSignedTransaction)
    fun verifyNotarySignature(transaction: UtxoSignedTransaction, signature: DigitalSignatureAndMetadata)
    fun verifySignatorySignature(transaction: UtxoSignedTransaction, signature: DigitalSignatureAndMetadata)
}
