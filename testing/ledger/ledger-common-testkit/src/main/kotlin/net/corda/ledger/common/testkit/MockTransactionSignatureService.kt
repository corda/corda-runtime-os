package net.corda.ledger.common.testkit

import net.corda.v5.ledger.common.transaction.TransactionSignatureService
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.TransactionWithMetadata
import java.security.PublicKey

private class MockTransactionSignatureService: TransactionSignatureService {
    override fun sign(transactionId: SecureHash, publicKeys: Iterable<PublicKey>): List<DigitalSignatureAndMetadata> =
        listOf(getSignatureWithMetadataExample())
    override fun sign(
        transactions: List<TransactionWithMetadata>,
        publicKeys: Iterable<PublicKey>
    ): List<List<DigitalSignatureAndMetadata>> =
        List(transactions.size) { publicKeys.map { publicKey -> getSignatureWithMetadataExample(publicKey) } }

    override fun verifySignature(
        transaction: TransactionWithMetadata,
        signatureWithMetadata: DigitalSignatureAndMetadata
    ) {}
}

fun mockTransactionSignatureService(): TransactionSignatureService {
    return MockTransactionSignatureService()
}
