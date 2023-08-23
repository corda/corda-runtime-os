package net.corda.ledger.common.testkit

import net.corda.crypto.core.SecureHashImpl
import net.corda.ledger.common.flow.transaction.TransactionSignatureServiceInternal
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.TransactionWithMetadata
import java.security.MessageDigest
import java.security.PublicKey

private class FakeTransactionSignatureService: TransactionSignatureServiceInternal {
    override fun sign(
        transaction: TransactionWithMetadata,
        publicKeys: Iterable<PublicKey>
    ): List<DigitalSignatureAndMetadata> =
        publicKeys.map { getSignatureWithMetadataExample(it) }

    override fun signBatch(
        transactions: List<TransactionWithMetadata>,
        publicKeys: Iterable<PublicKey>
    ): List<List<DigitalSignatureAndMetadata>> =
        List(transactions.size) { publicKeys.map { publicKey -> getSignatureWithMetadataExample(publicKey) } }

    override fun verifySignature(
        transaction: TransactionWithMetadata,
        signatureWithMetadata: DigitalSignatureAndMetadata,
        publicKey: PublicKey
    ) {}

    override fun verifySignature(
        secureHash: SecureHash,
        signatureWithMetadata: DigitalSignatureAndMetadata,
        publicKey: PublicKey
    ) {}

    override fun getIdOfPublicKey(publicKey: PublicKey, digestAlgorithmName: String): SecureHash = SecureHashImpl(
        digestAlgorithmName,
        MessageDigest.getInstance(digestAlgorithmName).digest(publicKey.encoded)
    )
}

fun fakeTransactionSignatureService(): TransactionSignatureServiceInternal {
    return FakeTransactionSignatureService()
}
