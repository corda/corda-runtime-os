package net.corda.ledger.common.testkit

import net.corda.ledger.common.flow.transaction.TransactionSignatureService
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.crypto.SecureHash
import java.security.PublicKey

private class MockTransactionSignatureService: TransactionSignatureService {
    override fun sign(transactionId: SecureHash, publicKeys: Set<PublicKey>): List<DigitalSignatureAndMetadata> =
        listOf(getSignatureWithMetadataExample())

    override fun verifySignature(transactionId: SecureHash, signatureWithMetadata: DigitalSignatureAndMetadata) {}
    override fun verifyNotarySignature(transactionId: SecureHash, signatureWithMetadata: DigitalSignatureAndMetadata) {}
}

fun mockTransactionSignatureService(): TransactionSignatureService {
    return MockTransactionSignatureService()
}