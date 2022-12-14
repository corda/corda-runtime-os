package net.corda.ledger.utxo.flow.impl.flows.finality

import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.common.flow.transaction.TransactionSignatureService
import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainSenderFlow
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.exceptions.CryptoSignatureException
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.corda.v5.membership.MemberInfo
import net.corda.v5.serialization.SerializedBytes
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
    private val memberLookup = mock<MemberLookup>()
    private val persistenceService = mock<UtxoLedgerPersistenceService>()
    private val serializationService = mock<SerializationService>()
    private val flowEngine = mock<FlowEngine>()

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
    private val ledgerTransaction = mock<UtxoLedgerTransaction>()

    @BeforeEach
    fun beforeEach() {
        whenever(sessionAlice.counterparty).thenReturn(ALICE)
        whenever(sessionAlice.receive(Unit::class.java)).thenReturn(Unit)
        whenever(sessionBob.counterparty).thenReturn(BOB)
        whenever(sessionBob.receive(Unit::class.java)).thenReturn(Unit)

        whenever(memberLookup.lookup(ALICE)).thenReturn(memberInfoAlice)
        whenever(memberLookup.lookup(BOB)).thenReturn(memberInfoBob)

        whenever(memberInfoAlice.ledgerKeys).thenReturn(listOf(publicKeyAlice1, publicKeyAlice2))
        whenever(memberInfoBob.ledgerKeys).thenReturn(listOf(publicKeyBob))

        whenever(flowEngine.subFlow(any<TransactionBackchainSenderFlow>())).thenReturn(Unit)

        whenever(signedTransaction.id).thenReturn(SecureHash("algo", byteArrayOf(1, 2, 3)))
        whenever(signedTransaction.getMissingSignatories()).thenReturn(setOf(publicKeyAlice1, publicKeyAlice2, publicKeyBob))
        whenever(signedTransaction.toLedgerTransaction()).thenReturn(ledgerTransaction)
        whenever(signedTransaction.addSignature(any<DigitalSignatureAndMetadata>())).thenReturn(updatedSignedTransaction)
        whenever(updatedSignedTransaction.addSignature(any<DigitalSignatureAndMetadata>())).thenReturn(updatedSignedTransaction)

        whenever(serializationService.serialize(any())).thenReturn(SerializedBytes(byteArrayOf(1, 2, 3, 4)))
    }

    @Test
    fun `receiving valid signatures over a transaction leads to it being recorded and distributed for recording to passed in sessions`() {
        whenever(signedTransaction.getMissingSignatories()).thenReturn(setOf(publicKeyAlice1, publicKeyAlice2, publicKeyBob))

        whenever(sessionAlice.receive(Payload::class.java)).thenReturn(Payload.Success(listOf(signatureAlice1, signatureAlice2)))
        whenever(sessionBob.receive(Payload::class.java)).thenReturn(Payload.Success(listOf(signatureBob)))

        callFinalityFlow(signedTransaction, listOf(sessionAlice, sessionBob))

        verify(transactionSignatureService).verifySignature(any(), eq(signatureAlice1))
        verify(transactionSignatureService).verifySignature(any(), eq(signatureAlice2))
        verify(transactionSignatureService).verifySignature(any(), eq(signatureBob))

        verify(signedTransaction).addSignature(signatureAlice1)
        verify(updatedSignedTransaction).addSignature(signatureAlice2)
        verify(updatedSignedTransaction).addSignature(signatureBob)

        verify(persistenceService).persist(updatedSignedTransaction, TransactionStatus.VERIFIED)

        verify(sessionAlice).send(signedTransaction)
        verify(sessionAlice).send(updatedSignedTransaction)
        verify(sessionBob).send(signedTransaction)
        verify(sessionBob).send(updatedSignedTransaction)
    }

    @Test
    fun `missing member throws exception`() {
        whenever(memberLookup.lookup(BOB)).thenReturn(null)
        assertThatThrownBy { callFinalityFlow(signedTransaction, listOf(sessionAlice, sessionBob)) }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessage("A session with $BOB exists but the member no longer exists in the membership group")

        verify(transactionSignatureService, never()).verifySignature(any(), eq(signatureAlice1))
        verify(transactionSignatureService, never()).verifySignature(any(), eq(signatureAlice2))
        verify(transactionSignatureService, never()).verifySignature(any(), eq(signatureBob))

        verify(signedTransaction, never()).addSignature(signatureAlice1)
        verify(updatedSignedTransaction, never()).addSignature(signatureAlice2)
        verify(updatedSignedTransaction, never()).addSignature(signatureBob)

        verify(persistenceService, never()).persist(updatedSignedTransaction, TransactionStatus.VERIFIED)
    }

    @Test
    fun `member having no ledger keys throws exception`() {
        whenever(memberInfoBob.ledgerKeys).thenReturn(emptyList())
        assertThatThrownBy { callFinalityFlow(signedTransaction, listOf(sessionAlice, sessionBob)) }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessage("A session with $BOB exists but the member does not have any active ledger keys")

        verify(transactionSignatureService, never()).verifySignature(any(), eq(signatureAlice1))
        verify(transactionSignatureService, never()).verifySignature(any(), eq(signatureAlice2))
        verify(transactionSignatureService, never()).verifySignature(any(), eq(signatureBob))

        verify(signedTransaction, never()).addSignature(signatureAlice1)
        verify(updatedSignedTransaction, never()).addSignature(signatureAlice2)
        verify(updatedSignedTransaction, never()).addSignature(signatureBob)

        verify(persistenceService, never()).persist(updatedSignedTransaction, TransactionStatus.VERIFIED)
    }

    @Test
    fun `ledger keys from the passed in sessions not satisfying all the missing signatories throws an exception`() {
        val missingSignatories = setOf(publicKeyAlice1, publicKeyBob)
        whenever(signedTransaction.getMissingSignatories()).thenReturn(missingSignatories)

        assertThatThrownBy { callFinalityFlow(signedTransaction, listOf(sessionAlice)) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Required signatures $missingSignatories but ledger keys for the passed in sessions are")

        verify(transactionSignatureService, never()).verifySignature(any(), eq(signatureAlice1))
        verify(transactionSignatureService, never()).verifySignature(any(), eq(signatureAlice2))
        verify(transactionSignatureService, never()).verifySignature(any(), eq(signatureBob))

        verify(signedTransaction, never()).addSignature(signatureAlice1)
        verify(updatedSignedTransaction, never()).addSignature(signatureAlice2)
        verify(updatedSignedTransaction, never()).addSignature(signatureBob)

        verify(persistenceService, never()).persist(updatedSignedTransaction, TransactionStatus.VERIFIED)
    }

    @Test
    fun `ledger keys from the passed in sessions can contain more than the missing signatories`() {
        whenever(memberInfoAlice.ledgerKeys).thenReturn(listOf(publicKeyAlice1, publicKeyAlice2))

        whenever(signedTransaction.getMissingSignatories()).thenReturn(setOf(publicKeyAlice1, publicKeyBob))

        whenever(sessionAlice.receive(Payload::class.java)).thenReturn(Payload.Success(listOf(signatureAlice1)))
        whenever(sessionBob.receive(Payload::class.java)).thenReturn(Payload.Success(listOf(signatureBob)))

        callFinalityFlow(signedTransaction, listOf(sessionAlice, sessionBob))

        verify(transactionSignatureService).verifySignature(any(), eq(signatureAlice1))
        verify(transactionSignatureService, never()).verifySignature(any(), eq(signatureAlice2))
        verify(transactionSignatureService).verifySignature(any(), eq(signatureBob))

        verify(signedTransaction).addSignature(signatureAlice1)
        verify(updatedSignedTransaction, never()).addSignature(signatureAlice2)
        verify(updatedSignedTransaction).addSignature(signatureBob)

        verify(persistenceService).persist(updatedSignedTransaction, TransactionStatus.VERIFIED)

        verify(sessionAlice).send(signedTransaction)
        verify(sessionAlice).send(updatedSignedTransaction)
        verify(sessionBob).send(signedTransaction)
        verify(sessionBob).send(updatedSignedTransaction)
    }

    @Test
    fun `receiving a session error instead of signatures rethrows the error`() {
        whenever(sessionAlice.receive(Payload::class.java)).thenReturn(Payload.Success(listOf(signatureAlice1, signatureAlice2)))
        whenever(sessionBob.receive(Payload::class.java)).thenThrow(CordaRuntimeException("session error"))

        assertThatThrownBy { callFinalityFlow(signedTransaction, listOf(sessionAlice, sessionBob)) }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessage("session error")

        verify(transactionSignatureService).verifySignature(any(), eq(signatureAlice1))
        verify(transactionSignatureService).verifySignature(any(), eq(signatureAlice2))
        verify(transactionSignatureService, never()).verifySignature(any(), eq(signatureBob))

        verify(signedTransaction).addSignature(signatureAlice1)
        verify(updatedSignedTransaction).addSignature(signatureAlice2)
        verify(updatedSignedTransaction, never()).addSignature(signatureBob)

        verify(persistenceService, never()).persist(updatedSignedTransaction, TransactionStatus.VERIFIED)
    }

    @Test
    fun `receiving a failure payload throws an exception`() {
        whenever(sessionAlice.receive(Payload::class.java)).thenReturn(Payload.Success(listOf(signatureAlice1, signatureAlice2)))
        whenever(sessionBob.receive(Payload::class.java)).thenReturn(Payload.Failure<DigitalSignatureAndMetadata>("message!", "reason"))

        assertThatThrownBy { callFinalityFlow(signedTransaction, listOf(sessionAlice, sessionBob)) }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessage("Failed to receive signature from $BOB for signed transaction ${signedTransaction.id} with message: message!")

        verify(transactionSignatureService).verifySignature(any(), eq(signatureAlice1))
        verify(transactionSignatureService).verifySignature(any(), eq(signatureAlice2))
        verify(transactionSignatureService, never()).verifySignature(any(), eq(signatureBob))

        verify(signedTransaction).addSignature(signatureAlice1)
        verify(updatedSignedTransaction).addSignature(signatureAlice2)
        verify(updatedSignedTransaction, never()).addSignature(signatureBob)

        verify(persistenceService, never()).persist(updatedSignedTransaction, TransactionStatus.VERIFIED)
    }

    @Test
    fun `receiving a signature from a session that is not contained in the required signatories throws an exception`() {
        whenever(signedTransaction.getMissingSignatories()).thenReturn(setOf(publicKeyAlice1, publicKeyAlice2, publicKeyBob))

        whenever(sessionAlice.receive(Payload::class.java)).thenReturn(Payload.Success(listOf(signatureAlice1, signatureAlice2)))
        whenever(sessionBob.receive(Payload::class.java))
            .thenReturn(Payload.Success(listOf(signatureBob, digitalSignatureAndMetadata(mock(), byteArrayOf(1)))))

        assertThatThrownBy { callFinalityFlow(signedTransaction, listOf(sessionAlice, sessionBob)) }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessageContaining("A session with $BOB did not return the signatures with the expected keys")

        verify(transactionSignatureService).verifySignature(any(), eq(signatureAlice1))
        verify(transactionSignatureService).verifySignature(any(), eq(signatureAlice2))
        verify(transactionSignatureService, never()).verifySignature(any(), eq(signatureBob))

        verify(signedTransaction).addSignature(signatureAlice1)
        verify(updatedSignedTransaction).addSignature(signatureAlice2)
        verify(updatedSignedTransaction, never()).addSignature(signatureBob)

        verify(persistenceService, never()).persist(updatedSignedTransaction, TransactionStatus.VERIFIED)
    }

    @Test
    fun `receiving no signatures from a session throws an exception`() {
        whenever(sessionAlice.receive(Payload::class.java)).thenReturn(Payload.Success(listOf(signatureAlice1, signatureAlice2)))
        whenever(sessionBob.receive(Payload::class.java)).thenReturn(Payload.Success(emptyList<List<DigitalSignatureAndMetadata>>()))

        assertThatThrownBy { callFinalityFlow(signedTransaction, listOf(sessionAlice, sessionBob)) }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessageContaining("A session with $BOB did not return the signatures with the expected keys")

        verify(transactionSignatureService).verifySignature(any(), eq(signatureAlice1))
        verify(transactionSignatureService).verifySignature(any(), eq(signatureAlice2))
        verify(transactionSignatureService, never()).verifySignature(any(), eq(signatureBob))

        verify(signedTransaction).addSignature(signatureAlice1)
        verify(updatedSignedTransaction).addSignature(signatureAlice2)
        verify(updatedSignedTransaction, never()).addSignature(signatureBob)

        verify(persistenceService, never()).persist(updatedSignedTransaction, TransactionStatus.VERIFIED)
    }

    @Test
    fun `failing to verify a received signature throws an exception`() {
        whenever(sessionAlice.receive(Payload::class.java)).thenReturn(Payload.Success(listOf(signatureAlice1, signatureAlice2)))
        whenever(sessionBob.receive(Payload::class.java)).thenReturn(Payload.Success(listOf(signatureBob)))

        whenever(transactionSignatureService.verifySignature(any(), eq(signatureBob))).thenThrow(CryptoSignatureException(""))

        assertThatThrownBy { callFinalityFlow(signedTransaction, listOf(sessionAlice, sessionBob)) }
            .isInstanceOf(CryptoSignatureException::class.java)

        verify(signedTransaction).addSignature(signatureAlice1)
        verify(updatedSignedTransaction).addSignature(signatureAlice2)
        verify(updatedSignedTransaction, never()).addSignature(signatureBob)

        verify(persistenceService, never()).persist(updatedSignedTransaction, TransactionStatus.VERIFIED)
    }

    @Test
    fun `receiving a session error instead of an acknowledgement of Unit after distributing the transaction throws an exception`() {
        whenever(sessionAlice.receive(Payload::class.java)).thenReturn(Payload.Success(listOf(signatureAlice1, signatureAlice2)))
        whenever(sessionBob.receive(Payload::class.java)).thenReturn(Payload.Success(listOf(signatureBob)))

        whenever(sessionBob.receive(Unit::class.java)).thenThrow(CordaRuntimeException("session error"))

        assertThatThrownBy { callFinalityFlow(signedTransaction, listOf(sessionAlice, sessionBob)) }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessage("session error")

        verify(transactionSignatureService).verifySignature(any(), eq(signatureAlice1))
        verify(transactionSignatureService).verifySignature(any(), eq(signatureAlice2))
        verify(transactionSignatureService).verifySignature(any(), eq(signatureBob))

        verify(signedTransaction).addSignature(signatureAlice1)
        verify(updatedSignedTransaction).addSignature(signatureAlice2)
        verify(updatedSignedTransaction).addSignature(signatureBob)

        verify(persistenceService).persist(updatedSignedTransaction, TransactionStatus.VERIFIED)
    }

    @Test
    fun `each passed in session is sent the transaction backchain`() {
        whenever(signedTransaction.getMissingSignatories()).thenReturn(setOf(publicKeyAlice1, publicKeyAlice2, publicKeyBob))

        whenever(sessionAlice.receive(Payload::class.java)).thenReturn(Payload.Success(listOf(signatureAlice1, signatureAlice2)))
        whenever(sessionBob.receive(Payload::class.java)).thenReturn(Payload.Success(listOf(signatureBob)))

        callFinalityFlow(signedTransaction, listOf(sessionAlice, sessionBob))

        verify(flowEngine).subFlow(TransactionBackchainSenderFlow(sessionAlice))
        verify(flowEngine).subFlow(TransactionBackchainSenderFlow(sessionBob))
    }
    
    private fun callFinalityFlow(signedTransaction: UtxoSignedTransactionInternal, sessions: List<FlowSession>) {
        val flow = UtxoFinalityFlow(signedTransaction, sessions)
        flow.transactionSignatureService = transactionSignatureService
        flow.memberLookup = memberLookup
        flow.persistenceService = persistenceService
        flow.serializationService = serializationService
        flow.flowEngine = flowEngine
        flow.call()
    }

    private fun digitalSignatureAndMetadata(publicKey: PublicKey, byteArray: ByteArray): DigitalSignatureAndMetadata {
        return DigitalSignatureAndMetadata(
            DigitalSignature.WithKey(publicKey, byteArray, emptyMap()),
            DigitalSignatureMetadata(Instant.now(), emptyMap())
        )
    }
}