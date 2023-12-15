package net.corda.ledger.utxo.flow.impl.transaction.verifier

import net.corda.crypto.cipher.suite.SignatureSpecImpl
import net.corda.crypto.core.DigitalSignatureWithKeyId
import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.fullIdHash
import net.corda.ledger.common.flow.transaction.TransactionSignatureServiceInternal
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.TransactionSignatureException
import net.corda.v5.ledger.common.transaction.TransactionWithMetadata
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.MessageDigest
import java.security.PublicKey
import java.time.Instant
import kotlin.test.assertEquals

class NotarySignatureVerificationServiceImplTest {
    private lateinit var notarySignatureVerificationService: NotarySignatureVerificationServiceImpl
    private val transactionSignatureServiceInternal = mock<TransactionSignatureServiceInternal>()

    // notarykeys
    private val notaryVNodeAliceKey = mock<PublicKey>().also { whenever(it.encoded).thenReturn(byteArrayOf(0x01)) }
    private val notaryVNodeBobKey = mock<PublicKey>().also { whenever(it.encoded).thenReturn(byteArrayOf(0x02)) }
    private val notaryServiceCompositeKey = mock<CompositeKey> {
        on { leafKeys } doReturn setOf(notaryVNodeAliceKey)
        on { isFulfilledBy(notaryVNodeAliceKey) } doReturn true
        on { isFulfilledBy(setOf(notaryVNodeAliceKey)) } doReturn true
    }

    // transactionId
    private val transaction = mock<TransactionWithMetadata>().also {
        whenever(it.id).thenReturn(transactionId)
    }
    private val transactionId = mock<SecureHash>()

    // signatures
    private val signatureAlice = digitalSignatureAndMetadata(notaryVNodeAliceKey, byteArrayOf(1, 2, 0))
    private val signatureBob = digitalSignatureAndMetadata(notaryVNodeBobKey, byteArrayOf(1, 2, 3))

    private val keyIdToNotaryKeysMap: MutableMap<String, Map<SecureHash, PublicKey>> = mutableMapOf()

    // keyIds
    private val keyIdOfAlice = SecureHashImpl(
        signatureAlice.by.algorithm,
        MessageDigest.getInstance(signatureAlice.by.algorithm).digest(notaryVNodeAliceKey.encoded)
    )
    private val keyIdOfBob = SecureHashImpl(
        signatureBob.by.algorithm,
        MessageDigest.getInstance(signatureBob.by.algorithm).digest(notaryVNodeBobKey.encoded)
    )

    @BeforeEach
    fun setup() {
        notarySignatureVerificationService = NotarySignatureVerificationServiceImpl(
            transactionSignatureServiceInternal
        )
    }

    @Test
    fun `notary signature verification with a valid public key`() {
        whenever(
            transactionSignatureServiceInternal.getIdOfPublicKey(
                notaryVNodeAliceKey,
                signatureAlice.by.algorithm
            )
        ).thenReturn(keyIdOfAlice)
        assertDoesNotThrow {
            notarySignatureVerificationService.verifyNotarySignatures(
                transaction,
                notaryVNodeAliceKey,
                listOf(signatureAlice),
                keyIdToNotaryKeysMap
            )
        }
    }

    @Test
    fun `notary signature verification with a valid composite key`() {
        whenever(
            transactionSignatureServiceInternal.getIdOfPublicKey(
                notaryVNodeAliceKey,
                signatureAlice.by.algorithm
            )
        ).thenReturn(keyIdOfAlice)
        assertDoesNotThrow {
            notarySignatureVerificationService.verifyNotarySignatures(
                transaction,
                notaryServiceCompositeKey,
                listOf(signatureAlice),
                keyIdToNotaryKeysMap
            )
        }
    }

    @Test
    fun `notary signature verification with an invalid notary key`() {
        whenever(
            transactionSignatureServiceInternal.getIdOfPublicKey(
                notaryVNodeBobKey,
                signatureBob.by.algorithm
            )
        ).thenReturn(keyIdOfBob)

        val exception = assertThrows<TransactionSignatureException> {
            notarySignatureVerificationService.verifyNotarySignatures(
                transaction,
                notaryServiceCompositeKey,
                listOf(signatureBob),
                keyIdToNotaryKeysMap
            )
        }

        assertEquals(
            "Notary signing keys [] did not fulfil requirements of notary service key $notaryServiceCompositeKey",
            exception.message
        )
    }

    @Test
    fun `notary signature verification with failed signature verification`() {
        whenever(
            transactionSignatureServiceInternal.getIdOfPublicKey(
                notaryVNodeBobKey,
                signatureBob.by.algorithm
            )
        ).thenReturn(keyIdOfBob)
        whenever(transactionSignatureServiceInternal.verifySignature(transaction, signatureBob, notaryVNodeBobKey))
            .thenThrow(TransactionSignatureException(transactionId, "", null))

        val exception = assertThrows<TransactionSignatureException> {
            notarySignatureVerificationService.verifyNotarySignatures(
                transaction,
                notaryVNodeBobKey,
                listOf(signatureBob),
                keyIdToNotaryKeysMap
            )
        }

        assertEquals(
            "Failed to verify signature of ${signatureBob.signature} for transaction $transaction. Message: ",
            exception.message
        )
    }

    private fun digitalSignatureAndMetadata(publicKey: PublicKey, byteArray: ByteArray): DigitalSignatureAndMetadata {
        return DigitalSignatureAndMetadata(
            DigitalSignatureWithKeyId(publicKey.fullIdHash(), byteArray),
            DigitalSignatureMetadata(Instant.now(), SignatureSpecImpl("dummySignatureName"), emptyMap())
        )
    }
}
