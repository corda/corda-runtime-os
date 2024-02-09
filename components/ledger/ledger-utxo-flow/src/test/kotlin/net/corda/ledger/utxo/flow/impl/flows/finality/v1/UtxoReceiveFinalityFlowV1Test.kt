package net.corda.ledger.utxo.flow.impl.flows.finality.v1

import net.corda.crypto.cipher.suite.SignatureSpecImpl
import net.corda.crypto.core.DigitalSignatureWithKeyId
import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.fullIdHash
import net.corda.flow.application.GroupParametersLookupInternal
import net.corda.flow.state.ContextPlatformProperties
import net.corda.flow.state.FlowContext
import net.corda.ledger.common.data.transaction.TransactionMetadataInternal
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.data.transaction.FilteredTransactionAndSignatures
import net.corda.ledger.utxo.data.transaction.TransactionVerificationStatus
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionImpl
import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainResolutionFlow
import net.corda.ledger.utxo.flow.impl.flows.backchain.dependencies
import net.corda.ledger.utxo.flow.impl.flows.finality.FinalityPayload
import net.corda.ledger.utxo.flow.impl.groupparameters.verifier.SignedGroupParametersVerifier
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerGroupParametersPersistenceService
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.ledger.utxo.flow.impl.transaction.verifier.TransactionVerificationException
import net.corda.ledger.utxo.flow.impl.transaction.verifier.UtxoLedgerTransactionVerificationService
import net.corda.ledger.utxo.testkit.UtxoCommandExample
import net.corda.ledger.utxo.testkit.anotherNotaryX500Name
import net.corda.ledger.utxo.testkit.getExampleInvalidStateAndRefImpl
import net.corda.ledger.utxo.testkit.getUtxoStateExample
import net.corda.ledger.utxo.testkit.notaryX500Name
import net.corda.ledger.utxo.testkit.utxoTimeWindowExample
import net.corda.membership.lib.SignedGroupParameters
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.exceptions.CryptoSignatureException
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.common.transaction.TransactionSignatureException
import net.corda.v5.ledger.utxo.NotarySignatureVerificationService
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.VisibilityChecker
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionValidator
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredData.Audit
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransaction
import net.corda.v5.membership.MemberInfo
import net.corda.v5.membership.NotaryInfo
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.timeout
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.FileNotFoundException
import java.security.PublicKey
import java.time.Instant

@Suppress("MaxLineLength")
@Disabled
class UtxoReceiveFinalityFlowV1Test {

    private companion object {
        val MEMBER = MemberX500Name("Alice", "London", "GB")
        val ID = SecureHashImpl("algo", byteArrayOf(1, 2, 3))
        val transactionGroupParametersHash = SecureHashImpl("algo", byteArrayOf(10, 0, 0))
        val anotherGroupParametersHash = SecureHashImpl("algo", byteArrayOf(11, 0, 0))
    }

    private val memberLookup = mock<MemberLookup>()
    private val persistenceService = mock<UtxoLedgerPersistenceService>()
    private val groupParametersLookup = mock<GroupParametersLookupInternal>()
    private val utxoLedgerGroupParametersPersistenceService = mock<UtxoLedgerGroupParametersPersistenceService>()
    private val transactionVerificationService = mock<UtxoLedgerTransactionVerificationService>()
    private val signedGroupParametersVerifier = mock<SignedGroupParametersVerifier>()
    private val visibilityChecker = mock<VisibilityChecker>()
    private val notarySignatureVerificationService = mock<NotarySignatureVerificationService>()

    private val platformProperties = mock<ContextPlatformProperties>().also { properties ->
        whenever(properties.set(any(), any())).thenAnswer {}
    }
    private val flowContextProperties = mock<FlowContext>().also {
        whenever(it.platformProperties).thenReturn(platformProperties)
    }
    private val flowEngine = mock<FlowEngine>().also {
        whenever(it.flowContextProperties).thenReturn(flowContextProperties)
    }

    private val session = mock<FlowSession>()

    private val memberInfo = mock<MemberInfo>()

    private val publicKey0 = mock<PublicKey>().also { whenever(it.encoded).thenReturn(byteArrayOf(0x01)) }
    private val publicKey1 = mock<PublicKey>().also { whenever(it.encoded).thenReturn(byteArrayOf(0x02)) }
    private val publicKey2 = mock<PublicKey>().also { whenever(it.encoded).thenReturn(byteArrayOf(0x03)) }
    private val publicKey3 = mock<PublicKey>().also { whenever(it.encoded).thenReturn(byteArrayOf(0x04)) }
    private val publicKeyNotary = mock<PublicKey>().also { whenever(it.encoded).thenReturn(byteArrayOf(0x05)) }
    private val publicKeyAnotherNotary = mock<PublicKey>().also { whenever(it.encoded).thenReturn(byteArrayOf(0x06)) }
    private val compositeKeyNotary = mock<CompositeKey>().also {
        whenever(it.leafKeys).thenReturn(setOf(publicKeyNotary))
        whenever(it.isFulfilledBy(publicKeyNotary)).thenReturn(true)
        whenever(it.isFulfilledBy(setOf(publicKeyNotary))).thenReturn(true)
    }

    private val signature0 = digitalSignatureAndMetadata(publicKey0, byteArrayOf(1, 2, 0))
    private val signature1 = digitalSignatureAndMetadata(publicKey1, byteArrayOf(1, 2, 3))
    private val signature2 = digitalSignatureAndMetadata(publicKey2, byteArrayOf(1, 2, 4))
    private val signature3 = digitalSignatureAndMetadata(publicKey3, byteArrayOf(1, 2, 5))
    private val signatureNotary = digitalSignatureAndMetadata(publicKeyNotary, byteArrayOf(1, 2, 6))
    private val signatureAnotherNotary = digitalSignatureAndMetadata(publicKeyAnotherNotary, byteArrayOf(1, 2, 7))

    private val notaryInfo = mock<NotaryInfo>().also {
        whenever(it.isBackchainRequired).thenReturn(true)
        whenever(it.publicKey).thenReturn(publicKeyNotary)
        whenever(it.name).thenReturn(notaryX500Name)
    }
    private val notaryLookup = mock<NotaryLookup>().also {
        whenever(it.lookup(notaryX500Name)).thenReturn(notaryInfo)
    }

    private val metadata = mock<TransactionMetadataInternal>()

    private val currentGroupParameters = mock<SignedGroupParameters>().also {
        whenever(it.hash).thenReturn(transactionGroupParametersHash)
        whenever(it.notaries).thenReturn(listOf(notaryInfo))
    }
    private val ledgerTransaction = mock<UtxoLedgerTransactionImpl>()
    private val signedTransaction = mock<UtxoSignedTransactionInternal>()
    private val signedTransactionWithOwnKeys = mock<UtxoSignedTransactionInternal>()
    private val notarizedTransaction = mock<UtxoSignedTransactionInternal>()

    private val filteredOutputStateAndRefs = mock<Audit<StateAndRef<*>>>()
    private val filteredTransaction = mock<UtxoFilteredTransaction>().also {
        whenever(it.outputStateAndRefs).thenReturn(filteredOutputStateAndRefs)
        whenever(it.notaryKey).thenReturn(publicKeyNotary)
        whenever(it.notaryName).thenReturn(notaryX500Name)
    }

    private val filteredTxAndSig = FilteredTransactionAndSignatures(filteredTransaction, listOf(signatureNotary))
    private val filteredTxPayload = listOf(filteredTxAndSig)
    private val finalityPayload = FinalityPayload(signedTransaction, true)
    private val verifyingFinalityPayload = FinalityPayload(signedTransaction, true, filteredTxPayload)

    private val receivedPayloadV2 = FinalityPayload(signedTransaction, true)
    private val receivedPayloadV2ForTwoParties = FinalityPayload(signedTransaction, false)

    @BeforeEach
    fun beforeEach() {
        whenever(session.counterparty).thenReturn(MEMBER)

        whenever(session.receive(UtxoSignedTransactionInternal::class.java)).thenReturn(signedTransaction)
        whenever(session.receive(FinalityPayload::class.java)).thenReturn(receivedPayloadV2)

        whenever(memberLookup.myInfo()).thenReturn(memberInfo)
        whenever(memberInfo.ledgerKeys).thenReturn(listOf(publicKey1, publicKey2))

        whenever(flowEngine.subFlow(any<TransactionBackchainResolutionFlow>())).thenReturn(Unit)

        whenever(signedTransaction.id).thenReturn(ID)
        whenever(signedTransaction.metadata).thenReturn(metadata)
        whenever(signedTransaction.notaryKey).thenReturn(publicKeyNotary)
        whenever(signedTransaction.notaryName).thenReturn(notaryX500Name)
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

        whenever(metadata.getMembershipGroupParametersHash()).thenReturn(transactionGroupParametersHash.toString())

        whenever(groupParametersLookup.currentGroupParameters).thenReturn(currentGroupParameters)
    }

    @Test
    fun `receiving a transaction that passes verification and notarization is signed and recorded`() {
        whenever(signedTransaction.addMissingSignatures()).thenReturn(signedTransactionWithOwnKeys to listOf(signature1, signature2))
        whenever(session.receive(List::class.java)).thenReturn(listOf(signature3))
        whenever(session.receive(Payload::class.java)).thenReturn(Payload.Success(listOf(signatureNotary)))

        callReceiveFinalityFlow()

        verify(signedTransaction).addMissingSignatures()

        verify(signedTransactionWithOwnKeys).addSignature(signature3)
        verify(persistenceService, times(1)).persist(signedTransactionWithOwnKeys, TransactionStatus.UNVERIFIED)
        verify(persistenceService).persistTransactionSignatures(
            ID,
            2,
            listOf()
        )
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
    fun `receiving a transaction initially with not the current group parameters throws and persists as invalid`() {
        whenever(currentGroupParameters.hash).thenReturn(anotherGroupParametersHash)
        assertThatThrownBy { callReceiveFinalityFlow() }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessageContaining("Transactions can be created only with the latest membership group parameters.")

        verify(signedTransaction, never()).addMissingSignatures()
        verify(persistenceService).persist(signedTransaction, TransactionStatus.INVALID)
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
        verify(persistenceService, times(1)).persist(signedTransactionWithOwnKeys, TransactionStatus.UNVERIFIED)
        verify(persistenceService).persistTransactionSignatures(
            ID,
            2,
            listOf()
        )
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
        verify(persistenceService, times(1)).persist(signedTransactionWithOwnKeys, TransactionStatus.UNVERIFIED)
        verify(persistenceService).persistTransactionSignatures(
            ID,
            2,
            listOf()
        )
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
        verify(persistenceService, times(1)).persist(signedTransactionWithOwnKeys, TransactionStatus.UNVERIFIED)
        verify(persistenceService).persistTransactionSignatures(
            ID,
            2,
            listOf()
        )
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
        verify(persistenceService, times(1)).persist(signedTransactionWithOwnKeys, TransactionStatus.UNVERIFIED)
        verify(persistenceService).persistTransactionSignatures(
            ID,
            2,
            listOf()
        )
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
        verify(persistenceService, times(1)).persist(signedTransactionWithOwnKeys, TransactionStatus.UNVERIFIED)
        verify(persistenceService).persistTransactionSignatures(
            ID,
            2,
            listOf()
        )
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
        verify(persistenceService, times(1)).persist(signedTransactionWith1Key, TransactionStatus.UNVERIFIED)
        verify(persistenceService).persistTransactionSignatures(
            ID,
            1,
            listOf()
        )
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
        verify(persistenceService, times(1)).persist(signedTransaction, TransactionStatus.UNVERIFIED)
        verify(persistenceService).persistTransactionSignatures(
            ID,
            1,
            listOf()
        )
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
        whenever(
            signedTransactionWithOwnKeys.verifySignatorySignatures()
        ).thenThrow(TransactionSignatureException(ID, "There are missing signatures", null))
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
        whenever(signedTransaction.inputStateRefs).thenReturn(listOf(mock()))
        whenever(signedTransaction.referenceStateRefs).thenReturn(listOf(mock()))
        whenever(session.receive(List::class.java)).thenReturn(listOf(signature3))
        whenever(session.receive(Payload::class.java)).thenReturn(Payload.Success(listOf(signatureNotary)))

        callReceiveFinalityFlow()

        verify(flowEngine).subFlow(TransactionBackchainResolutionFlow(signedTransaction.dependencies, session))
    }

    @Test
    fun `receiving a transaction resolves the transaction's backchain even when it fails verification`() {
        whenever(ledgerTransaction.outputStateAndRefs).thenReturn(listOf(getExampleInvalidStateAndRefImpl()))
        whenever(signedTransaction.inputStateRefs).thenReturn(listOf(mock()))
        whenever(signedTransaction.referenceStateRefs).thenReturn(listOf(mock()))
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

    @Test
    fun `if receiving a transaction with no dependencies then the backchain resolution flow will not be called`() {
        whenever(signedTransaction.addMissingSignatures()).thenReturn(signedTransactionWithOwnKeys to listOf(signature1, signature2))

        whenever(signedTransaction.inputStateRefs).thenReturn(emptyList())
        whenever(signedTransaction.referenceStateRefs).thenReturn(emptyList())
        whenever(session.receive(List::class.java)).thenReturn(listOf(signature3))
        whenever(session.receive(Payload::class.java)).thenReturn(Payload.Success(listOf(signatureNotary)))

        callReceiveFinalityFlow()

        verify(flowEngine, never()).subFlow(TransactionBackchainResolutionFlow(signedTransaction.dependencies, session))
    }

    @Test
    fun `skip receiving and persisting signatures when there are only two parties`() {
        whenever(session.receive(FinalityPayload::class.java)).thenReturn(receivedPayloadV2ForTwoParties)
        whenever(signedTransaction.addMissingSignatures()).thenReturn(signedTransactionWithOwnKeys to listOf(signature1))
        whenever(session.receive(Payload::class.java)).thenReturn(Payload.Success(listOf(signatureNotary)))

        callReceiveFinalityFlow()

        verify(session, never()).receive(List::class.java)
        verify(persistenceService, times(1)).persist(signedTransactionWithOwnKeys, TransactionStatus.UNVERIFIED)
    }

    @Test
    fun `receiving and persisting signatures when there are more than two parties`() {
        whenever(signedTransaction.addMissingSignatures()).thenReturn(signedTransactionWithOwnKeys to listOf(signature1, signature2))
        whenever(session.receive(List::class.java)).thenReturn(listOf(signature3))
        whenever(session.receive(Payload::class.java)).thenReturn(Payload.Success(listOf(signatureNotary)))

        callReceiveFinalityFlow()

        verify(session, times(1)).receive(List::class.java)
        verify(persistenceService, times(1)).persist(signedTransactionWithOwnKeys, TransactionStatus.UNVERIFIED)
        verify(persistenceService).persistTransactionSignatures(
            ID,
            2,
            listOf()
        )
    }

    @Test
    fun `skip backchain with a backchain not required notary`() {
        whenever(notaryInfo.isBackchainRequired).thenReturn(false)
        whenever(signedTransaction.addMissingSignatures()).thenReturn(signedTransactionWithOwnKeys to listOf(signature1, signature2))
        whenever(session.receive(FinalityPayload::class.java)).thenReturn(verifyingFinalityPayload)
        whenever(session.receive(List::class.java)).thenReturn(listOf(signature3))
        whenever(session.receive(Payload::class.java)).thenReturn(Payload.Success(listOf(signatureNotary)))

        callReceiveFinalityFlow()

        verify(flowEngine, timeout(100).times(0)).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `run backchain with a backchain required notary`() {
        val inputState = mock<StateRef>()
        whenever(signedTransaction.inputStateRefs).thenReturn(listOf(inputState))
        whenever(signedTransaction.addMissingSignatures()).thenReturn(signedTransactionWithOwnKeys to listOf(signature1, signature2))
        whenever(session.receive(FinalityPayload::class.java)).thenReturn(finalityPayload)
        whenever(session.receive(List::class.java)).thenReturn(listOf(signature3))
        whenever(session.receive(Payload::class.java)).thenReturn(Payload.Success(listOf(signatureNotary)))

        callReceiveFinalityFlow()

        verify(flowEngine, timeout(100).times(1)).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `fail if received signatures in filtered tx are not signed by the notary of initial tx`() {
        val filteredOutputStateAndRefs = mock<Audit<StateAndRef<*>>>()
        val filteredTransaction = mock<UtxoFilteredTransaction>().also {
            whenever(it.outputStateAndRefs).thenReturn(filteredOutputStateAndRefs)
            whenever(it.notaryKey).thenReturn(publicKeyAnotherNotary)
            whenever(it.notaryName).thenReturn(notaryX500Name)
        }
        val filteredTxAndSig = FilteredTransactionAndSignatures(filteredTransaction, listOf(signatureAnotherNotary))
        val filteredTxPayload = listOf(filteredTxAndSig)
        val finalityPayload = FinalityPayload(signedTransaction, true, filteredTxPayload)

        whenever(signedTransaction.addMissingSignatures()).thenReturn(signedTransactionWithOwnKeys to listOf(signature1, signature2))
        whenever(session.receive(FinalityPayload::class.java)).thenReturn(finalityPayload)
        whenever(session.receive(List::class.java)).thenReturn(listOf(signature3))
        whenever(session.receive(Payload::class.java)).thenReturn(Payload.Success(listOf(signatureNotary)))

        whenever(notaryInfo.isBackchainRequired).thenReturn(false)
        whenever(notarySignatureVerificationService.verifyNotarySignatures(any(), any(), any(), any()))
            .thenThrow(CordaRuntimeException("Failed to verify signature"))

        assertThatThrownBy { callReceiveFinalityFlow() }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessageContaining(
                "Failed to verify signature"
            )
    }

    @Test
    fun `fail if notary name of filtered tx doesn't match with one in initial tx`() {
        val anotherNotaryInfo = mock<NotaryInfo>().also {
            whenever(it.isBackchainRequired).thenReturn(false)
            whenever(it.publicKey).thenReturn(publicKeyAnotherNotary)
            whenever(it.name).thenReturn(anotherNotaryX500Name)
        }

        whenever(currentGroupParameters.notaries).thenReturn(listOf(anotherNotaryInfo))

        whenever(anotherNotaryInfo.isBackchainRequired).thenReturn(false)
        whenever(anotherNotaryInfo.name).thenReturn(anotherNotaryX500Name)
        whenever(notaryLookup.lookup(anotherNotaryX500Name)).thenReturn(anotherNotaryInfo)

        whenever(signedTransaction.notaryName).thenReturn(anotherNotaryX500Name)
        whenever(signedTransaction.notaryKey).thenReturn(publicKeyAnotherNotary)
        whenever(signedTransaction.addMissingSignatures()).thenReturn(signedTransactionWithOwnKeys to listOf(signature1, signature2))
        whenever(session.receive(FinalityPayload::class.java)).thenReturn(verifyingFinalityPayload)
        whenever(session.receive(List::class.java)).thenReturn(listOf(signature3))
        whenever(session.receive(Payload::class.java)).thenReturn(Payload.Success(listOf(signatureNotary)))

        assertThatThrownBy { callReceiveFinalityFlow() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining(
                "Notary name of filtered transaction \"${filteredTransaction.notaryName}\" doesn't match with " +
                    "notary name of initial transaction \"${anotherNotaryX500Name}\""
            )
    }

    @Test
    fun `notary composite key in initial tx should be able to comparable`() {
        val filteredOutputStateAndRefs = mock<Audit<StateAndRef<*>>>()
        val filteredTransaction = mock<UtxoFilteredTransaction>().also {
            whenever(it.outputStateAndRefs).thenReturn(filteredOutputStateAndRefs)
            whenever(it.notaryKey).thenReturn(publicKeyNotary)
            whenever(it.notaryName).thenReturn(notaryX500Name)
        }
        val filteredTxAndSig = FilteredTransactionAndSignatures(filteredTransaction, listOf(signatureNotary))
        val filteredTxPayload = listOf(filteredTxAndSig)
        val finalityPayload = FinalityPayload(signedTransaction, true, filteredTxPayload)

        whenever(notaryInfo.publicKey).thenReturn(compositeKeyNotary)
        whenever(notaryInfo.isBackchainRequired).thenReturn(false)

        whenever(currentGroupParameters.notaries).thenReturn(listOf(notaryInfo))

        whenever(signedTransaction.notaryKey).thenReturn(compositeKeyNotary)
        whenever(signedTransaction.addMissingSignatures()).thenReturn(signedTransactionWithOwnKeys to listOf(signature1, signature2))

        whenever(session.receive(FinalityPayload::class.java)).thenReturn(finalityPayload)
        whenever(session.receive(List::class.java)).thenReturn(listOf(signature3))
        whenever(session.receive(Payload::class.java)).thenReturn(Payload.Success(listOf(signatureNotary)))

        whenever(
            notarySignatureVerificationService.verifyNotarySignatures(
                filteredTransaction,
                signedTransaction.notaryKey,
                listOf(signatureNotary),
                mutableMapOf()
            )
        ).thenAnswer { }

        callReceiveFinalityFlow()

        verify(notarySignatureVerificationService).verifyNotarySignatures(
            filteredTransaction,
            signedTransaction.notaryKey,
            listOf(signatureNotary),
            mutableMapOf()
        )
    }

    private fun callReceiveFinalityFlow(validator: UtxoTransactionValidator = UtxoTransactionValidator { }) {
        val flow = UtxoReceiveFinalityFlowV1(session, validator)
        flow.notaryLookup = notaryLookup
        flow.memberLookup = memberLookup
        flow.persistenceService = persistenceService
        flow.transactionVerificationService = transactionVerificationService
        flow.flowEngine = flowEngine
        flow.visibilityChecker = visibilityChecker
        flow.groupParametersLookup = groupParametersLookup
        flow.utxoLedgerGroupParametersPersistenceService = utxoLedgerGroupParametersPersistenceService
        flow.signedGroupParametersVerifier = signedGroupParametersVerifier
        flow.notarySignatureVerificationService = notarySignatureVerificationService
        flow.call()
    }

    private fun digitalSignatureAndMetadata(publicKey: PublicKey, byteArray: ByteArray): DigitalSignatureAndMetadata {
        return DigitalSignatureAndMetadata(
            DigitalSignatureWithKeyId(publicKey.fullIdHash(), byteArray),
            DigitalSignatureMetadata(Instant.now(), SignatureSpecImpl("dummySignatureName"), emptyMap())
        )
    }
}
