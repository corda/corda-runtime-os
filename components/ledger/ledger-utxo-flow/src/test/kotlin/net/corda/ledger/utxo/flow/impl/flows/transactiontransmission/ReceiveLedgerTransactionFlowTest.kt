package net.corda.ledger.utxo.flow.impl.flows.transactiontransmission

import net.corda.crypto.core.SecureHashImpl
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainResolutionFlow
import net.corda.ledger.utxo.flow.impl.flows.backchain.dependencies
import net.corda.ledger.utxo.flow.impl.flows.finality.v1.UtxoFinalityFlowV1Test.TestContact
import net.corda.ledger.utxo.flow.impl.transaction.factory.UtxoLedgerTransactionFactory
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TransactionState
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.security.PublicKey

class ReceiveLedgerTransactionFlowTest {
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
    private var utxoLedgerTransactionFactory = mock<UtxoLedgerTransactionFactory>()

    private val sessionAlice = mock<FlowSession>()

    private val transaction = mock<WireTransaction>()
    private val ledgerTransaction = mock<UtxoLedgerTransaction>()

    private val publicKeyAlice = mock<PublicKey>().also { whenever(it.encoded).thenReturn(byteArrayOf(0x01)) }
    private val publicKeyBob = mock<PublicKey>().also { whenever(it.encoded).thenReturn(byteArrayOf(0x02)) }

    private val stateAndRef = mock<StateAndRef<TestState>>()
    private val transactionState = mock<TransactionState<TestState>>()
    private val testState = TestState(listOf(publicKeyAlice))

    @BeforeEach
    fun beforeEach() {
        whenever(transaction.id).thenReturn(TX_ID_1)
        whenever(flowEngine.subFlow(any<TransactionBackchainResolutionFlow>())).thenReturn(Unit)
//        whenever(utxoLedgerTransactionFactory.create(transaction)).thenReturn(ledgerTransaction)

        // Single output State
//        whenever(transaction.outputStateAndRefs).thenReturn(listOf(stateAndRef))
        whenever(stateAndRef.state).thenReturn(transactionState)
        whenever(transactionState.contractType).thenReturn(TestContact::class.java)
        whenever(transactionState.contractState).thenReturn(testState)
    }

//    @Test
//    fun `successful verification in receive flow should persist transaction`() {
//        whenever(sessionAlice.receive(UtxoSignedTransactionInternal::class.java)).thenReturn(transaction)
//        whenever(visibilityChecker.containsMySigningKeys(listOf(publicKeyAlice))).thenReturn(true)
//        whenever(visibilityChecker.containsMySigningKeys(listOf(publicKeyBob))).thenReturn(true)
//
//        callReceiveTransactionFlow(sessionAlice)
//
//        verify(utxoLedgerPersistenceService).persist(transaction, VERIFIED, transaction.getVisibleStateIndexes(visibilityChecker))
//    }

    @Test
    fun `receiving transaction with dependencies should call backchain resolution flow`() {
        whenever(sessionAlice.receive(WireTransaction::class.java)).thenReturn(transaction)
        whenever(ledgerTransaction.inputStateRefs).thenReturn(
            listOf(
                TX_2_INPUT_DEPENDENCY_STATE_REF_1,
                TX_3_INPUT_DEPENDENCY_STATE_REF_1,
                TX_3_INPUT_DEPENDENCY_STATE_REF_2
            )
        )
        whenever(ledgerTransaction.referenceStateRefs).thenReturn(
            listOf(
                TX_3_INPUT_REFERENCE_DEPENDENCY_STATE_REF_1,
                TX_3_INPUT_REFERENCE_DEPENDENCY_STATE_REF_2
            )
        )

        callReceiveTransactionFlow(sessionAlice)

        verify(flowEngine).subFlow(TransactionBackchainResolutionFlow(ledgerTransaction.dependencies, sessionAlice))
        verify(sessionAlice).send(Payload.Success("Successfully received transaction."))
    }

    @Test
    fun `receiving invalid transaction with signatory signature verification failure should throw exception`() {
        whenever(sessionAlice.receive(WireTransaction::class.java)).thenReturn(transaction)

        verify(sessionAlice).receive(WireTransaction::class.java)
        verify(sessionAlice).send(
            Payload.Failure<List<DigitalSignatureAndMetadata>>(
                "Failed to verify transaction and signatures of transaction: ${transaction.id}"
            )
        )
    }

    @Test
    fun `receiving invalid transaction with notary signature verification failure should throw exception`() {
        whenever(sessionAlice.receive(WireTransaction::class.java)).thenReturn(transaction)

        verify(sessionAlice).receive(WireTransaction::class.java)
        verify(sessionAlice).send(
            Payload.Failure<List<DigitalSignatureAndMetadata>>(
                "Failed to verify transaction and signatures of transaction: ${transaction.id}"
            )
        )
    }

    @Test
    fun `receiving invalid transaction with transaction verification failure should throw exception`() {
        whenever(sessionAlice.receive(WireTransaction::class.java)).thenReturn(transaction)

        verify(sessionAlice).receive(WireTransaction::class.java)
        verify(sessionAlice).send(
            Payload.Failure<List<DigitalSignatureAndMetadata>>(
                "Failed to verify transaction and signatures of transaction: ${transaction.id}"
            )
        )
    }

    private fun callReceiveTransactionFlow(session: FlowSession) {
        val flow = spy(ReceiveLedgerTransactionFlow(session))
        flow.utxoLedgerTransactionFactory = utxoLedgerTransactionFactory
        flow.call()
    }

    class TestState(private val participants: List<PublicKey>) : ContractState {

        override fun getParticipants(): List<PublicKey> {
            return participants
        }
    }
}
