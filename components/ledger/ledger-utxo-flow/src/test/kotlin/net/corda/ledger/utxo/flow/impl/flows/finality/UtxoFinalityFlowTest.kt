package net.corda.ledger.utxo.flow.impl.flows.finality

import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.common.flow.transaction.TransactionSignatureService
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.exceptions.CryptoSignatureException
import net.corda.v5.membership.MemberInfo
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.security.PublicKey
import java.time.Instant

// Todo adjust these tests to UTXO logic

class UtxoFinalityFlowTest {

    private companion object {
        val ALICE = MemberX500Name("Alice", "London", "GB")
        val BOB = MemberX500Name("Bob", "London", "GB")
    }

    private val transactionSignatureService = mock<TransactionSignatureService>()
    private val persistenceService = mock<UtxoLedgerPersistenceService>()
    private val flowMessaging = mock<FlowMessaging>()

    private val sessionAlice = mock<FlowSession>()
    private val sessionBob = mock<FlowSession>()

    private val memberInfoAlice = mock<MemberInfo>()
    private val memberInfoBob = mock<MemberInfo>()

    private val publicKeyAlice1 = mock<PublicKey>()
    private val publicKeyAlice2 = mock<PublicKey>()
    private val publicKeyBob = mock<PublicKey>()

    private val signatureAlice1 = digitalSignatureAndMetadata(publicKeyAlice1, byteArrayOf(1, 2, 3))
    private val signatureAlice2 = digitalSignatureAndMetadata(publicKeyAlice2, byteArrayOf(1, 2, 4))
    private val signatureBob = digitalSignatureAndMetadata(publicKeyBob, byteArrayOf(1, 2, 5))

    private val signedTransaction = mock<UtxoSignedTransactionInternal>()
    private val updatedSignedTransaction = mock<UtxoSignedTransactionInternal>()

    @BeforeEach
    fun beforeEach() {
        whenever(sessionAlice.counterparty).thenReturn(ALICE)
        whenever(sessionAlice.receive(Unit::class.java)).thenReturn(Unit)
        whenever(sessionBob.counterparty).thenReturn(BOB)
        whenever(sessionBob.receive(Unit::class.java)).thenReturn(Unit)

        whenever(memberInfoAlice.ledgerKeys).thenReturn(listOf(publicKeyAlice1, publicKeyAlice2))
        whenever(memberInfoBob.ledgerKeys).thenReturn(listOf(publicKeyBob))

        whenever(signedTransaction.id).thenReturn(SecureHash("algo", byteArrayOf(1, 2, 3)))
        whenever(signedTransaction.getMissingSignatories()).thenReturn(
            setOf(
                publicKeyAlice1,
                publicKeyAlice2,
                publicKeyBob
            )
        )
        whenever(signedTransaction.addSignature(any<DigitalSignatureAndMetadata>())).thenReturn(updatedSignedTransaction)
        whenever(updatedSignedTransaction.addSignature(any<DigitalSignatureAndMetadata>())).thenReturn(
            updatedSignedTransaction
        )
        whenever(updatedSignedTransaction.id).thenReturn(SecureHash("algo", byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `receiving valid signatures over a transaction leads to it being recorded and distributed for recording to passed in sessions`() {
        whenever(signedTransaction.getMissingSignatories()).thenReturn(
            setOf(
                publicKeyAlice1,
                publicKeyAlice2,
                publicKeyBob
            )
        )

        whenever(sessionAlice.sendAndReceive(Payload::class.java, signedTransaction)).thenReturn(
            Payload.Success(
                listOf(
                    signatureAlice1,
                    signatureAlice2
                )
            )
        )
        whenever(sessionBob.sendAndReceive(Payload::class.java, signedTransaction)).thenReturn(
            Payload.Success(
                listOf(
                    signatureBob
                )
            )
        )
        whenever(updatedSignedTransaction.signatures).thenReturn(listOf(signatureAlice1, signatureAlice2, signatureBob))

        callFinalityFlow(signedTransaction, listOf(sessionAlice, sessionBob))

        verify(transactionSignatureService).verifySignature(any(), eq(signatureAlice1))
        verify(transactionSignatureService).verifySignature(any(), eq(signatureAlice2))
        verify(transactionSignatureService).verifySignature(any(), eq(signatureBob))

        verify(signedTransaction).addSignature(signatureAlice1)
        verify(updatedSignedTransaction).addSignature(signatureAlice2)
        verify(updatedSignedTransaction).addSignature(signatureBob)

        verify(persistenceService).persist(updatedSignedTransaction, TransactionStatus.UNVERIFIED)
        verify(persistenceService).persist(updatedSignedTransaction, TransactionStatus.VERIFIED)

        verify(sessionAlice).sendAndReceive(Payload::class.java, signedTransaction)
        verify(sessionBob).sendAndReceive(Payload::class.java, signedTransaction)
        verify(flowMessaging).sendAllMap(
            mapOf(
                sessionAlice to listOf(signatureBob),
                sessionBob to listOf(signatureAlice1, signatureAlice2)
            )
        )
    }

    @Test
    fun `ledger keys from the passed in sessions can contain more than the missing signatories`() {
        whenever(memberInfoAlice.ledgerKeys).thenReturn(listOf(publicKeyAlice1, publicKeyAlice2))

        whenever(signedTransaction.getMissingSignatories()).thenReturn(setOf(publicKeyAlice1, publicKeyBob))

        whenever(sessionAlice.sendAndReceive(Payload::class.java, signedTransaction)).thenReturn(
            Payload.Success(
                listOf(
                    signatureAlice1
                )
            )
        )
        whenever(sessionBob.sendAndReceive(Payload::class.java, signedTransaction)).thenReturn(
            Payload.Success(
                listOf(
                    signatureBob
                )
            )
        )
        whenever(updatedSignedTransaction.signatures).thenReturn(listOf(signatureAlice1, signatureBob))

        callFinalityFlow(signedTransaction, listOf(sessionAlice, sessionBob))

        verify(transactionSignatureService).verifySignature(any(), eq(signatureAlice1))
        verify(transactionSignatureService, never()).verifySignature(any(), eq(signatureAlice2))
        verify(transactionSignatureService).verifySignature(any(), eq(signatureBob))

        verify(signedTransaction).addSignature(signatureAlice1)
        verify(updatedSignedTransaction, never()).addSignature(signatureAlice2)
        verify(updatedSignedTransaction).addSignature(signatureBob)

        verify(persistenceService).persist(signedTransaction, TransactionStatus.UNVERIFIED)
        verify(persistenceService).persist(updatedSignedTransaction, TransactionStatus.VERIFIED)

        verify(sessionAlice).sendAndReceive(Payload::class.java, signedTransaction)
        verify(sessionBob).sendAndReceive(Payload::class.java, signedTransaction)
        verify(flowMessaging).sendAllMap(
            mapOf(
                sessionAlice to listOf(signatureBob),
                sessionBob to listOf(signatureAlice1)
            )
        )
    }

    @Test
    fun `receiving a session error instead of signatures rethrows the error`() {
        whenever(sessionAlice.sendAndReceive(Payload::class.java, signedTransaction)).thenReturn(
            Payload.Success(
                listOf(
                    signatureAlice1,
                    signatureAlice2
                )
            )
        )
        whenever(
            sessionBob.sendAndReceive(
                Payload::class.java,
                signedTransaction
            )
        ).thenThrow(CordaRuntimeException("session error"))

        assertThatThrownBy { callFinalityFlow(signedTransaction, listOf(sessionAlice, sessionBob)) }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessage("session error")

        verify(transactionSignatureService, never()).verifySignature(any(), any())

        verify(signedTransaction, never()).addSignature(any())
        verify(updatedSignedTransaction, never()).addSignature(any())
        verify(updatedSignedTransaction, never()).addSignature(any())

        verify(persistenceService).persist(signedTransaction, TransactionStatus.UNVERIFIED)
        verify(persistenceService, never()).persist(updatedSignedTransaction, TransactionStatus.VERIFIED)
    }

    @Test
    fun `receiving a failure payload throws an exception`() {
        whenever(sessionAlice.sendAndReceive(Payload::class.java, signedTransaction)).thenReturn(
            Payload.Success(
                listOf(
                    signatureAlice1,
                    signatureAlice2
                )
            )
        )
        whenever(
            sessionBob.sendAndReceive(
                Payload::class.java,
                signedTransaction
            )
        ).thenReturn(Payload.Failure<DigitalSignatureAndMetadata>("message!", "reason"))

        assertThatThrownBy { callFinalityFlow(signedTransaction, listOf(sessionAlice, sessionBob)) }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessage("Failed to receive signatures from $BOB for transaction ${signedTransaction.id} with message: message!")

        verify(transactionSignatureService).verifySignature(any(), eq(signatureAlice1))
        verify(transactionSignatureService).verifySignature(any(), eq(signatureAlice2))
        verify(transactionSignatureService, never()).verifySignature(any(), eq(signatureBob))

        verify(signedTransaction).addSignature(signatureAlice1)
        verify(updatedSignedTransaction).addSignature(signatureAlice2)
        verify(updatedSignedTransaction, never()).addSignature(signatureBob)

        verify(persistenceService).persist(signedTransaction, TransactionStatus.UNVERIFIED)
        verify(persistenceService, never()).persist(updatedSignedTransaction, TransactionStatus.VERIFIED)
    }

    /* Q: Can it be OK to return no signatures? */
    @Test
    fun `receiving no signatures from a session throws an exception`() {
        whenever(sessionAlice.sendAndReceive(Payload::class.java, signedTransaction)).thenReturn(
            Payload.Success(
                listOf(
                    signatureAlice1,
                    signatureAlice2
                )
            )
        )
        whenever(
            sessionBob.sendAndReceive(
                Payload::class.java,
                signedTransaction
            )
        ).thenReturn(Payload.Success(emptyList<List<DigitalSignatureAndMetadata>>()))

        assertThatThrownBy { callFinalityFlow(signedTransaction, listOf(sessionAlice, sessionBob)) }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessageContaining("Received 0 signatures from $BOB for transaction algo:010203.")

        verify(transactionSignatureService).verifySignature(any(), eq(signatureAlice1))
        verify(transactionSignatureService).verifySignature(any(), eq(signatureAlice2))
        verify(transactionSignatureService, never()).verifySignature(any(), eq(signatureBob))

        verify(signedTransaction).addSignature(signatureAlice1)
        verify(updatedSignedTransaction).addSignature(signatureAlice2)
        verify(updatedSignedTransaction, never()).addSignature(signatureBob)

        verify(persistenceService).persist(signedTransaction, TransactionStatus.UNVERIFIED)
        verify(persistenceService, never()).persist(updatedSignedTransaction, TransactionStatus.VERIFIED)
    }

    @Test
    fun `failing to verify a received signature throws an exception`() {
        whenever(sessionAlice.sendAndReceive(Payload::class.java, signedTransaction)).thenReturn(
            Payload.Success(
                listOf(
                    signatureAlice1,
                    signatureAlice2
                )
            )
        )
        whenever(sessionBob.sendAndReceive(Payload::class.java, signedTransaction)).thenReturn(
            Payload.Success(
                listOf(
                    signatureBob
                )
            )
        )

        whenever(transactionSignatureService.verifySignature(any(), eq(signatureBob))).thenThrow(
            CryptoSignatureException("")
        )

        assertThatThrownBy { callFinalityFlow(signedTransaction, listOf(sessionAlice, sessionBob)) }
            .isInstanceOf(CryptoSignatureException::class.java)

        verify(signedTransaction).addSignature(signatureAlice1)
        verify(updatedSignedTransaction).addSignature(signatureAlice2)
        verify(updatedSignedTransaction, never()).addSignature(signatureBob)

        verify(persistenceService).persist(signedTransaction, TransactionStatus.UNVERIFIED)
        verify(persistenceService, never()).persist(updatedSignedTransaction, TransactionStatus.VERIFIED)
    }

    private fun callFinalityFlow(signedTransaction: UtxoSignedTransactionInternal, sessions: List<FlowSession>) {
        val flow = UtxoFinalityFlow(signedTransaction, sessions)
        flow.transactionSignatureService = transactionSignatureService
        flow.flowMessaging = flowMessaging
        flow.persistenceService = persistenceService
        flow.call()
    }

    private fun digitalSignatureAndMetadata(publicKey: PublicKey, byteArray: ByteArray): DigitalSignatureAndMetadata {
        return DigitalSignatureAndMetadata(
            DigitalSignature.WithKey(publicKey, byteArray, emptyMap()),
            DigitalSignatureMetadata(Instant.now(), emptyMap())
        )
    }
}