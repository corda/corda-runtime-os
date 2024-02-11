package net.corda.ledger.utxo.flow.impl.flows.transactiontransmission

import net.corda.crypto.cipher.suite.SignatureSpecImpl
import net.corda.crypto.core.DigitalSignatureWithKeyId
import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.fullIdHash
import net.corda.ledger.common.data.transaction.TransactionStatus.VERIFIED
import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.utxo.data.transaction.UtxoFilteredTransactionAndSignaturesImpl
import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainResolutionFlow
import net.corda.ledger.utxo.flow.impl.flows.backchain.dependencies
import net.corda.ledger.utxo.flow.impl.flows.finality.getVisibleStateIndexes
import net.corda.ledger.utxo.flow.impl.flows.finality.v1.UtxoFinalityFlowV1Test.TestContact
import net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.common.UtxoTransactionPayload
import net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.v1.ReceiveTransactionFlowV1
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.ledger.utxo.flow.impl.transaction.verifier.UtxoLedgerTransactionVerificationService
import net.corda.ledger.utxo.testkit.notaryX500Name
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.NotarySignatureVerificationService
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TransactionState
import net.corda.v5.ledger.utxo.VisibilityChecker
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransaction
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransactionAndSignatures
import net.corda.v5.membership.GroupParameters
import net.corda.v5.membership.GroupParametersLookup
import net.corda.v5.membership.NotaryInfo
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.security.PublicKey
import java.time.Instant

class ReceiveTransactionFlowTest {
    private companion object {
        val TX_ID_1 = SecureHashImpl("SHA", byteArrayOf(2, 2, 2, 2))
        val TX_ID_2 = SecureHashImpl("SHA", byteArrayOf(3, 3, 3, 3))

        val TX_2_INPUT_DEPENDENCY_STATE_REF_1 = StateRef(TX_ID_2, 0)
        val TX_3_INPUT_DEPENDENCY_STATE_REF_1 = StateRef(TX_ID_2, 0)
        val TX_3_INPUT_DEPENDENCY_STATE_REF_2 = StateRef(TX_ID_2, 1)

        val TX_3_INPUT_REFERENCE_DEPENDENCY_STATE_REF_1 = StateRef(TX_ID_2, 0)
        val TX_3_INPUT_REFERENCE_DEPENDENCY_STATE_REF_2 = StateRef(TX_ID_2, 1)
    }

    private val flowEngine = mock<FlowEngine>()

    private val utxoLedgerPersistenceService = mock<UtxoLedgerPersistenceService>()
    private val transactionVerificationService = mock<UtxoLedgerTransactionVerificationService>()
    private val visibilityChecker = mock<VisibilityChecker>()

    private val sessionAlice = mock<FlowSession>()

    private val transaction = mock<UtxoSignedTransactionInternal>()
    private val ledgerTransaction = mock<UtxoLedgerTransaction>()

    private val publicKeyAlice = mock<PublicKey>().also { whenever(it.encoded).thenReturn(byteArrayOf(0x01)) }
    private val publicKeyBob = mock<PublicKey>().also { whenever(it.encoded).thenReturn(byteArrayOf(0x02)) }
    private val publicKeyNotary = mock<PublicKey>().also { whenever(it.encoded).thenReturn(byteArrayOf(0x03)) }

    private val stateAndRef = mock<StateAndRef<TestState>>()
    private val transactionState = mock<TransactionState<TestState>>()
    private val testState = TestState(listOf(publicKeyAlice))

    private val mockNotary = mock<NotaryInfo>()
    private val mockNotarySignatureVerificationService = mock<NotarySignatureVerificationService>()
    private val mockGroupParametersLookup = mock<GroupParametersLookup>()
    private val filteredDependencyStorage = mutableListOf<UtxoFilteredTransactionAndSignatures>()
    private val mockNotaryLookup = mock<NotaryLookup>()

    private val notarySignature = DigitalSignatureAndMetadata(
        DigitalSignatureWithKeyId(publicKeyNotary.fullIdHash(), byteArrayOf(1, 2, 2)),
        DigitalSignatureMetadata(Instant.now(), SignatureSpecImpl("dummySignatureName"), emptyMap())
    )
    private val filteredTransaction = mock<UtxoFilteredTransaction>()
    private val filteredDependency = UtxoFilteredTransactionAndSignaturesImpl(
        filteredTransaction,
        listOf(notarySignature)
    )

    @BeforeEach
    fun beforeEach() {
        whenever(transaction.id).thenReturn(TX_ID_1)
        whenever(flowEngine.subFlow(any<TransactionBackchainResolutionFlow>())).thenReturn(Unit)
        whenever(transaction.toLedgerTransaction()).thenReturn(ledgerTransaction)

        // Single output State
        whenever(transaction.outputStateAndRefs).thenReturn(listOf(stateAndRef))
        whenever(stateAndRef.state).thenReturn(transactionState)
        whenever(transactionState.contractType).thenReturn(TestContact::class.java)
        whenever(transactionState.contractState).thenReturn(testState)

        // Notary backchain on by default
        whenever(mockNotary.isBackchainRequired).thenReturn(true)
        whenever(mockNotary.publicKey).thenReturn(publicKeyNotary)
        whenever(mockNotary.name).thenReturn(notaryX500Name)
        whenever(mockNotaryLookup.lookup(notaryX500Name)).thenReturn(mockNotary)

        // Group parameters notary is valid by default
        val mockGroupParameters = mock<GroupParameters> {
            on { notaries } doReturn listOf(mockNotary)
        }
        whenever(mockGroupParametersLookup.currentGroupParameters).thenReturn(mockGroupParameters)
        whenever(utxoLedgerPersistenceService.persistFilteredTransactionsAndSignatures(any())).doAnswer {
            @Suppress("unchecked_cast")
            filteredDependencyStorage.addAll(it.arguments.first() as List<UtxoFilteredTransactionAndSignatures>)
            Unit
        }

        whenever(transaction.notaryName).thenReturn(notaryX500Name)
    }

    @Test
    fun `notary backchain on - successful verification in receive flow should persist transaction`() {
        whenever(sessionAlice.receive(UtxoTransactionPayload::class.java)).thenReturn(UtxoTransactionPayload(transaction))
        whenever(visibilityChecker.containsMySigningKeys(listOf(publicKeyAlice))).thenReturn(true)
        whenever(visibilityChecker.containsMySigningKeys(listOf(publicKeyBob))).thenReturn(true)

        callReceiveTransactionFlow(sessionAlice)

        verify(utxoLedgerPersistenceService).persist(transaction, VERIFIED, transaction.getVisibleStateIndexes(visibilityChecker))
    }

    @Test
    fun `notary backchain on - receiving transaction with dependencies should call backchain resolution flow`() {
        whenever(sessionAlice.receive(UtxoTransactionPayload::class.java)).thenReturn(UtxoTransactionPayload(transaction))
        whenever(transaction.inputStateRefs).thenReturn(
            listOf(
                TX_2_INPUT_DEPENDENCY_STATE_REF_1,
                TX_3_INPUT_DEPENDENCY_STATE_REF_1,
                TX_3_INPUT_DEPENDENCY_STATE_REF_2
            )
        )
        whenever(transaction.referenceStateRefs).thenReturn(
            listOf(
                TX_3_INPUT_REFERENCE_DEPENDENCY_STATE_REF_1,
                TX_3_INPUT_REFERENCE_DEPENDENCY_STATE_REF_2
            )
        )

        callReceiveTransactionFlow(sessionAlice)

        verify(flowEngine).subFlow(TransactionBackchainResolutionFlow(transaction.dependencies, sessionAlice))
        verify(sessionAlice).send(Payload.Success("Successfully received transaction."))
    }

    @Test
    fun `notary backchain on - receiving invalid transaction with signatory signature verification failure should throw exception`() {
        whenever(sessionAlice.receive(UtxoTransactionPayload::class.java)).thenReturn(UtxoTransactionPayload(transaction))
        whenever(transaction.verifySignatorySignatures()).thenThrow(
            CordaRuntimeException("Failed to verify")
        )

        assertThatThrownBy { callReceiveTransactionFlow(sessionAlice) }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessageContaining("Failed to verify transaction")

        verify(sessionAlice).receive(UtxoTransactionPayload::class.java)
        verify(sessionAlice).send(
            Payload.Failure<List<DigitalSignatureAndMetadata>>(
                "Failed to verify transaction and signatures of transaction: ${transaction.id}"
            )
        )
    }

    @Test
    fun `notary backchain on - receiving invalid transaction with notary signature verification failure should throw exception`() {
        whenever(sessionAlice.receive(UtxoTransactionPayload::class.java)).thenReturn(UtxoTransactionPayload(transaction))
        whenever(transaction.verifyAttachedNotarySignature()).thenThrow(
            CordaRuntimeException("Failed to verify")
        )

        assertThatThrownBy { callReceiveTransactionFlow(sessionAlice) }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessageContaining("Failed to verify transaction")

        verify(sessionAlice).receive(UtxoTransactionPayload::class.java)
        verify(sessionAlice).send(
            Payload.Failure<List<DigitalSignatureAndMetadata>>(
                "Failed to verify transaction and signatures of transaction: ${transaction.id}"
            )
        )
    }

    @Test
    fun `notary backchain on - receiving invalid transaction with transaction verification failure should throw exception`() {
        whenever(sessionAlice.receive(UtxoTransactionPayload::class.java)).thenReturn(UtxoTransactionPayload(transaction))
        whenever(transactionVerificationService.verify(ledgerTransaction)).thenThrow(
            CordaRuntimeException("Failed to verify")
        )

        assertThatThrownBy { callReceiveTransactionFlow(sessionAlice) }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessageContaining("Failed to verify transaction")

        verify(sessionAlice).receive(UtxoTransactionPayload::class.java)
        verify(sessionAlice).send(
            Payload.Failure<List<DigitalSignatureAndMetadata>>(
                "Failed to verify transaction and signatures of transaction: ${transaction.id}"
            )
        )
    }

    @Test
    fun `notary backchain off - will verify filtered transactions and will not call backchain resolution`() {
        whenever(transaction.inputStateRefs).thenReturn(
            listOf(
                TX_2_INPUT_DEPENDENCY_STATE_REF_1
            )
        )
        // Filtered dependency passes verification
        whenever(filteredTransaction.verify()).doAnswer {  }
        whenever(filteredTransaction.notaryName).thenReturn(notaryX500Name)
        whenever(mockNotarySignatureVerificationService.verifyNotarySignatures(
            filteredTransaction,
            mockNotary.publicKey,
            listOf(notarySignature),
            emptyMap()
        )).thenAnswer {  }

        whenever(sessionAlice.receive(UtxoTransactionPayload::class.java)).thenReturn(
            UtxoTransactionPayload(
                transaction,
                filteredDependencies = listOf(filteredDependency)
            )
        )
        whenever(mockNotary.isBackchainRequired).thenReturn(false)

        callReceiveTransactionFlow(sessionAlice)

        verify(sessionAlice).send(Payload.Success("Successfully received transaction."))
        verify(flowEngine, never()).subFlow(
            TransactionBackchainResolutionFlow(
                setOf(),
                sessionAlice
            )
        )
        verify(mockNotarySignatureVerificationService).verifyNotarySignatures(
            filteredTransaction,
            mockNotary.publicKey,
            listOf(notarySignature),
            emptyMap()
        )
        verify(filteredTransaction).verify()
        Assertions.assertThat(filteredDependencyStorage).containsExactly(filteredDependency)
    }

    private fun callReceiveTransactionFlow(session: FlowSession) {
        val flow = spy(ReceiveTransactionFlowV1(session))

        flow.transactionVerificationService = transactionVerificationService
        flow.ledgerPersistenceService = utxoLedgerPersistenceService
        flow.flowEngine = flowEngine
        flow.visibilityChecker = visibilityChecker
        flow.notarySignatureVerificationService = mockNotarySignatureVerificationService
        flow.groupParametersLookup = mockGroupParametersLookup

        flow.call()
    }

    class TestState(private val participants: List<PublicKey>) : ContractState {

        override fun getParticipants(): List<PublicKey> {
            return participants
        }
    }
}
