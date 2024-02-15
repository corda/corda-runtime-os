package net.corda.ledger.utxo.flow.impl.flows.transactiontransmission

import net.corda.crypto.core.SecureHashImpl
import net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.common.SendTransactionFlow
import net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.v1.SendSignedTransactionFlowV1
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.ledger.utxo.testkit.notaryX500Name
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.messaging.FlowSession
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.lang.IllegalArgumentException

class SendSignedTransactionFlowV1Test {

    private val mockFlowEngine = mock<FlowEngine>()

    private val signedTransaction = mock<UtxoSignedTransactionInternal>()

    @BeforeEach
    fun beforeEach() {
        whenever(signedTransaction.id).thenReturn(SecureHashImpl("SHA", byteArrayOf(1, 1, 1)))
        whenever(signedTransaction.notaryName).thenReturn(notaryX500Name)
        whenever(signedTransaction.inputStateRefs).thenReturn(emptyList())
        whenever(signedTransaction.referenceStateRefs).thenReturn(emptyList())
    }

    @Test
    fun `flow should respond with success payload if sub-flow executes properly`() {
        whenever(mockFlowEngine.subFlow(any<SendTransactionFlow<UtxoSignedTransactionInternal>>())).thenAnswer { }

        callSendSignedTransactionFlow(signedTransaction, listOf(mock()))
    }

    @Test
    fun `sub-flow error is propagated and main flow fails too`() {
        whenever(mockFlowEngine.subFlow(any<SendTransactionFlow<UtxoSignedTransactionInternal>>())).thenAnswer {
            throw IllegalArgumentException("Error sending transaction!")
        }

        val ex = assertThrows<IllegalArgumentException> {
            callSendSignedTransactionFlow(signedTransaction, listOf(mock()))
        }

        assertThat(ex).hasStackTraceContaining("Error sending transaction!")
    }

    private fun callSendSignedTransactionFlow(
        signedTransaction: UtxoSignedTransactionInternal,
        sessions: List<FlowSession>
    ) {
        val flow = SendSignedTransactionFlowV1(signedTransaction, sessions)

        flow.flowEngine = mockFlowEngine
        flow.call()
    }
}
