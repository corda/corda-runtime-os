package net.corda.ledger.lib.utxo.flow.impl.transaction.verifier

import net.corda.ledger.common.flow.transaction.TransactionSignatureVerificationServiceInternal
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.KeyUtils
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.TransactionSignatureException
import net.corda.v5.ledger.common.transaction.TransactionWithMetadata
import net.corda.v5.ledger.utxo.NotarySignatureVerificationService
import java.security.PublicKey

class NotarySignatureVerificationServiceImpl(
    private val transactionSignatureService: TransactionSignatureVerificationServiceInternal
) : NotarySignatureVerificationService, NotarySignatureVerificationServiceInternal {
    override fun verifyNotarySignatures(
        transaction: TransactionWithMetadata,
        notaryKey: PublicKey,
        signatures: List<DigitalSignatureAndMetadata>,
        keyIdToNotaryKeys: MutableMap<String, Map<SecureHash, PublicKey>>
    ) {
        val notaryPublicKeysWithValidSignatures = signatures.mapNotNull {
            val publicKey =
                getNotaryPublicKeyByKeyId(it.by, notaryKey, keyIdToNotaryKeys)
            if (publicKey != null) {
                try {
                    transactionSignatureService.verifySignature(transaction, it, publicKey)
                    publicKey
                } catch (e: Exception) {
                    throw TransactionSignatureException(
                        transaction.id,
                        "Failed to verify signature of ${it.signature} for transaction $transaction. Message: ${e.message}",
                        e
                    )
                }
            } else {
                null
            }
        }.toSet()
        // If the notary service key (composite key) is provided we need to make sure it contains the key the
        // transaction was signed with. This means it was signed with one of the notary VNodes (worker).
        if (!KeyUtils.isKeyFulfilledBy(notaryKey, notaryPublicKeysWithValidSignatures)) {
            throw TransactionSignatureException(
                transaction.id,
                "Notary signing keys $notaryPublicKeysWithValidSignatures did not fulfil " +
                    "requirements of notary service key $notaryKey",
                null
            )
        }
    }

    override fun getNotaryPublicKeyByKeyId(
        keyId: SecureHash,
        notaryKey: PublicKey,
        keyIdToNotaryKeys: MutableMap<String, Map<SecureHash, PublicKey>>
    ): PublicKey? {
        val keyIdToPublicKey = keyIdToNotaryKeys.getOrPut(keyId.algorithm) {
            // Prepare keyIds for all public keys related to the notary for the relevant algorithm
            getKeyOrLeafKeys(notaryKey).associateBy {
                transactionSignatureService.getIdOfPublicKey(
                    it,
                    keyId.algorithm
                )
            }
        }
        return keyIdToPublicKey[keyId]
    }

    override fun getKeyOrLeafKeys(publicKey: PublicKey): List<PublicKey> {
        return when (publicKey) {
            is CompositeKey -> publicKey.leafKeys.toList()
            else -> listOf(publicKey)
        }
    }
}
