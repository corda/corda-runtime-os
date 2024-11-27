package net.corda.ledger.lib.utxo.flow.impl.transaction.verifier

import net.corda.ledger.common.flow.transaction.TransactionMissingSignaturesException
import net.corda.ledger.common.flow.transaction.TransactionSignatureServiceInternal
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.crypto.KeyUtils
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.TransactionSignatureException
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import java.security.PublicKey

class UtxoSignedTransactionSignatureVerificationServiceImpl(
    private val notarySignatureVerificationService: NotarySignatureVerificationServiceInternal,
    private val transactionSignatureServiceInternal: TransactionSignatureServiceInternal,
) : UtxoSignedTransactionSignatureVerificationService {

    private val keyIdToSignatories: MutableMap<String, Map<SecureHash, PublicKey>> = mutableMapOf()
    private val keyIdToNotaryKeys: MutableMap<String, Map<SecureHash, PublicKey>> = mutableMapOf()

    // Notary/unknown signatures are ignored.
    override fun getMissingSignatories(transaction: UtxoSignedTransaction): Set<PublicKey> {
        return getMissingSignatories(transaction, getPublicKeysToSignatorySignatures(transaction))
    }

    // Notary/unknown signatures are ignored
    override fun verifySignatorySignatures(transaction: UtxoSignedTransaction) {
        val publicKeysToSignatures =
            getPublicKeysToSignatorySignatures(transaction)

        val missingSignatories = getMissingSignatories(transaction)
        if (missingSignatories.isNotEmpty()) {
            throw TransactionMissingSignaturesException(
                transaction.id,
                missingSignatories,
                "Transaction ${transaction.id} is missing signatures for signatories (encoded) ${
                    missingSignatories.map { it.encoded }
                }"
            )
        }
        publicKeysToSignatures.forEach { (publicKey, signature) ->
            try {
                transactionSignatureServiceInternal.verifySignature(transaction, signature, publicKey)
            } catch (e: Exception) {
                throw TransactionSignatureException(
                    transaction.id,
                    "Failed to verify signature of $signature from $publicKey for ${transaction.id} Message: ${e.message}",
                    e
                )
            }
        }
    }

    private fun getSignatoryKeyFromKeyId(transaction: UtxoSignedTransaction, keyId: SecureHash): PublicKey? {
        val keyIdToPublicKey = keyIdToSignatories.getOrPut(keyId.algorithm) {
            // Prepare keyIds for all public keys related to signatories for the relevant algorithm
            transaction.signatories.flatMap { signatory ->
                notarySignatureVerificationService.getKeyOrLeafKeys(signatory).map {
                    transactionSignatureServiceInternal.getIdOfPublicKey(
                        it, keyId.algorithm
                    ) to it
                }
            }.toMap()
        }
        return keyIdToPublicKey[keyId]
    }

    private fun getMissingSignatories(
        transaction: UtxoSignedTransaction,
        publicKeysToSignatures: Map<PublicKey, DigitalSignatureAndMetadata>
    ): Set<PublicKey> {
        val publicKeysWithSignatures = publicKeysToSignatures.keys.toHashSet()

        // TODO CORE-12207 isKeyFulfilledBy is not the most efficient
        // isKeyFulfilledBy() helps to make this working with CompositeKeys.
        return transaction.signatories
            .filterNot { KeyUtils.isKeyFulfilledBy(it, publicKeysWithSignatures) }
            .toSet()
    }

    private fun getPublicKeysToSignatorySignatures(transaction: UtxoSignedTransaction): Map<PublicKey, DigitalSignatureAndMetadata> {
        return transaction.signatures.mapNotNull { // We do not care about non-notary/non-signatory keys
            (getSignatoryKeyFromKeyId(transaction, it.by) ?: return@mapNotNull null) to it
        }.toMap()
    }

    override fun verifyAttachedNotarySignature(transaction: UtxoSignedTransaction) {
        notarySignatureVerificationService.verifyNotarySignatures(
            transaction,
            transaction.notaryKey,
            transaction.signatures.toList(),
            keyIdToNotaryKeys
        )
    }

    override fun verifyNotarySignature(transaction: UtxoSignedTransaction, signature: DigitalSignatureAndMetadata) {
        val publicKey = notarySignatureVerificationService.getNotaryPublicKeyByKeyId(signature.by, transaction.notaryKey, keyIdToNotaryKeys)
            ?: throw TransactionSignatureException(
                transaction.id,
                "Notary signature has not been created by the notary for this transaction. " +
                    "Notary public key: $transaction.notaryKey " +
                    "Notary signature key Id: ${signature.by}",
                null
            )

        try {
            transactionSignatureServiceInternal.verifySignature(transaction, signature, publicKey)
        } catch (e: Exception) {
            throw TransactionSignatureException(
                transaction.id,
                "Failed to verify notary signature of ${signature.signature} for ${transaction.id}. Message: ${e.message}",
                e
            )
        }
    }

    override fun verifySignatorySignature(transaction: UtxoSignedTransaction, signature: DigitalSignatureAndMetadata) {
        val publicKey = getSignatoryKeyFromKeyId(transaction, signature.by)
            ?: return // We do not care about non-notary/non-signatory signatures.
        try {
            transactionSignatureServiceInternal.verifySignature(transaction, signature, publicKey)
        } catch (e: Exception) {
            throw TransactionSignatureException(
                transaction.id,
                "Failed to verify signature of ${signature.signature} for transaction ${transaction.id}. Message: ${e.message}",
                e
            )
        }
    }
}
