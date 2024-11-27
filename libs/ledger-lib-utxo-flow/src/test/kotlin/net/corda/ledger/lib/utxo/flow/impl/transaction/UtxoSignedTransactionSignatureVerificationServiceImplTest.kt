package net.corda.ledger.lib.utxo.flow.impl.transaction

import net.corda.crypto.cipher.suite.SignatureSpecImpl
import net.corda.crypto.core.DigitalSignatureWithKeyId
import net.corda.crypto.core.fullIdHash
import net.corda.ledger.common.flow.transaction.TransactionSignatureServiceInternal
import net.corda.ledger.lib.utxo.flow.impl.transaction.verifier.NotarySignatureVerificationServiceInternal
import net.corda.ledger.lib.utxo.flow.impl.transaction.verifier.UtxoSignedTransactionSignatureVerificationServiceImpl
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import java.security.PublicKey
import java.time.Instant

class UtxoSignedTransactionSignatureVerificationServiceImplTest {

    private val notarySignatureVerificationService = mock<NotarySignatureVerificationServiceInternal>()
    private val transactionSignatureService = mock<TransactionSignatureServiceInternal>()

    private val transaction = mock<UtxoSignedTransaction>()
    private val publicKey = mock<PublicKey>().also { whenever(it.encoded).thenReturn(byteArrayOf(0x01)) }
    private val signature = digitalSignatureAndMetadata(publicKey, byteArrayOf(1, 2, 6))
    private val service = UtxoSignedTransactionSignatureVerificationServiceImpl(
        notarySignatureVerificationService,
        transactionSignatureService
    )

    @Test
    fun `Given a transaction with all signatories, getMissingSignatories should return an empty set`() {
        val fullIdHash = publicKey.fullIdHash()
        whenever(notarySignatureVerificationService.getKeyOrLeafKeys(publicKey)).thenReturn(listOf(publicKey))
        whenever(transactionSignatureService.getIdOfPublicKey(eq(publicKey), any())).thenReturn(fullIdHash)
        whenever(transaction.signatures).thenReturn(listOf(signature))
        whenever(transaction.signatories).thenReturn(listOf(publicKey))

        val missingSignatories = service.getMissingSignatories(transaction)
        println(missingSignatories)
        assert(missingSignatories.isEmpty())
    }

    private fun digitalSignatureAndMetadata(publicKey: PublicKey, byteArray: ByteArray): DigitalSignatureAndMetadata {
        return DigitalSignatureAndMetadata(
            DigitalSignatureWithKeyId(publicKey.fullIdHash(), byteArray),
            DigitalSignatureMetadata(Instant.now(), SignatureSpecImpl("dummySignatureName"), emptyMap())
        )
    }
}
