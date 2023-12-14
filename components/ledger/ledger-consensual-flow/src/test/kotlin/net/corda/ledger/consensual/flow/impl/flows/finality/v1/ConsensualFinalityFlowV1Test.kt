package net.corda.ledger.consensual.flow.impl.flows.finality.v1

import net.corda.crypto.cipher.suite.SignatureSpecImpl
import net.corda.crypto.core.DigitalSignatureWithKeyId
import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.fullId
import net.corda.crypto.core.fullIdHash
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.common.flow.transaction.TransactionMissingSignaturesException
import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.consensual.data.transaction.ConsensualLedgerTransactionImpl
import net.corda.ledger.consensual.flow.impl.persistence.ConsensualLedgerPersistenceService
import net.corda.ledger.consensual.flow.impl.transaction.ConsensualSignedTransactionInternal
import net.corda.ledger.consensual.testkit.ConsensualStateClassExample
import net.corda.ledger.consensual.testkit.consensualStateExample
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.exceptions.CryptoSignatureException
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.ledger.common.transaction.TransactionSignatureService
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
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

class ConsensualFinalityFlowV1Test {

    private companion object {
        val TX_ID = SecureHashImpl("algo", byteArrayOf(1, 2, 3))
        val ALICE = MemberX500Name("Alice", "London", "GB")
        val BOB = MemberX500Name("Bob", "London", "GB")
    }

    private val flowMessaging = mock<FlowMessaging>()
    private val transactionSignatureService = mock<TransactionSignatureService>()
    private val memberLookup = mock<MemberLookup>()
    private val persistenceService = mock<ConsensualLedgerPersistenceService>()

    private val sessionAlice = mock<FlowSession>()
    private val sessionBob = mock<FlowSession>()

    private val memberInfoAlice = mock<MemberInfo>()
    private val memberInfoBob = mock<MemberInfo>()

    private val publicKey0 = mock<PublicKey>().also { whenever(it.encoded).thenReturn(byteArrayOf(0x01)) }
    private val publicKeyAlice1 = mock<PublicKey>().also { whenever(it.encoded).thenReturn(byteArrayOf(0x02)) }
    private val publicKeyAlice2 = mock<PublicKey>().also { whenever(it.encoded).thenReturn(byteArrayOf(0x03)) }
    private val publicKeyBob = mock<PublicKey>().also { whenever(it.encoded).thenReturn(byteArrayOf(0x04)) }

    private val signature0 = digitalSignatureAndMetadata(publicKey0, byteArrayOf(1, 2, 0))
    private val signatureAlice1 = digitalSignatureAndMetadata(publicKeyAlice1, byteArrayOf(1, 2, 3))
    private val signatureAlice2 = digitalSignatureAndMetadata(publicKeyAlice2, byteArrayOf(1, 2, 4))
    private val signatureBob = digitalSignatureAndMetadata(publicKeyBob, byteArrayOf(1, 2, 5))

    private val signedTransaction = mock<ConsensualSignedTransactionInternal>()
    private val updatedSignedTransaction = mock<ConsensualSignedTransactionInternal>()
    private val transactionMetadata = mock<TransactionMetadata>()
    private val wireTransaction = mock<WireTransaction>()
    private val ledgerTransaction = mock<ConsensualLedgerTransaction>()

    @BeforeEach
    fun beforeEach() {
        whenever(sessionAlice.counterparty).thenReturn(ALICE)
        whenever(sessionBob.counterparty).thenReturn(BOB)

        whenever(memberLookup.lookup(ALICE)).thenReturn(memberInfoAlice)
        whenever(memberLookup.lookup(BOB)).thenReturn(memberInfoBob)

        whenever(memberInfoAlice.ledgerKeys).thenReturn(listOf(publicKeyAlice1, publicKeyAlice2))
        whenever(memberInfoBob.ledgerKeys).thenReturn(listOf(publicKeyBob))

        whenever(signedTransaction.id).thenReturn(TX_ID)
        whenever(signedTransaction.getMissingSignatories()).thenReturn(setOf(publicKeyAlice1, publicKeyAlice2, publicKeyBob))
        whenever(signedTransaction.addSignature(any())).thenReturn(updatedSignedTransaction)
        whenever(signedTransaction.toLedgerTransaction()).thenReturn(ledgerTransaction)
        whenever(signedTransaction.signatures).thenReturn(listOf(signature0))
        whenever(signedTransaction.wireTransaction).thenReturn(wireTransaction)

        whenever(updatedSignedTransaction.id).thenReturn(TX_ID)
        whenever(updatedSignedTransaction.addSignature(any())).thenReturn(updatedSignedTransaction)
        whenever(wireTransaction.metadata).thenReturn(transactionMetadata)
        whenever(transactionMetadata.getLedgerModel()).thenReturn(ConsensualLedgerTransactionImpl::class.java.name)

        whenever(ledgerTransaction.id).thenReturn(TX_ID)
        whenever(ledgerTransaction.states).thenReturn(listOf(consensualStateExample))
        whenever(ledgerTransaction.requiredSignatories).thenReturn(setOf(publicKeyExample))
    }

    @Test
    fun `receiving valid signatures over a transaction leads to it being recorded and distributed for recording to passed in sessions`() {
        whenever(signedTransaction.getMissingSignatories()).thenReturn(setOf(publicKeyAlice1, publicKeyAlice2, publicKeyBob))

        whenever(sessionAlice.receive(Payload::class.java)).thenReturn(Payload.Success(listOf(signatureAlice1, signatureAlice2)))
        whenever(sessionBob.receive(Payload::class.java)).thenReturn(Payload.Success(listOf(signatureBob)))

        val signedTransactionAfterSigAlice1 = mock<ConsensualSignedTransactionInternal>()
        whenever(signedTransaction.addSignature(eq(signatureAlice1))).thenReturn(signedTransactionAfterSigAlice1)
        val signedTransactionAfterSigAlice2 = mock<ConsensualSignedTransactionInternal>()
        whenever(signedTransactionAfterSigAlice1.addSignature(eq(signatureAlice2))).thenReturn(signedTransactionAfterSigAlice2)
        val signedTransactionAfterSigBob = mock<ConsensualSignedTransactionInternal>()
        whenever(signedTransactionAfterSigAlice2.addSignature(eq(signatureBob))).thenReturn(signedTransactionAfterSigBob)

        whenever(signedTransactionAfterSigBob.signatures).thenReturn(listOf(signatureAlice1, signatureAlice2, signatureBob))

        callFinalityFlow(signedTransaction, listOf(sessionAlice, sessionBob))

        verify(signedTransaction).verifySignature(eq(signatureAlice1))
        verify(signedTransactionAfterSigAlice1).verifySignature(eq(signatureAlice2))
        verify(signedTransactionAfterSigAlice2).verifySignature(eq(signatureBob))

        verify(signedTransaction).addSignature(signatureAlice1)
        verify(signedTransactionAfterSigAlice1).addSignature(signatureAlice2)
        verify(signedTransactionAfterSigAlice2).addSignature(signatureBob)

        verify(persistenceService).persist(signedTransactionAfterSigBob, TransactionStatus.VERIFIED)

        verify(flowMessaging).sendAllMap(
            mapOf(
                sessionAlice to listOf(signatureBob),
                sessionBob to listOf(signatureAlice1, signatureAlice2)
            )
        )
    }

    @Test
    fun `called with a transaction initially without signatures throws and persists as invalid`() {
        whenever(signedTransaction.signatures).thenReturn(listOf())
        assertThatThrownBy { callFinalityFlow(signedTransaction, listOf(sessionAlice, sessionBob)) }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessageContaining("Received initial transaction without signatures.")

        verify(signedTransaction, never()).addMissingSignatures()
        verify(persistenceService).persist(signedTransaction, TransactionStatus.INVALID)
    }

    @Test
    fun `called with a transaction initially with invalid signature throws and persists as invalid`() {
        whenever(signedTransaction.verifySignature(any())).thenThrow(
            CryptoSignatureException("Verifying signature failed!!")
        )
        assertThatThrownBy { callFinalityFlow(signedTransaction, listOf(sessionAlice, sessionBob)) }
            .isInstanceOf(CryptoSignatureException::class.java)
            .hasMessageContaining("Verifying signature failed!!")

        verify(signedTransaction, never()).addMissingSignatures()
        verify(persistenceService).persist(signedTransaction, TransactionStatus.INVALID)
    }

    @Test
    fun `called with an invalid transaction initially throws and persists as invalid`() {
        whenever(ledgerTransaction.states).thenReturn(
            listOf(
                ConsensualStateClassExample(
                    "throw",
                    listOf(
                        publicKeyExample
                    )
                )
            )
        )

        assertThatThrownBy { callFinalityFlow(signedTransaction, listOf(sessionAlice, sessionBob)) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("State verification failed")

        verify(signedTransaction, never()).addMissingSignatures()
        verify(persistenceService).persist(signedTransaction, TransactionStatus.INVALID)
    }

    @Test
    fun `receiving a session error instead of signatures rethrows the error`() {
        whenever(sessionAlice.receive(Payload::class.java)).thenReturn(Payload.Success(listOf(signatureAlice1, signatureAlice2)))
        whenever(sessionBob.receive(Payload::class.java)).thenThrow(CordaRuntimeException("session error"))

        assertThatThrownBy { callFinalityFlow(signedTransaction, listOf(sessionAlice, sessionBob)) }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessage("session error")

        verify(transactionSignatureService, never()).verifySignature(eq(updatedSignedTransaction), any(), any())

        verify(signedTransaction, never()).addSignature(signatureAlice1)
        verify(updatedSignedTransaction, never()).addSignature(signatureAlice2)
        verify(updatedSignedTransaction, never()).addSignature(signatureBob)

        verify(persistenceService, never()).persist(updatedSignedTransaction, TransactionStatus.VERIFIED)
        verify(persistenceService).persist(signedTransaction, TransactionStatus.INVALID)
    }

    @Test
    fun `receiving a failure payload throws an exception`() {
        whenever(sessionAlice.receive(Payload::class.java)).thenReturn(Payload.Success(listOf(signatureAlice1, signatureAlice2)))
        whenever(sessionBob.receive(Payload::class.java)).thenReturn(Payload.Failure<DigitalSignatureAndMetadata>("message!", "reason"))

        val signedTransactionAfterSigAlice1 = mock<ConsensualSignedTransactionInternal>()
        whenever(signedTransaction.addSignature(eq(signatureAlice1))).thenReturn(signedTransactionAfterSigAlice1)
        val signedTransactionAfterSigAlice2 = mock<ConsensualSignedTransactionInternal>()
        whenever(signedTransactionAfterSigAlice1.addSignature(eq(signatureAlice2))).thenReturn(signedTransactionAfterSigAlice2)

        assertThatThrownBy { callFinalityFlow(signedTransaction, listOf(sessionAlice, sessionBob)) }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessage("Failed to receive signatures from $BOB for transaction ${signedTransaction.id} with message: message!")

        verify(signedTransaction).verifySignature(eq(signatureAlice1))
        verify(signedTransactionAfterSigAlice1).verifySignature(eq(signatureAlice2))
        verify(signedTransactionAfterSigAlice2, never()).verifySignature(eq(signatureBob))

        verify(signedTransaction).addSignature(signatureAlice1)
        verify(signedTransactionAfterSigAlice1).addSignature(signatureAlice2)
        verify(signedTransactionAfterSigAlice2, never()).addSignature(signatureBob)

        verify(persistenceService).persist(signedTransaction, TransactionStatus.UNVERIFIED)
        verify(persistenceService, never()).persist(signedTransactionAfterSigAlice1, TransactionStatus.VERIFIED)
        verify(persistenceService).persist(signedTransaction, TransactionStatus.INVALID)
    }

    @Test
    fun `failing to verify a received signature throws an exception`() {
        whenever(sessionAlice.receive(Payload::class.java)).thenReturn(Payload.Success(listOf(signatureAlice1, signatureAlice2)))
        whenever(sessionBob.receive(Payload::class.java)).thenReturn(Payload.Success(listOf(signatureBob)))

        val signedTransactionAfterSigAlice1 = mock<ConsensualSignedTransactionInternal>()
        whenever(signedTransaction.addSignature(eq(signatureAlice1))).thenReturn(signedTransactionAfterSigAlice1)
        val signedTransactionAfterSigAlice2 = mock<ConsensualSignedTransactionInternal>()
        whenever(signedTransactionAfterSigAlice1.addSignature(eq(signatureAlice2))).thenReturn(signedTransactionAfterSigAlice2)

        whenever(signedTransactionAfterSigAlice2.verifySignature(eq(signatureBob))).thenThrow(
            CryptoSignatureException("")
        )

        assertThatThrownBy { callFinalityFlow(signedTransaction, listOf(sessionAlice, sessionBob)) }
            .isInstanceOf(CryptoSignatureException::class.java)

        verify(signedTransaction).addSignature(signatureAlice1)
        verify(signedTransactionAfterSigAlice1).addSignature(signatureAlice2)
        verify(signedTransactionAfterSigAlice2, never()).addSignature(signatureBob)

        verify(persistenceService, never()).persist(signedTransactionAfterSigAlice2, TransactionStatus.VERIFIED)
        verify(persistenceService).persist(signedTransactionAfterSigAlice2, TransactionStatus.INVALID)
    }

    @Test
    fun `missing signatures when verifying all signatures rethrows exception with useful message`() {
        val aliceSignatures = listOf(signatureAlice1, signatureAlice2)

        whenever(sessionAlice.receive(Payload::class.java)).thenReturn(Payload.Success(aliceSignatures))
        whenever(sessionBob.receive(Payload::class.java)).thenReturn(
            Payload.Success(
                emptyList<Payload<DigitalSignatureAndMetadata>>()
            )
        )

        whenever(updatedSignedTransaction.verifySignatures()).thenThrow(
            TransactionMissingSignaturesException(TX_ID, setOf(publicKeyBob), "missing")
        )

        assertThatThrownBy { callFinalityFlow(signedTransaction, listOf(sessionAlice, sessionBob)) }
            .isInstanceOf(TransactionMissingSignaturesException::class.java)
            .hasMessageContainingAll(
                "Transaction $TX_ID is missing signatures for signatories (key ids) ${setOf(publicKeyBob).map { it.fullId() }}. ",
                "The following counterparties provided signatures while finalizing the transaction:",
                "$ALICE provided 2 signature(s) to satisfy the signatories (key ids) ${aliceSignatures.map { it.by }}",
                "$BOB provided 0 signature(s) to satisfy the signatories (key ids) []"
            )

        verify(signedTransaction).addSignature(signatureAlice1)
        verify(updatedSignedTransaction).addSignature(signatureAlice2)
        verify(updatedSignedTransaction, never()).addSignature(signatureBob)

        verify(persistenceService).persist(signedTransaction, TransactionStatus.UNVERIFIED)
        verify(persistenceService, never()).persist(any(), eq(TransactionStatus.VERIFIED))
        verify(persistenceService).persist(updatedSignedTransaction, TransactionStatus.INVALID)
    }

    @Test
    fun `failing to verify all signatures throws exception`() {
        whenever(sessionAlice.receive(Payload::class.java)).thenReturn(Payload.Success(listOf(signatureAlice1, signatureAlice2)))
        whenever(sessionBob.receive(Payload::class.java)).thenReturn(Payload.Success(listOf(signatureBob)))

        whenever(updatedSignedTransaction.verifySignatures()).thenThrow(
            TransactionMissingSignaturesException(
                TX_ID,
                setOf(),
                "failed"
            )
        )

        assertThatThrownBy { callFinalityFlow(signedTransaction, listOf(sessionAlice, sessionBob)) }
            .isInstanceOf(TransactionMissingSignaturesException::class.java)
            .hasMessageContaining("is missing signatures for signatories")

        verify(signedTransaction).addSignature(signatureAlice1)
        verify(updatedSignedTransaction).addSignature(signatureAlice2)
        verify(updatedSignedTransaction).addSignature(signatureBob)

        verify(persistenceService).persist(signedTransaction, TransactionStatus.UNVERIFIED)
        verify(persistenceService, never()).persist(any(), eq(TransactionStatus.VERIFIED))
        verify(persistenceService).persist(updatedSignedTransaction, TransactionStatus.INVALID)
    }

    private fun callFinalityFlow(signedTransaction: ConsensualSignedTransactionInternal, sessions: List<FlowSession>) {
        val flow = ConsensualFinalityFlowV1(signedTransaction, sessions)
        flow.flowMessaging = flowMessaging
        flow.memberLookup = memberLookup
        flow.persistenceService = persistenceService
        flow.call()
    }

    private fun digitalSignatureAndMetadata(publicKey: PublicKey, byteArray: ByteArray): DigitalSignatureAndMetadata {
        return DigitalSignatureAndMetadata(
            DigitalSignatureWithKeyId(publicKey.fullIdHash(), byteArray),
            DigitalSignatureMetadata(Instant.now(), SignatureSpecImpl("dummySignatureName"), emptyMap())
        )
    }
}
