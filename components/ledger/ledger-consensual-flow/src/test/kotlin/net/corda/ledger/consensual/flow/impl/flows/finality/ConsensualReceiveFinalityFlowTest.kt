package net.corda.ledger.consensual.flow.impl.flows.finality

import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.consensual.data.transaction.ConsensualLedgerTransactionImpl
import net.corda.ledger.consensual.flow.impl.persistence.ConsensualLedgerPersistenceService
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.v5.ledger.common.transaction.TransactionSignatureService
import net.corda.ledger.consensual.flow.impl.transaction.ConsensualSignedTransactionInternal
import net.corda.ledger.consensual.testkit.consensualStateExample
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.ledger.common.transaction.TransactionVerificationException
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionValidator
import net.corda.v5.membership.MemberInfo
import net.corda.v5.serialization.SerializedBytes
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.FileNotFoundException
import java.security.PublicKey
import java.time.Instant

@Suppress("MaxLineLength")
class ConsensualReceiveFinalityFlowTest {

    private companion object {
        val MEMBER = MemberX500Name("Alice", "London", "GB")
        val ID = SecureHash("algo", byteArrayOf(1, 2, 3))
    }

    private val memberLookup = mock<MemberLookup>()
    private val persistenceService = mock<ConsensualLedgerPersistenceService>()
    private val serializationService = mock<SerializationService>()
    private val transactionSignatureService = mock<TransactionSignatureService>()

    private val session = mock<FlowSession>()

    private val memberInfo = mock<MemberInfo>()

    private val publicKey1 = mock<PublicKey>()
    private val publicKey2 = mock<PublicKey>()
    private val publicKey3 = mock<PublicKey>()

    private val signature1 = digitalSignatureAndMetadata(publicKey1, byteArrayOf(1, 2, 2))
    private val signature2 = digitalSignatureAndMetadata(publicKey2, byteArrayOf(1, 2, 3))
    private val signature3 = digitalSignatureAndMetadata(publicKey3, byteArrayOf(1, 2, 4))

    private val transactionMetadata = mock<TransactionMetadata>()
    private val wireTransaction = mock<WireTransaction>()
    private val ledgerTransaction = mock<ConsensualLedgerTransactionImpl>()
    private val signedTransaction = mock<ConsensualSignedTransactionInternal>()

    @BeforeEach
    fun beforeEach() {
        whenever(session.counterparty).thenReturn(MEMBER)
        whenever(session.receive(ConsensualSignedTransactionInternal::class.java)).thenReturn(signedTransaction)

        whenever(memberLookup.myInfo()).thenReturn(memberInfo)

        whenever(memberInfo.ledgerKeys).thenReturn(listOf(publicKey1, publicKey2))

        whenever(signedTransaction.id).thenReturn(ID)
        whenever(wireTransaction.metadata).thenReturn(transactionMetadata)
        whenever(signedTransaction.wireTransaction).thenReturn(wireTransaction)
        whenever(signedTransaction.toLedgerTransaction()).thenReturn(ledgerTransaction)
        whenever(signedTransaction.addMissingSignatures()).thenReturn(signedTransaction to listOf(signature1, signature2))
        whenever(signedTransaction.addSignature(signature3)).thenReturn(signedTransaction)
        whenever(signedTransaction.signatures).thenReturn(listOf(signature1, signature2))

        whenever(ledgerTransaction.states).thenReturn(listOf(consensualStateExample))
        whenever(ledgerTransaction.requiredSignatories).thenReturn(setOf(publicKeyExample))

        whenever(serializationService.serialize(any())).thenReturn(SerializedBytes(byteArrayOf(1, 2, 3, 4)))
    }

    @Test
    fun `receiving a transaction that passes verification is signed and recorded`() {
        whenever(signedTransaction.addMissingSignatures()).thenReturn(signedTransaction to listOf(signature1, signature2))
        whenever(session.receive(List::class.java)).thenReturn(listOf(signature3))

        callReceiveFinalityFlow()

        verify(signedTransaction).addMissingSignatures()
        verify(signedTransaction).addSignature(signature3)
        verify(persistenceService).persist(signedTransaction, TransactionStatus.UNVERIFIED)
        verify(persistenceService).persist(signedTransaction, TransactionStatus.VERIFIED)
        verify(session).send(Payload.Success(listOf(signature1, signature2)))
    }

    @Test
    fun `the received transaction is only signed with ledger keys in the transaction's missing signatories`() {
        whenever(signedTransaction.addMissingSignatures()).thenReturn(signedTransaction to listOf(signature1))
        whenever(session.receive(List::class.java)).thenReturn(listOf(signature3))

        callReceiveFinalityFlow()

        verify(signedTransaction).addMissingSignatures()
        verify(persistenceService).persist(signedTransaction, TransactionStatus.UNVERIFIED)
        verify(persistenceService).persist(signedTransaction, TransactionStatus.VERIFIED)
        verify(session).send(Payload.Success(listOf(signature1)))
    }

    @Test
    fun `receiving a transaction that fails validation with an IllegalArgumentException sends a failure payload and throws an exception`() {
        assertThatThrownBy { callReceiveFinalityFlow { throw IllegalArgumentException() } }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessageContaining("Transaction validation failed for transaction")

        verify(session).send(any<Payload.Failure<List<DigitalSignatureAndMetadata>>>())
        verify(persistenceService, never()).persist(signedTransaction, TransactionStatus.UNVERIFIED)
        verify(persistenceService).persist(signedTransaction, TransactionStatus.INVALID)
        verify(persistenceService, never()).persist(signedTransaction, TransactionStatus.VERIFIED)
    }

    @Test
    fun `receiving a transaction that fails validation with an IllegalStateException sends a failure payload and throws an exception`() {
        assertThatThrownBy { callReceiveFinalityFlow { throw IllegalStateException() } }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessageContaining("Transaction validation failed for transaction")

        verify(session).send(any<Payload.Failure<List<DigitalSignatureAndMetadata>>>())
        verify(persistenceService).persist(signedTransaction, TransactionStatus.INVALID)
        verify(persistenceService, never()).persist(signedTransaction, TransactionStatus.UNVERIFIED)
        verify(persistenceService, never()).persist(signedTransaction, TransactionStatus.VERIFIED)
    }

    @Test
    fun `receiving a transaction that fails validation with a CordaRuntimeException sends a failure payload and throws an exception`() {
        assertThatThrownBy { callReceiveFinalityFlow { throw CordaRuntimeException("") } }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessageContaining("Transaction validation failed for transaction")

        verify(session).send(any<Payload.Failure<List<DigitalSignatureAndMetadata>>>())
        verify(persistenceService).persist(signedTransaction, TransactionStatus.INVALID)
        verify(persistenceService, never()).persist(signedTransaction, TransactionStatus.UNVERIFIED)
        verify(persistenceService, never()).persist(signedTransaction, TransactionStatus.VERIFIED)
    }

    @Test
    fun `receiving a transaction that throws an unexpected exception during validation throws an exception`() {
        assertThatThrownBy { callReceiveFinalityFlow { throw FileNotFoundException("message!") } }
            .isInstanceOf(FileNotFoundException::class.java)
            .hasMessage("message!")

        verify(session, never()).send(any<Payload.Failure<List<DigitalSignatureAndMetadata>>>())
        verify(persistenceService, never()).persist(signedTransaction, TransactionStatus.INVALID)
        verify(persistenceService, never()).persist(signedTransaction, TransactionStatus.UNVERIFIED)
        verify(persistenceService, never()).persist(signedTransaction, TransactionStatus.VERIFIED)
    }

    @Test
    fun `the received transaction does not have to be signed by the local member to be recorded`() {
        whenever(signedTransaction.addMissingSignatures()).thenReturn(signedTransaction to listOf())
        whenever(session.receive(List::class.java)).thenReturn(listOf(signature3))

        callReceiveFinalityFlow()

        verify(signedTransaction).addMissingSignatures()
        verify(session).send(Payload.Success(emptyList<DigitalSignatureAndMetadata>()))
        verify(persistenceService).persist(signedTransaction, TransactionStatus.UNVERIFIED)
        verify(persistenceService).persist(signedTransaction, TransactionStatus.VERIFIED)
    }

    @Test
    fun `receiving a transaction to record that is not fully signed throws an exception`() {
        whenever(signedTransaction.verifySignatures()).thenThrow(TransactionVerificationException(ID, "There are missing signatures", null))
        whenever(session.receive(List::class.java)).thenReturn(emptyList<DigitalSignatureAndMetadata>())

        assertThatThrownBy { callReceiveFinalityFlow() }
            .isInstanceOf(TransactionVerificationException::class.java)
            .hasMessageContaining("There are missing signatures")

        verify(persistenceService).persist(signedTransaction, TransactionStatus.UNVERIFIED)
        verify(persistenceService, never()).persist(signedTransaction, TransactionStatus.VERIFIED)
    }

    private fun callReceiveFinalityFlow(validator: ConsensualTransactionValidator = ConsensualTransactionValidator { }) {
        val flow = ConsensualReceiveFinalityFlow(session, validator)
        flow.persistenceService = persistenceService
        flow.serializationService = serializationService
        flow.transactionSignatureService = transactionSignatureService
        flow.call()
    }

    private fun digitalSignatureAndMetadata(publicKey: PublicKey, byteArray: ByteArray): DigitalSignatureAndMetadata {
        return DigitalSignatureAndMetadata(
            DigitalSignature.WithKey(publicKey, byteArray, emptyMap()),
            DigitalSignatureMetadata(Instant.now(), SignatureSpec("dummySignatureName"), emptyMap())
        )
    }
}
