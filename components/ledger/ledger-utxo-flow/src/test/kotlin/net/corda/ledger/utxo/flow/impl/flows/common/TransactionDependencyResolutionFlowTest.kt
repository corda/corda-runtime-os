package net.corda.ledger.utxo.flow.impl.flows.common

import net.corda.crypto.cipher.suite.SignatureSpecImpl
import net.corda.crypto.core.DigitalSignatureWithKeyId
import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.fullIdHash
import net.corda.ledger.utxo.data.transaction.UtxoFilteredTransactionAndSignaturesImpl
import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainResolutionFlow
import net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.common.TransactionDependencyResolutionFlow
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.test.UtxoLedgerTest
import net.corda.ledger.utxo.testkit.notaryX500Name
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.NotarySignatureVerificationService
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransaction
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransactionAndSignatures
import net.corda.v5.membership.GroupParameters
import net.corda.v5.membership.GroupParametersLookup
import net.corda.v5.membership.NotaryInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.security.PublicKey
import java.time.Instant

class TransactionDependencyResolutionFlowTest : UtxoLedgerTest() {
    private companion object {
        val TX_ID_1 = SecureHashImpl("SHA", byteArrayOf(2, 2, 2, 2))
        val TX_ID_2 = SecureHashImpl("SHA", byteArrayOf(3, 3, 3, 3))

        val TX_INPUT_DEPENDENCY_STATE_REF_1 = StateRef(TX_ID_1, 0)
        val TX_INPUT_DEPENDENCY_STATE_REF_2 = StateRef(TX_ID_2, 0)

        val TX_INPUT_REFERENCE_DEPENDENCY_STATE_REF_1 = StateRef(TX_ID_2, 0)
    }

    private val publicKey1 = mock<PublicKey>().also { whenever(it.encoded).thenReturn(byteArrayOf(0x01)) }
    private val notarySignature = DigitalSignatureAndMetadata(
        DigitalSignatureWithKeyId(publicKey1.fullIdHash(), byteArrayOf(1, 2, 2)),
        DigitalSignatureMetadata(Instant.now(), SignatureSpecImpl("dummySignatureName"), emptyMap())
    )
    private val filteredTransaction = mock<UtxoFilteredTransaction>()
    private val filteredDependency = UtxoFilteredTransactionAndSignaturesImpl(
        filteredTransaction,
        listOf(notarySignature)
    )

    private val mockFlowEngine = mock<FlowEngine>()
    private val sessionAlice = mock<FlowSession>()
    private val mockNotary = mock<NotaryInfo>()
    private val mockLedgerPersistenceService = mock<UtxoLedgerPersistenceService>()
    private val mockNotarySignatureVerificationService = mock<NotarySignatureVerificationService>()
    private val mockGroupParametersLookup = mock<GroupParametersLookup>()
    private val filteredDependencyStorage = mutableListOf<UtxoFilteredTransactionAndSignatures>()

    @BeforeEach
    fun beforeEach() {
        filteredDependencyStorage.clear()

        // Notary backchain on by default
        whenever(mockNotary.isBackchainRequired).thenReturn(true)
        whenever(mockNotary.publicKey).thenReturn(publicKey1)
        whenever(mockNotary.name).thenReturn(notaryX500Name)
        whenever(mockNotaryLookup.lookup(notaryX500Name)).thenReturn(mockNotary)

        // Group parameters notary is valid by default
        val mockGroupParameters = mock<GroupParameters> {
            on { notaries } doReturn listOf(mockNotary)
        }
        whenever(mockGroupParametersLookup.currentGroupParameters).thenReturn(mockGroupParameters)
        whenever(mockLedgerPersistenceService.persistFilteredTransactionsAndSignatures(any())).doAnswer {
            @Suppress("unchecked_cast")
            filteredDependencyStorage.addAll(it.arguments.first() as List<UtxoFilteredTransactionAndSignatures>)
            Unit
        }
    }

    @Test
    fun `notary backchain on - receiving transaction with dependencies should call backchain resolution flow`() {
        callReceiveTransactionFlow(
            sessionAlice,
            TX_ID_1,
            notaryX500Name,
            setOf(TX_INPUT_DEPENDENCY_STATE_REF_1.transactionId, TX_INPUT_DEPENDENCY_STATE_REF_2.transactionId)
        )

        verify(mockFlowEngine).subFlow(
            TransactionBackchainResolutionFlow(
                setOf(TX_INPUT_DEPENDENCY_STATE_REF_1.transactionId, TX_INPUT_DEPENDENCY_STATE_REF_2.transactionId),
                sessionAlice
            )
        )
    }

    @Test
    fun `notary backchain on - receiving transaction with no dependencies shouldn't call backchain resolution flow`() {
        callReceiveTransactionFlow(
            sessionAlice,
            TX_ID_1,
            notaryX500Name,
            emptySet()
        )

        verify(mockFlowEngine, never()).subFlow(
            TransactionBackchainResolutionFlow(
                emptySet(),
                sessionAlice
            )
        )
    }

    @Test
    fun `notary backchain off - will verify filtered transactions and will not call backchain resolution`() {
        // Filtered dependency passes verification
        whenever(filteredTransaction.verify()).doAnswer { }
        whenever(filteredTransaction.notaryName).thenReturn(notaryX500Name)
        whenever(
            mockNotarySignatureVerificationService.verifyNotarySignatures(
                filteredTransaction,
                mockNotary.publicKey,
                listOf(notarySignature),
                emptyMap()
            )
        ).thenAnswer { }

        whenever(mockNotary.isBackchainRequired).thenReturn(false)

        callReceiveTransactionFlow(
            sessionAlice,
            TX_ID_1,
            notaryX500Name,
            setOf(TX_INPUT_DEPENDENCY_STATE_REF_1.transactionId),
            listOf(filteredDependency)
        )

        verify(mockFlowEngine, never()).subFlow(
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
        assertThat(filteredDependencyStorage).containsExactly(filteredDependency)
    }

    private fun callReceiveTransactionFlow(
        session: FlowSession,
        transactionId: SecureHash,
        notaryName: MemberX500Name,
        transactionDependencies: Set<SecureHash>,
        filteredDependencies: List<UtxoFilteredTransactionAndSignatures>? = null
    ) {
        val flow = TransactionDependencyResolutionFlow(
            session,
            transactionId,
            notaryName,
            transactionDependencies,
            filteredDependencies
        )
        flow.flowEngine = mockFlowEngine
        flow.ledgerPersistenceService = mockLedgerPersistenceService
        flow.notarySignatureVerificationService = mockNotarySignatureVerificationService
        flow.groupParametersLookup = mockGroupParametersLookup
        flow.call()
    }
}
