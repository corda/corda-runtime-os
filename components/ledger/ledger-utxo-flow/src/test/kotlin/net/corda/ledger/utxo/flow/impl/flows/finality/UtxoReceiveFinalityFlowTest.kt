package net.corda.ledger.utxo.flow.impl.flows.finality

import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.common.flow.transaction.TransactionSignatureService
import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionImpl
import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainResolutionFlow
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.ledger.utxo.testkit.UtxoCommandExample
import net.corda.ledger.utxo.testkit.utxoInvalidStateAndRefExample
import net.corda.ledger.utxo.testkit.utxoNotaryExample
import net.corda.ledger.utxo.testkit.utxoStateExample
import net.corda.ledger.utxo.testkit.utxoTimeWindowExample
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.exceptions.CryptoSignatureException
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.ledger.common.transaction.TransactionVerificationException
import net.corda.v5.ledger.utxo.ContractVerificationException
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
class UtxoReceiveFinalityFlowTest {

    private companion object {
        val MEMBER = MemberX500Name("Alice", "London", "GB")
        val ID = SecureHash("algo", byteArrayOf(1, 2, 3))
    }

    private val memberLookup = mock<MemberLookup>()
    private val persistenceService = mock<UtxoLedgerPersistenceService>()
    private val transactionSignatureService = mock<TransactionSignatureService>()
    private val flowEngine = mock<FlowEngine>()

    private val session = mock<FlowSession>()

    private val memberInfo = mock<MemberInfo>()

    private val publicKey1 = mock<PublicKey>()
    private val publicKey2 = mock<PublicKey>()
    private val publicKeyNotary = mock<PublicKey>()

    private val notaryService = mock<Party>()

    private val signature1 = digitalSignatureAndMetadata(publicKey1)
    private val signature2 = digitalSignatureAndMetadata(publicKey2)
    private val signatureNotary = digitalSignatureAndMetadata(publicKeyNotary)

    private val metadata = mock<TransactionMetadata>()

    private val ledgerTransaction = mock<UtxoLedgerTransactionImpl>()
    private val signedTransaction = mock<UtxoSignedTransactionInternal>()
    private val signedTransactionWithOwnKeys = mock<UtxoSignedTransactionInternal>()
    private val notarisedTransaction = mock<UtxoSignedTransactionInternal>()

    @BeforeEach
    fun beforeEach() {
        whenever(session.counterparty).thenReturn(MEMBER)
        whenever(session.receive(UtxoSignedTransactionInternal::class.java)).thenReturn(signedTransaction)

        whenever(memberLookup.myInfo()).thenReturn(memberInfo)

        whenever(memberInfo.ledgerKeys).thenReturn(listOf(publicKey1, publicKey2))

        whenever(flowEngine.subFlow(any<TransactionBackchainResolutionFlow>())).thenReturn(Unit)

        whenever(signedTransaction.id).thenReturn(ID)
        whenever(signedTransaction.metadata).thenReturn(metadata)
        whenever(signedTransaction.notary).thenReturn(utxoNotaryExample)
        whenever(signedTransaction.getMissingSignatories()).thenReturn(setOf(publicKey1, publicKey2))
        whenever(signedTransaction.toLedgerTransaction()).thenReturn(ledgerTransaction)
        whenever(signedTransaction.sign(publicKey1)).thenReturn(signedTransaction to signature1)
        whenever(signedTransaction.sign(publicKey2)).thenReturn(signedTransactionWithOwnKeys to signature2)

        whenever(signedTransactionWithOwnKeys.id).thenReturn(ID)
        whenever(signedTransactionWithOwnKeys.getMissingSignatories()).thenReturn(setOf())
        whenever(signedTransactionWithOwnKeys.toLedgerTransaction()).thenReturn(ledgerTransaction)
        whenever(signedTransactionWithOwnKeys.signatures).thenReturn(listOf(signature1, signature2))
        whenever(signedTransactionWithOwnKeys.addSignature(signatureNotary))
            .thenReturn(notarisedTransaction)
        whenever(signedTransactionWithOwnKeys.notary).thenReturn(notaryService)

        whenever(notarisedTransaction.id).thenReturn(ID)

        whenever(ledgerTransaction.id).thenReturn(ID)
        whenever(ledgerTransaction.outputContractStates).thenReturn(listOf(utxoStateExample))
        whenever(ledgerTransaction.signatories).thenReturn(listOf(publicKeyExample))
        whenever(ledgerTransaction.commands).thenReturn(listOf(UtxoCommandExample()))
        whenever(ledgerTransaction.timeWindow).thenReturn(utxoTimeWindowExample)

        whenever(notaryService.owningKey).thenReturn(publicKeyNotary)
    }
    @Test
    fun `receiving a transaction that passes verification and notarisation is signed and recorded`() {
        whenever(signedTransaction.getMissingSignatories()).thenReturn(setOf(publicKey1, publicKey2, mock()))
        whenever(session.receive(List::class.java))
            .thenReturn(emptyList<DigitalSignatureAndMetadata>())
            .thenReturn(listOf(signatureNotary))

        callReceiveFinalityFlow()

        verify(signedTransaction).sign(publicKey1)
        verify(signedTransaction).sign(publicKey2)
        verify(persistenceService, times(2)).persist(signedTransactionWithOwnKeys, TransactionStatus.UNVERIFIED)
        verify(persistenceService).persist(notarisedTransaction, TransactionStatus.VERIFIED)
        verify(session).send(Payload.Success(listOf(signature1, signature2)))
    }

    @Test
    fun `receiving a transaction that passes verification then no notary signatures throws`() {
        whenever(signedTransaction.getMissingSignatories()).thenReturn(setOf(publicKey1, publicKey2, mock()))
        whenever(session.receive(List::class.java)).thenReturn(emptyList<DigitalSignatureAndMetadata>())

        assertThatThrownBy { callReceiveFinalityFlow() }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessageContaining("No notary signature received for transaction:")

        verify(signedTransaction).sign(publicKey1)
        verify(signedTransaction).sign(publicKey2)
        verify(persistenceService, times(2)).persist(signedTransactionWithOwnKeys, TransactionStatus.UNVERIFIED)
        verify(persistenceService, never()).persist(any(), eq(TransactionStatus.VERIFIED), any())
        verify(session).send(Payload.Success(listOf(signature1, signature2)))
    }

    @Test
    fun `receiving a transaction that passes verification then invalid notary signature throws`() {
        whenever(signedTransaction.getMissingSignatories()).thenReturn(setOf(publicKey1, publicKey2, mock()))
        whenever(session.receive(List::class.java))
            .thenReturn(emptyList<DigitalSignatureAndMetadata>())
            .thenReturn(listOf(signatureNotary))

        whenever(transactionSignatureService.verifyNotarySignature(any(), eq(signatureNotary))).thenThrow(
            CryptoSignatureException("Verifying notary signature failed!!")
        )

        assertThatThrownBy { callReceiveFinalityFlow() }
            .isInstanceOf(CryptoSignatureException::class.java)
            .hasMessageContaining("Verifying notary signature failed!!")

        verify(signedTransaction).sign(publicKey1)
        verify(signedTransaction).sign(publicKey2)
        verify(persistenceService, times(2)).persist(signedTransactionWithOwnKeys, TransactionStatus.UNVERIFIED)
        verify(persistenceService, never()).persist(any(), eq(TransactionStatus.VERIFIED), any())
        verify(session).send(Payload.Success(listOf(signature1, signature2)))
    }

    @Test
    fun `receiving a transaction that passes verification then receiving signatures from notary with unexpected signer throws`() {
        whenever(signedTransaction.getMissingSignatories()).thenReturn(setOf(publicKey1, publicKey2, mock()))
        whenever(session.receive(List::class.java))
            .thenReturn(emptyList<DigitalSignatureAndMetadata>())
            .thenReturn(listOf(signatureNotary))
        whenever(notaryService.owningKey).thenReturn(publicKey1)

        assertThatThrownBy { callReceiveFinalityFlow() }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessageContaining("Notary's signature has not been created by the transaction's notary.")

        verify(signedTransaction).sign(publicKey1)
        verify(signedTransaction).sign(publicKey2)
        verify(persistenceService, times(2)).persist(signedTransactionWithOwnKeys, TransactionStatus.UNVERIFIED)
        verify(persistenceService, never()).persist(any(), eq(TransactionStatus.VERIFIED), any())
        verify(session).send(Payload.Success(listOf(signature1, signature2)))
    }

    @Test
    fun `the received transaction is only signed with ledger keys in the transaction's missing signatories`() {
        whenever(signedTransaction.getMissingSignatories()).thenReturn(setOf(publicKey1, mock()))
        whenever(session.receive(List::class.java))
            .thenReturn(emptyList<DigitalSignatureAndMetadata>())
            .thenReturn(listOf(signatureNotary))

        val signedTransactionWith1Key = mock<UtxoSignedTransactionInternal>()
        whenever(signedTransactionWith1Key.id).thenReturn(ID)
        whenever(signedTransactionWith1Key.signatures).thenReturn(listOf(signature1))

        whenever(signedTransaction.sign(publicKey1)).thenReturn(signedTransactionWith1Key to signature1)
        whenever(signedTransactionWith1Key.notary).thenReturn(notaryService)
        whenever(signedTransactionWith1Key.addSignature(signatureNotary))
            .thenReturn(notarisedTransaction)

        callReceiveFinalityFlow()

        verify(signedTransaction).sign(publicKey1)
        verify(signedTransactionWith1Key, never()).sign(publicKey2)
        verify(persistenceService, times(2)).persist(signedTransactionWith1Key, TransactionStatus.UNVERIFIED)
        verify(persistenceService).persist(notarisedTransaction, TransactionStatus.VERIFIED)
        verify(session).send(Payload.Success(listOf(signature1)))
    }

    @Test
    fun `receiving a transaction that fails verification with an IllegalArgumentException sends a failure payload and throws an exception`() {
        assertThatThrownBy { callReceiveFinalityFlow { throw IllegalArgumentException() } }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessageContaining("Transaction validation failed for transaction")

        verify(session).send(any<Payload.Failure<List<DigitalSignatureAndMetadata>>>())
        verify(persistenceService).persist(signedTransaction, TransactionStatus.INVALID)
        verify(persistenceService, never()).persist(any(), eq(TransactionStatus.VERIFIED), any())
    }

    @Test
    fun `receiving a transaction that fails verification with an IllegalStateException sends a failure payload and throws an exception`() {
        assertThatThrownBy { callReceiveFinalityFlow { throw IllegalStateException() } }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessageContaining("Transaction validation failed for transaction")

        verify(session).send(any<Payload.Failure<List<DigitalSignatureAndMetadata>>>())
        verify(persistenceService).persist(signedTransaction, TransactionStatus.INVALID)
        verify(persistenceService, never()).persist(any(), eq(TransactionStatus.VERIFIED), any())
    }

    @Test
    fun `receiving a transaction that fails verification with a CordaRuntimeException sends a failure payload and throws an exception`() {
        assertThatThrownBy { callReceiveFinalityFlow { throw CordaRuntimeException("") } }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessageContaining("Transaction validation failed for transaction")

        verify(session).send(any<Payload.Failure<List<DigitalSignatureAndMetadata>>>())
        verify(persistenceService).persist(signedTransaction, TransactionStatus.INVALID)
        verify(persistenceService, never()).persist(any(), eq(TransactionStatus.VERIFIED), any())
    }

    @Test
    fun `receiving a transaction that throws an unexpected exception during verification throws an exception`() {
        assertThatThrownBy { callReceiveFinalityFlow { throw FileNotFoundException("message!") } }
            .isInstanceOf(FileNotFoundException::class.java)
            .hasMessage("message!")

        verify(session, never()).send(any<Payload.Failure<List<DigitalSignatureAndMetadata>>>())
        verify(persistenceService, never()).persist(any(), any(), any())
    }

    @Test
    fun `the received transaction is not signed if the member does not have any ledger keys`() {
        whenever(signedTransaction.getMissingSignatories()).thenReturn(setOf(publicKey1, publicKey2, mock()))
        whenever(session.receive(List::class.java)).thenReturn(emptyList<DigitalSignatureAndMetadata>())
        whenever(memberInfo.ledgerKeys).thenReturn(emptyList())

        whenever(session.receive(List::class.java)).then {
            throw CordaRuntimeException("session error")
        }

        assertThatThrownBy { callReceiveFinalityFlow() }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessage("session error")
        verify(signedTransaction, never()).sign(any<PublicKey>())
        verify(session).send(Payload.Success(emptyList<DigitalSignatureAndMetadata>()))
        verify(persistenceService).persist(signedTransaction, TransactionStatus.UNVERIFIED)
        verify(persistenceService, never()).persist(any(), eq(TransactionStatus.VERIFIED), any())
    }

    @Test
    fun `receiving an invalid signature to record throws an exception`() {
        val invalidSignature = mock<DigitalSignatureAndMetadata>()

        whenever(session.receive(List::class.java)).thenReturn(listOf(invalidSignature))
        whenever(signedTransactionWithOwnKeys.addSignature(any())).thenReturn(signedTransactionWithOwnKeys)
        whenever(signedTransactionWithOwnKeys.verifySignatures()).thenThrow(
            CryptoSignatureException("Verifying signature failed!!")
        )

        assertThatThrownBy { callReceiveFinalityFlow() }
            .isInstanceOf(CryptoSignatureException::class.java)
            .hasMessage("Verifying signature failed!!")

        verify(persistenceService).persist(signedTransactionWithOwnKeys, TransactionStatus.UNVERIFIED)
        verify(persistenceService, never()).persist(any(), eq(TransactionStatus.VERIFIED), any())
    }

    @Test
    fun `receiving a transaction to record that is not fully signed throws an exception`() {
        whenever(signedTransactionWithOwnKeys.verifySignatures()).thenThrow(TransactionVerificationException(ID, "There are missing signatures", null))
        whenever(session.receive(List::class.java)).thenReturn(emptyList<DigitalSignatureAndMetadata>())

        assertThatThrownBy { callReceiveFinalityFlow() }
            .isInstanceOf(TransactionVerificationException::class.java)
            .hasMessageContaining("There are missing signatures")

        verify(persistenceService).persist(signedTransactionWithOwnKeys, TransactionStatus.UNVERIFIED)
        verify(persistenceService, never()).persist(any(), eq(TransactionStatus.VERIFIED), any())
    }

    @Test
    fun `receiving a transaction resolves the transaction's backchain`() {
        whenever(signedTransaction.getMissingSignatories()).thenReturn(setOf(publicKey1, publicKey2, mock()))
        whenever(session.receive(List::class.java))
            .thenReturn(emptyList<DigitalSignatureAndMetadata>())
            .thenReturn(listOf(signatureNotary))

        callReceiveFinalityFlow()

       verify(flowEngine).subFlow(TransactionBackchainResolutionFlow(signedTransaction, session))
    }

    @Test
    fun `receiving a transaction resolves the transaction's backchain even when it fails verification`() {
        whenever(signedTransaction.getMissingSignatories()).thenReturn(setOf(publicKey1, publicKey2, mock()))
        whenever(ledgerTransaction.outputStateAndRefs).thenReturn(listOf(utxoInvalidStateAndRefExample))

        assertThatThrownBy { callReceiveFinalityFlow() }.isInstanceOf(ContractVerificationException::class.java)

        verify(flowEngine).subFlow(TransactionBackchainResolutionFlow(signedTransaction, session))
    }

    private fun callReceiveFinalityFlow(verifier: UtxoTransactionValidator = UtxoTransactionValidator { }) {
        val flow = UtxoReceiveFinalityFlow(session, verifier)
        flow.memberLookup = memberLookup
        flow.persistenceService = persistenceService
        flow.transactionSignatureService = transactionSignatureService
        flow.flowEngine = flowEngine
        flow.call()
    }

    private fun digitalSignatureAndMetadata(publicKey: PublicKey): DigitalSignatureAndMetadata {
        return DigitalSignatureAndMetadata(
            DigitalSignature.WithKey(publicKey, byteArrayOf(1, 2, 3), emptyMap()),
            DigitalSignatureMetadata(Instant.now(), emptyMap())
        )
    }
}
