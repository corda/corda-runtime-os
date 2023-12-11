package net.corda.ledger.common.flow.impl.transaction

import net.corda.ledger.common.flow.transaction.TransactionSignatureServiceInternal
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.KeyUtils
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.TransactionSignatureException
import net.corda.v5.ledger.common.transaction.TransactionSignatureService
import java.security.PublicKey

fun verifyNotarySignatures(
    transactionId: SecureHash,
    notaryKey: PublicKey,
    signatures: List<DigitalSignatureAndMetadata>,
    keyIdToNotaryKeys: MutableMap<String, Map<SecureHash, PublicKey>>,
    transactionSignatureService: TransactionSignatureService
) {
    val transactionSignatureServiceInternal = transactionSignatureService as TransactionSignatureServiceInternal
    val notaryPublicKeysWithValidSignatures = signatures.mapNotNull {
        val publicKey = getNotaryPublicKeyByKeyId(it.by, notaryKey, keyIdToNotaryKeys, transactionSignatureServiceInternal)
        if (publicKey != null) {
            try {
                transactionSignatureServiceInternal.verifySignature(transactionId, it, publicKey)
                publicKey
            } catch (e: Exception) {
                throw TransactionSignatureException(
                    transactionId,
                    "Failed to verify signature of ${it.signature} for transaction ${transactionId}. Message: ${e.message}",
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
            transactionId,
            "Notary signing keys $notaryPublicKeysWithValidSignatures did not fulfil " +
                    "requirements of notary service key $notaryKey",
            null
        )
    }
}

private fun getNotaryPublicKeyByKeyId(
    keyId: SecureHash,
    notaryKey: PublicKey,
    keyIdToNotaryKeys: MutableMap<String, Map<SecureHash, PublicKey>>,
    transactionSignatureServiceInternal: TransactionSignatureServiceInternal
): PublicKey? {
    val keyIdToPublicKey = keyIdToNotaryKeys.getOrPut(keyId.algorithm) {
        //Prepare keyIds for all public keys related to the notary for the relevant algorithm
        getKeyOrLeafKeys(notaryKey).associateBy {
            transactionSignatureServiceInternal.getIdOfPublicKey(
                it, keyId.algorithm
            )
        }
    }
    return keyIdToPublicKey[keyId]
}

fun getKeyOrLeafKeys(publicKey: PublicKey): List<PublicKey> {
    return when (publicKey) {
        is CompositeKey -> publicKey.leafKeys.toList()
        else -> listOf(publicKey)
    }
}