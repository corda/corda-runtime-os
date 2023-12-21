package net.corda.ledger.utxo.flow.impl.flows.transactiontransmission

import net.corda.crypto.core.SecureHashImpl
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionInternal
import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainResolutionFlow
import net.corda.ledger.utxo.flow.impl.flows.backchain.dependencies
import net.corda.ledger.utxo.flow.impl.transaction.factory.UtxoLedgerTransactionFactory
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.ledger.utxo.StateRef
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

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
    private val ledgerTransaction = mock<UtxoLedgerTransactionInternal>()

    @BeforeEach
    fun beforeEach() {
        whenever(transaction.id).thenReturn(TX_ID_1)
        whenever(flowEngine.subFlow(any<TransactionBackchainResolutionFlow>())).thenReturn(Unit)
        whenever(utxoLedgerTransactionFactory.create(transaction)).thenReturn(ledgerTransaction)
    }

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
    fun `receiving transaction with no dependencies shouldn't call backchain resolution flow`() {
        whenever(sessionAlice.receive(WireTransaction::class.java)).thenReturn(transaction)
        whenever(ledgerTransaction.inputStateRefs).thenReturn(listOf())
        whenever(ledgerTransaction.referenceStateRefs).thenReturn(listOf())
        callReceiveTransactionFlow(sessionAlice)

        verify(sessionAlice).send(Payload.Success("Successfully received transaction."))
        verify(flowEngine, never()).subFlow(TransactionBackchainResolutionFlow(ledgerTransaction.dependencies, sessionAlice))
    }

    private fun callReceiveTransactionFlow(session: FlowSession) {
        val flow = spy(ReceiveLedgerTransactionFlow(session))
        flow.utxoLedgerTransactionFactory = utxoLedgerTransactionFactory
        flow.flowEngine = flowEngine
        flow.call()
    }
}
