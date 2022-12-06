package net.corda.ledger.utxo.flow.impl.flows.backchain

import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

class TransactionBackchainResolutionFlowTest {

    private companion object {
        val TX_ID_1 = SecureHash("SHA", byteArrayOf(2, 2, 2, 2))
        val TX_ID_2 = SecureHash("SHA", byteArrayOf(3, 3, 3, 3))
        val TX_ID_3 = SecureHash("SHA", byteArrayOf(4, 4, 4, 4))
        val TX_2_INPUT_DEPENDENCY_STATE_REF_1 = StateRef(TX_ID_3, 0)
        val TX_3_INPUT_DEPENDENCY_STATE_REF_1 = StateRef(TX_ID_3, 0)
        val TX_3_INPUT_DEPENDENCY_STATE_REF_2 = StateRef(TX_ID_3, 1)

        val TX_3_INPUT_REFERENCE_DEPENDENCY_STATE_REF_1 = StateRef(TX_ID_3, 0)
        val TX_3_INPUT_REFERENCE_DEPENDENCY_STATE_REF_2 = StateRef(TX_ID_3, 1)
    }

    private val flowEngine = mock<FlowEngine>()
    private val transactionBackchainVerifier = mock<TransactionBackchainVerifier>()
    private val utxoLedgerPersistenceService = mock<UtxoLedgerPersistenceService>()

    private val session = mock<FlowSession>()
    private val transaction = mock<UtxoSignedTransaction>()
    private val ledgerTransaction = mock<UtxoLedgerTransaction>()

    @BeforeEach
    fun beforeEach() {
        whenever(transaction.id).thenReturn(TX_ID_1)
        whenever(transaction.toLedgerTransaction()).thenReturn(ledgerTransaction)
    }

    @Test
    fun `does nothing when the transaction has no dependencies`() {
        whenever(ledgerTransaction.inputStateRefs).thenReturn(emptyList())
        whenever(ledgerTransaction.referenceInputStateRefs).thenReturn(emptyList())

        callTransactionBackchainResolutionFlow()

        verifyNoInteractions(flowEngine)
        verifyNoInteractions(transactionBackchainVerifier)
        verifyNoInteractions(utxoLedgerPersistenceService)
    }

    @Test
    fun `does nothing when the transactions dependencies are already verified`() {
        whenever(ledgerTransaction.inputStateRefs).thenReturn(
            listOf(
                TX_2_INPUT_DEPENDENCY_STATE_REF_1,
                TX_3_INPUT_DEPENDENCY_STATE_REF_1,
                TX_3_INPUT_DEPENDENCY_STATE_REF_2
            )
        )
        whenever(ledgerTransaction.referenceInputStateRefs).thenReturn(
            listOf(
                TX_3_INPUT_REFERENCE_DEPENDENCY_STATE_REF_1,
                TX_3_INPUT_REFERENCE_DEPENDENCY_STATE_REF_2
            )
        )

        whenever(utxoLedgerPersistenceService.find(any(), eq(TransactionStatus.VERIFIED))).thenReturn(mock())

        callTransactionBackchainResolutionFlow()

        verifyNoInteractions(flowEngine)
        verifyNoInteractions(transactionBackchainVerifier)
    }

    @Test
    fun `retrieves and verifies transactions dependencies that are not verified`() {
        whenever(ledgerTransaction.inputStateRefs).thenReturn(
            listOf(
                TX_2_INPUT_DEPENDENCY_STATE_REF_1,
                TX_3_INPUT_DEPENDENCY_STATE_REF_1,
                TX_3_INPUT_DEPENDENCY_STATE_REF_2
            )
        )
        whenever(ledgerTransaction.referenceInputStateRefs).thenReturn(
            listOf(
                TX_3_INPUT_REFERENCE_DEPENDENCY_STATE_REF_1,
                TX_3_INPUT_REFERENCE_DEPENDENCY_STATE_REF_2
            )
        )

        whenever(utxoLedgerPersistenceService.find(TX_ID_2, TransactionStatus.VERIFIED)).thenReturn(mock())
        whenever(utxoLedgerPersistenceService.find(TX_ID_3, TransactionStatus.VERIFIED)).thenReturn(null)

        whenever(flowEngine.subFlow(any<TransactionBackchainReceiverFlow>())).thenReturn(TopologicalSort())

        callTransactionBackchainResolutionFlow()

        verify(flowEngine).subFlow(TransactionBackchainReceiverFlow(TX_ID_1, setOf(TX_ID_3), session))
        verifyNoMoreInteractions(flowEngine)

        verify(transactionBackchainVerifier).verify(eq(TX_ID_1), any())
    }

    @Test
    fun `rethrows exception thrown from transaction verification`() {

    }

    private fun callTransactionBackchainResolutionFlow() {
        TransactionBackchainResolutionFlow(transaction, session).apply {
            flowEngine = this@TransactionBackchainResolutionFlowTest.flowEngine
            transactionBackchainVerifier = this@TransactionBackchainResolutionFlowTest.transactionBackchainVerifier
            utxoLedgerPersistenceService = this@TransactionBackchainResolutionFlowTest.utxoLedgerPersistenceService
        }.call()
    }
}