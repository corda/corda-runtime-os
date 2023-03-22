package net.corda.ledger.common.testkit

import net.corda.ledger.common.flow.transaction.TransactionSignatureServiceInternal
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.TransactionWithMetadata
import java.security.PublicKey

private class MockTransactionSignatureService: TransactionSignatureServiceInternal {
    override fun sign(transaction: TransactionWithMetadata, publicKeys: Iterable<PublicKey>): List<DigitalSignatureAndMetadata> =
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

    override fun getIdOfPublicKey(publicKey: PublicKey, digestAlgorithmName: String): SecureHash? {
        TODO("Not yet implemented")
    }
}

fun mockTransactionSignatureService(): TransactionSignatureServiceInternal {
    return MockTransactionSignatureService()
}
