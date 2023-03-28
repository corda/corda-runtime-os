package net.corda.ledger.utxo.flow.impl.flows.finality.v1

import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.fullIdHash
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.data.transaction.TransactionVerificationStatus
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionImpl
import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainResolutionFlow
import net.corda.ledger.utxo.flow.impl.flows.backchain.dependencies
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.ledger.utxo.flow.impl.transaction.verifier.TransactionVerificationException
import net.corda.ledger.utxo.flow.impl.transaction.verifier.UtxoLedgerTransactionVerificationService
import net.corda.ledger.utxo.testkit.UtxoCommandExample
import net.corda.ledger.utxo.testkit.getExampleInvalidStateAndRefImpl
import net.corda.ledger.utxo.testkit.getUtxoStateExample
import net.corda.ledger.utxo.testkit.utxoTimeWindowExample
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.exceptions.CryptoSignatureException
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.ledger.common.transaction.TransactionSignatureException
import net.corda.v5.ledger.utxo.VisibilityChecker
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionValidator
import net.corda.v5.membership.MemberInfo
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.FileNotFoundException
import java.security.PublicKey
import java.time.Instant

@Suppress("MaxLineLength")
class UtxoReceiveFinalityFlowV1Test {

    private companion object {
        val MEMBER = MemberX500Name("Alice", "London", "GB")
        val ID = SecureHashImpl("algo", byteArrayOf(1, 2, 3))
    }

    private val memberLookup = mock<MemberLookup>()
    private val persistenceService = mock<UtxoLedgerPersistenceService>()
    private val transactionVerificationService = mock<UtxoLedgerTransactionVerificationService>()
    private val flowEngine = mock<FlowEngine>()
    private val visibilityChecker = mock<VisibilityChecker>()

    private val session = mock<FlowSession>()

    private val memberInfo = mock<MemberInfo>()

    private val publicKey0 = mock<PublicKey>().also { whenever(it.encoded).thenReturn(byteArrayOf(0x01)) }
    private val publicKey1 = mock<PublicKey>().also { whenever(it.encoded).thenReturn(byteArrayOf(0x02)) }
    private val publicKey2 = mock<PublicKey>().also { whenever(it.encoded).thenReturn(byteArrayOf(0x03)) }
    private val publicKey3 = mock<PublicKey>().also { whenever(it.encoded).thenReturn(byteArrayOf(0x04)) }
    private val publicKeyNotary = mock<PublicKey>().also { whenever(it.encoded).thenReturn(byteArrayOf(0x05)) }

    private val signature0 = digitalSignatureAndMetadata(publicKey0, byteArrayOf(1, 2, 0))
    private val signature1 = digitalSignatureAndMetadata(publicKey1, byteArrayOf(1, 2, 3))
    private val signature2 = digitalSignatureAndMetadata(publicKey2, byteArrayOf(1, 2, 4))
    private val signature3 = digitalSignatureAndMetadata(publicKey3, byteArrayOf(1, 2, 5))
    private val signatureNotary = digitalSignatureAndMetadata(publicKeyNotary, byteArrayOf(1, 2, 6))

    private val metadata = mock<TransactionMetadata>()

    private val ledgerTransaction = mock<UtxoLedgerTransactionImpl>()
    private val signedTransaction = mock<UtxoSignedTransactionInternal>()
    private val signedTransactionWithOwnKeys = mock<UtxoSignedTransactionInternal>()
    private val notarizedTransaction = mock<UtxoSignedTransactionInternal>()

    @BeforeEach
    fun beforeEach() {
        whenever(session.counterparty).thenReturn(MEMBER)
        whenever(session.receive(UtxoSignedTransactionInternal::class.java)).thenReturn(signedTransaction)

        whenever(memberLookup.myInfo()).thenReturn(memberInfo)

        whenever(memberInfo.ledgerKeys).thenReturn(listOf(publicKey1, publicKey2))

        whenever(flowEngine.subFlow(any<TransactionBackchainResolutionFlow>())).thenReturn(Unit)

        whenever(signedTransaction.id).thenReturn(ID)
        whenever(signedTransaction.metadata).thenReturn(metadata)
        whenever(signedTransaction.notaryKey).thenReturn(publicKeyNotary)
        whenever(signedTransaction.toLedgerTransaction()).thenReturn(ledgerTransaction)
        whenever(signedTransaction.signatures).thenReturn(listOf(signature0))

        whenever(signedTransactionWithOwnKeys.id).thenReturn(ID)
        whenever(signedTransactionWithOwnKeys.toLedgerTransaction()).thenReturn(ledgerTransaction)
        whenever(signedTransactionWithOwnKeys.signatures).thenReturn(listOf(signature1, signature2))
        whenever(signedTransactionWithOwnKeys.addSignature(signature3)).thenReturn(signedTransactionWithOwnKeys)
        whenever(signedTransactionWithOwnKeys.addSignature(signatureNotary)).thenReturn(notarizedTransaction)
        whenever(signedTransactionWithOwnKeys.notaryKey).thenReturn(publicKeyNotary)

        whenever(notarizedTransaction.id).thenReturn(ID)

        whenever(ledgerTransaction.id).thenReturn(ID)
        whenever(ledgerTransaction.outputContractStates).thenReturn(listOf(getUtxoStateExample()))
        whenever(ledgerTransaction.signatories).thenReturn(listOf(publicKeyExample))
        whenever(ledgerTransaction.commands).thenReturn(listOf(UtxoCommandExample()))
        whenever(ledgerTransaction.timeWindow).thenReturn(utxoTimeWindowExample)
        whenever(ledgerTransaction.metadata).thenReturn(metadata)
    }

    @Test
    fun `receiving a transaction that passes verification and notarization is signed and recorded`() {
        whenever(signedTransaction.addMissingSignatures()).thenReturn(signedTransactionWithOwnKeys to listOf(signature1, signature2))
        whenever(session.receive(List::class.java)).thenReturn(listOf(signature3))
        whenever(session.receive(Payload::class.java)).thenReturn(Payload.Success(listOf(signatureNotary)))

        callReceiveFinalityFlow()

        verify(signedTransaction).addMissingSignatures()

        verify(signedTransactionWithOwnKeys).addSignature(signature3)
        verify(persistenceService, times(2)).persist(signedTransactionWithOwnKeys, TransactionStatus.UNVERIFIED)
        verify(persistenceService).persist(notarizedTransaction, TransactionStatus.VERIFIED)
        verify(session).send(Payload.Success(listOf(signature1, signature2)))
    }

    @Test
    fun `receiving a transaction initially without signatures throws and persists as invalid`() {
        whenever(signedTransaction.signatures).thenReturn(listOf())
        assertThatThrownBy { callReceiveFinalityFlow() }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessageContaining("Received initial transaction without signatures.")

        verify(signedTransaction, never()).addMissingSignatures()
        verify(persistenceService).persist(signedTransaction, TransactionStatus.INVALID)
        verify(session).send(any<Payload.Failure<List<DigitalSignatureAndMetadata>>>())
    }

    @Test
    fun `receiving a transaction initially with invalid signature throws and persists as invalid`() {
        whenever(signedTransaction.verifySignatorySignature(any())).thenThrow(
            CryptoSignatureException("Verifying signature failed!!")
        )
        assertThatThrownBy { callReceiveFinalityFlow() }
            .isInstanceOf(CryptoSignatureException::class.java)
            .hasMessageContaining("Verifying signature failed!!")

        verify(signedTransaction, never()).addMissingSignatures()
        verify(persistenceService).persist(signedTransaction, TransactionStatus.INVALID)
        verify(session).send(any<Payload.Failure<List<DigitalSignatureAndMetadata>>>())
    }

    @Test
    fun `receiving an invalid transaction initially throws and persists as invalid`() {
        whenever(transactionVerificationService.verify(any())).thenThrow(
            TransactionVerificationException(
                ID,
                TransactionVerificationStatus.INVALID,
                null,
                "Verification error"
            )
        )
        assertThatThrownBy { callReceiveFinalityFlow() }
            .isInstanceOf(TransactionVerificationException::class.java)
            .hasMessageContaining("Verification error")

        verify(signedTransaction, never()).addMissingSignatures()
        verify(persistenceService).persist(signedTransaction, TransactionStatus.INVALID)
    }

    @Test
    fun `receiving a transaction that passes verification then no notary signatures throws`() {
        whenever(signedTransaction.addMissingSignatures()).thenReturn(signedTransactionWithOwnKeys to listOf(signature1, signature2))
        whenever(session.receive(List::class.java)).thenReturn(listOf(signature3))
        whenever(session.receive(Payload::class.java)).thenReturn(Payload.Success(listOf<DigitalSignatureAndMetadata>()))

        assertThatThrownBy { callReceiveFinalityFlow() }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessageContaining("No notary signature received for transaction:")

        verify(signedTransaction).addMissingSignatures()
        verify(persistenceService, times(2)).persist(signedTransactionWithOwnKeys, TransactionStatus.UNVERIFIED)
        verify(persistenceService, never()).persist(any(), eq(TransactionStatus.VERIFIED), any())
        verify(persistenceService).persist(signedTransactionWithOwnKeys, TransactionStatus.INVALID)
        verify(session).send(Payload.Success(listOf(signature1, signature2)))
    }

    @Test
    fun `receiving a transaction that passes verification then unrecoverable failure from notarization throws and invalidates tx`() {
        whenever(signedTransaction.addMissingSignatures()).thenReturn(signedTransactionWithOwnKeys to listOf(signature1, signature2))
        whenever(session.receive(List::class.java)).thenReturn(listOf(signature3))
        whenever(session.receive(Payload::class.java)).thenReturn(
            Payload.Failure<List<DigitalSignatureAndMetadata>>(
                "notarization error",
                FinalityNotarizationFailureType.FATAL.value
            )
        )

        assertThatThrownBy { callReceiveFinalityFlow() }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessageContaining("notarization error")

        verify(signedTransaction).addMissingSignatures()
        verify(persistenceService, times(2)).persist(signedTransactionWithOwnKeys, TransactionStatus.UNVERIFIED)
        verify(persistenceService, never()).persist(any(), eq(TransactionStatus.VERIFIED), any())
        verify(persistenceService).persist(signedTransactionWithOwnKeys, TransactionStatus.INVALID)
        verify(session).send(Payload.Success(listOf(signature1, signature2)))
    }

    @Test
    fun `receiving a transaction that passes verification then a non-unrecoverable failure from notarization throws and does not invalidate tx`() {
        whenever(signedTransaction.addMissingSignatures()).thenReturn(signedTransactionWithOwnKeys to listOf(signature1, signature2))
        whenever(session.receive(List::class.java)).thenReturn(listOf(signature3))
        whenever(session.receive(Payload::class.java)).thenReturn(Payload.Failure<List<DigitalSignatureAndMetadata>>("notarization error"))

        assertThatThrownBy { callReceiveFinalityFlow() }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessageContaining("notarization error")

        verify(signedTransaction).addMissingSignatures()
        verify(persistenceService, times(2)).persist(signedTransactionWithOwnKeys, TransactionStatus.UNVERIFIED)
        verify(persistenceService, never()).persist(any(), eq(TransactionStatus.VERIFIED), any())
        verify(persistenceService, never()).persist(any(), eq(TransactionStatus.INVALID), any())
        verify(session).send(Payload.Success(listOf(signature1, signature2)))
    }

    @Test
    fun `receiving a transaction that passes verification then invalid notary signature throws`() {
        whenever(signedTransaction.addMissingSignatures()).thenReturn(signedTransactionWithOwnKeys to listOf(signature1, signature2))
        whenever(session.receive(List::class.java)).thenReturn(listOf(signature3))
        whenever(session.receive(Payload::class.java)).thenReturn(Payload.Success(listOf(signatureNotary)))

        whenever(signedTransactionWithOwnKeys.verifyNotarySignature(eq(signatureNotary))).thenThrow(
            CryptoSignatureException("Verifying notary signature failed!!")
        )

        assertThatThrownBy { callReceiveFinalityFlow() }
            .isInstanceOf(CryptoSignatureException::class.java)
            .hasMessageContaining("Verifying notary signature failed!!")

        verify(signedTransaction).addMissingSignatures()
        verify(persistenceService, times(2)).persist(signedTransactionWithOwnKeys, TransactionStatus.UNVERIFIED)
        verify(persistenceService, never()).persist(any(), eq(TransactionStatus.VERIFIED), any())
        verify(persistenceService).persist(signedTransactionWithOwnKeys, TransactionStatus.INVALID)
        verify(session).send(Payload.Success(listOf(signature1, signature2)))
    }

    @Test
    fun `receiving a transaction that passes verification then receiving signatures from notary with unexpected signer throws`() {
        whenever(signedTransaction.addMissingSignatures()).thenReturn(signedTransactionWithOwnKeys to listOf(signature1, signature2))
        whenever(session.receive(List::class.java)).thenReturn(listOf(signature3))
        whenever(session.receive(Payload::class.java)).thenReturn(Payload.Success(listOf(signatureNotary)))
        whenever(signedTransactionWithOwnKeys.notaryKey).thenReturn(publicKey1)

        whenever(signedTransactionWithOwnKeys.verifyNotarySignature(any())).thenThrow(
            CordaRuntimeException("Notary's signature has not been created by the transaction's notary.")
        )

        assertThatThrownBy { callReceiveFinalityFlow() }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessageContaining("Notary's signature has not been created by the transaction's notary.")

        verify(signedTransaction).addMissingSignatures()
        verify(persistenceService, times(2)).persist(signedTransactionWithOwnKeys, TransactionStatus.UNVERIFIED)
        verify(persistenceService, never()).persist(any(), eq(TransactionStatus.VERIFIED), any())
        verify(persistenceService).persist(signedTransactionWithOwnKeys, TransactionStatus.INVALID)
        verify(session).send(Payload.Success(listOf(signature1, signature2)))
    }

    @Test
    fun `the received transaction is only signed with ledger keys in the transaction's missing signatories`() {
        val signedTransactionWith1Key = mock<UtxoSignedTransactionInternal>()
        whenever(memberInfo.ledgerKeys).thenReturn(listOf(publicKey1, publicKey2))
        whenever(signedTransaction.addMissingSignatures()).thenReturn(signedTransactionWith1Key to listOf(signature1))
        whenever(session.receive(List::class.java)).thenReturn(listOf(signature3))
        whenever(session.receive(Payload::class.java)).thenReturn(Payload.Success(listOf(signatureNotary)))

        whenever(signedTransactionWith1Key.id).thenReturn(ID)
        whenever(signedTransactionWith1Key.signatures).thenReturn(listOf(signature1))

        whenever(signedTransactionWith1Key.addSignature(signature3)).thenReturn(signedTransactionWith1Key)
        whenever(signedTransactionWith1Key.notaryKey).thenReturn(publicKeyNotary)
        whenever(signedTransactionWith1Key.addSignature(signatureNotary)).thenReturn(notarizedTransaction)

        callReceiveFinalityFlow()

        verify(signedTransaction).addMissingSignatures()
        verify(signedTransactionWith1Key, never()).addMissingSignatures()
        verify(persistenceService, times(2)).persist(signedTransactionWith1Key, TransactionStatus.UNVERIFIED)
        verify(persistenceService).persist(notarizedTransaction, TransactionStatus.VERIFIED)
        verify(session).send(Payload.Success(listOf(signature1)))
    }

    @Test
    fun `receiving a transaction that fails validation with an IllegalArgumentException sends a failure payload, throws an exception and invalidates tx`() {
        assertThatThrownBy { callReceiveFinalityFlow { throw IllegalArgumentException() } }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessageContaining("Transaction validation failed for transaction")

        verify(session).send(any<Payload.Failure<List<DigitalSignatureAndMetadata>>>())
        verify(persistenceService).persist(signedTransaction, TransactionStatus.INVALID)
        verify(persistenceService, never()).persist(any(), eq(TransactionStatus.UNVERIFIED), any())
        verify(persistenceService, never()).persist(any(), eq(TransactionStatus.VERIFIED), any())
    }

    @Test
    fun `receiving a transaction that fails validation with an IllegalStateException sends a failure payload, throws an exception and invalidates tx`() {
        assertThatThrownBy { callReceiveFinalityFlow { throw IllegalStateException() } }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessageContaining("Transaction validation failed for transaction")

        verify(session).send(any<Payload.Failure<List<DigitalSignatureAndMetadata>>>())
        verify(persistenceService).persist(signedTransaction, TransactionStatus.INVALID)
        verify(persistenceService, never()).persist(any(), eq(TransactionStatus.UNVERIFIED), any())
        verify(persistenceService, never()).persist(any(), eq(TransactionStatus.VERIFIED), any())
    }

    @Test
    fun `receiving a transaction that fails validation with a CordaRuntimeException sends a failure payload, throws an exception and invalidates tx`() {
        assertThatThrownBy { callReceiveFinalityFlow { throw CordaRuntimeException("") } }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessageContaining("Transaction validation failed for transaction")

        verify(session).send(any<Payload.Failure<List<DigitalSignatureAndMetadata>>>())
        verify(persistenceService).persist(signedTransaction, TransactionStatus.INVALID)
        verify(persistenceService, never()).persist(any(), eq(TransactionStatus.UNVERIFIED), any())
        verify(persistenceService, never()).persist(any(), eq(TransactionStatus.VERIFIED), any())
    }

    @Test
    fun `receiving a transaction that throws an unexpected exception during validation throws an exception and does not invalidate tx`() {
        assertThatThrownBy { callReceiveFinalityFlow { throw FileNotFoundException("message!") } }
            .isInstanceOf(FileNotFoundException::class.java)
            .hasMessage("message!")

        verify(session, never()).send(any<Payload.Failure<List<DigitalSignatureAndMetadata>>>())
        verify(persistenceService, never()).persist(signedTransaction, TransactionStatus.INVALID)
        verify(persistenceService, never()).persist(any(), any(), any())
    }

    @Test
    fun `the received transaction does not have to be signed by the local member to be recorded`() {
        whenever(signedTransaction.addMissingSignatures()).thenReturn(signedTransaction to listOf())
        whenever(memberInfo.ledgerKeys).thenReturn(emptyList())
        whenever(signedTransaction.addSignature(signature3)).thenReturn(signedTransaction)
        whenever(signedTransaction.addSignature(signatureNotary)).thenReturn(notarizedTransaction)
        whenever(session.receive(List::class.java)).thenReturn(listOf(signature3))
        whenever(session.receive(Payload::class.java)).thenReturn(Payload.Success(listOf(signatureNotary)))

        callReceiveFinalityFlow()

        verify(signedTransaction).addMissingSignatures()
        verify(session).send(Payload.Success(emptyList<DigitalSignatureAndMetadata>()))
        verify(persistenceService, times(2)).persist(signedTransaction, TransactionStatus.UNVERIFIED)
        verify(persistenceService).persist(notarizedTransaction, TransactionStatus.VERIFIED)
    }

    @Test
    fun `receiving an invalid signature to record throws an exception`() {
        val invalidSignature = mock<DigitalSignatureAndMetadata>()

        whenever(session.receive(List::class.java)).thenReturn(listOf(invalidSignature))
        whenever(signedTransaction.addMissingSignatures()).thenReturn(signedTransactionWithOwnKeys to listOf())
        whenever(signedTransactionWithOwnKeys.addSignature(any())).thenReturn(signedTransactionWithOwnKeys)
        whenever(signedTransactionWithOwnKeys.verifySignatorySignatures()).thenThrow(
            CryptoSignatureException("Verifying signature failed!!")
        )

        assertThatThrownBy { callReceiveFinalityFlow() }
            .isInstanceOf(CryptoSignatureException::class.java)
            .hasMessage("Verifying signature failed!!")

        verify(persistenceService).persist(signedTransactionWithOwnKeys, TransactionStatus.UNVERIFIED)
        verify(persistenceService).persist(signedTransactionWithOwnKeys, TransactionStatus.INVALID)
        verify(persistenceService, never()).persist(any(), eq(TransactionStatus.VERIFIED), any())
    }

    @Test
    fun `receiving a transaction to record that is not fully signed throws an exception`() {
        whenever(signedTransaction.addMissingSignatures()).thenReturn(signedTransactionWithOwnKeys to listOf())
        whenever(signedTransactionWithOwnKeys.verifySignatorySignatures()).thenThrow(TransactionSignatureException(ID, "There are missing signatures", null))
        whenever(session.receive(List::class.java)).thenReturn(emptyList<DigitalSignatureAndMetadata>())

        assertThatThrownBy { callReceiveFinalityFlow() }
            .isInstanceOf(TransactionSignatureException::class.java)
            .hasMessageContaining("There are missing signatures")

        verify(persistenceService).persist(signedTransactionWithOwnKeys, TransactionStatus.UNVERIFIED)
        verify(persistenceService).persist(signedTransactionWithOwnKeys, TransactionStatus.INVALID)
        verify(persistenceService, never()).persist(any(), eq(TransactionStatus.VERIFIED), any())
    }

    @Test
    fun `receiving a transaction resolves the transaction's backchain`() {
        whenever(signedTransaction.addMissingSignatures()).thenReturn(signedTransactionWithOwnKeys to listOf(signature1, signature2))
        whenever(session.receive(List::class.java)).thenReturn(listOf(signature3))
        whenever(session.receive(Payload::class.java)).thenReturn(Payload.Success(listOf(signatureNotary)))

        callReceiveFinalityFlow()

        verify(flowEngine).subFlow(TransactionBackchainResolutionFlow(signedTransaction.dependencies, session))
    }

    @Test
    fun `receiving a transaction resolves the transaction's backchain even when it fails verification`() {
        whenever(ledgerTransaction.outputStateAndRefs).thenReturn(listOf(getExampleInvalidStateAndRefImpl()))
        whenever(transactionVerificationService.verify(any())).thenThrow(
            TransactionVerificationException(
                ID,
                TransactionVerificationStatus.INVALID,
                null,
                "Verification error"
            )
        )
        assertThatThrownBy { callReceiveFinalityFlow() }
            .isInstanceOf(TransactionVerificationException::class.java)
            .hasMessageContaining("Verification error")

        verify(flowEngine).subFlow(TransactionBackchainResolutionFlow(signedTransaction.dependencies, session))
    }

    private fun callReceiveFinalityFlow(validator: UtxoTransactionValidator = UtxoTransactionValidator { }) {
        val flow = UtxoReceiveFinalityFlowV1(session, validator)
        flow.memberLookup = memberLookup
        flow.persistenceService = persistenceService
        flow.transactionVerificationService = transactionVerificationService
        flow.flowEngine = flowEngine
        flow.visibilityChecker = visibilityChecker
        flow.call()
    }

    private fun digitalSignatureAndMetadata(publicKey: PublicKey, byteArray: ByteArray): DigitalSignatureAndMetadata {
        return DigitalSignatureAndMetadata(
            DigitalSignature.WithKeyId(publicKey.fullIdHash(), byteArray),
            DigitalSignatureMetadata(Instant.now(), SignatureSpec("dummySignatureName"), emptyMap())
        )
    }
}
