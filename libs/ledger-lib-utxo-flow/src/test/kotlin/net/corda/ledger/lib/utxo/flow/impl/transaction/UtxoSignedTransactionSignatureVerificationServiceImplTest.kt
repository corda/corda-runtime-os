package net.corda.ledger.lib.utxo.flow.impl.transaction

import net.corda.crypto.cipher.suite.SignatureSpecImpl
import net.corda.crypto.cipher.suite.sha256Bytes
import net.corda.crypto.core.DigitalSignatureWithKeyId
import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.fullIdHash
import net.corda.crypto.impl.keys
import net.corda.internal.serialization.amqp.api.SerializationServiceInternal
import net.corda.ledger.common.flow.transaction.TransactionMissingSignaturesException
import net.corda.ledger.common.flow.transaction.TransactionSignatureServiceInternal
import net.corda.ledger.lib.utxo.flow.impl.transaction.verifier.NotarySignatureVerificationServiceInternal
import net.corda.ledger.lib.utxo.flow.impl.transaction.verifier.UtxoSignedTransactionSignatureVerificationServiceImpl
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.security.PublicKey
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class UtxoSignedTransactionSignatureVerificationServiceImplTest {

    private val notarySignatureVerificationService = mock<NotarySignatureVerificationServiceInternal>()
    private val serializationService = mock<SerializationServiceInternal>()
    private val digitalSignatureVerificationService = mock<DigitalSignatureVerificationService>()
    private val transactionSignatureService = mock<TransactionSignatureServiceInternal>()

    private val signatureSpec = mock<SignatureSpec>()
    private val transaction = mock<UtxoSignedTransaction>()
    private val signature = mock<DigitalSignatureAndMetadata>()
    private val digitalSignatureWithKeyId = mock<DigitalSignatureWithKeyId>()
    private val publicKey = mock<PublicKey>()
    private val publicKeysToSignatures = mapOf(publicKey to listOf(signature))

    private val x500name = "CN=Test,O=R3,L=London,C=GB"

    private val transactionId = SecureHashImpl("123", ByteArray(5))
    private val service = UtxoSignedTransactionSignatureVerificationServiceImpl(
        notarySignatureVerificationService,
        transactionSignatureService
    )

    @Test
    fun `Given a transaction with all signatories, getMissingSignatories should return an empty set `() {
        whenever(publicKey.encoded).thenReturn(byteArrayOf(0x00))
        val signature = digitalSignatureAndMetadata(publicKey, byteArrayOf(1, 2, 6))
        whenever(transaction.signatures).thenReturn(listOf(signature))
        whenever(transaction.signatories).thenReturn(listOf(publicKey))
//        whenever(publicKey.fullIdHash()).thenReturn(SecureHashImpl("123", ByteArray(5)))
//        whenever(signature.by).thenReturn(publicKey.fullIdHash())

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

//    @Test
//    fun `Given a transaction with missing signatures verifySignatorySignatures should a TransactionMissingSignatures exception`() {
//        whenever(transaction.id).thenReturn(transactionId)
//        whenever(transaction.signatures).thenReturn(emptyList())
//        whenever(transaction.signatories).thenReturn(listOf(publicKey))
//
//        whenever(publicKey.name).thenReturn(x500name)
//        whenever(publicKey.encoded).thenReturn("named".toByteArray())
//        whenever(signature.signature).thenReturn(vaultSignature)
//
//        whenever(vaultSignature.digitalSignature.keyIdentity.networkIdentity).thenReturn(x500name)
//
//        assertThrows(TransactionMissingSignaturesException::class.java) {
//            service.verifySignatorySignatures(transaction)
//        }
//    }
//
//    @Test
//    fun `Given a transaction the verifyNotarySignature should verify the notary in the transaction`() {
//        val notaryKey = mock<PublicKey>()
//        val serializedTransaction = ByteArray(1)
//
//        whenever(transaction.notaryKey).thenReturn(notaryKey)
//        whenever(transaction.id).thenReturn(transactionId)
//
//        whenever(signature.by).thenReturn(SecureHashImpl("123", ByteArray(3)))
//        whenever(signature.signature).thenReturn(vaultSignature)
//
//        whenever(digitalSignatureVerificationService.verify(serializedTransaction, vaultSignature, notaryKey, signatureSpec))thenReturn(Unit)
//        whenever(notarySignatureVerificationService.getNotaryPublicKeyByKeyId(any(), any(), any())).thenReturn(notaryKey)
//        whenever(serializationService.serialize(transaction).bytes).thenReturn(serializedTransaction)
//
//        service.verifyNotarySignature(transaction, signature)
//
//        verify(
//            digitalSignatureVerificationService.verify(
//                serializedTransaction,
//                vaultSignature,
//                notaryKey,
//                signatureSpec
//            )
//        )
//    }
}
