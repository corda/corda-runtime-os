package net.corda.ledger.utxo.flow.impl.flows.transactiontransmission

import net.corda.crypto.core.SecureHashImpl
import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainSenderFlow
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SendTransactionFlowTest {

    private companion object {
        val TX_ID = SecureHashImpl("SHA", byteArrayOf(1, 1, 1, 1))
    }

    private val flowEngine = mock<FlowEngine>()
    private val flowMessaging = mock<FlowMessaging>()

    private val sessionAlice = mock<FlowSession>()
    private val sessionBob = mock<FlowSession>()
    private val sessions = listOf(sessionAlice, sessionBob)

    private val transaction = mock<UtxoSignedTransactionInternal>()
    private val successMessage = "Successfully received transaction."

    @BeforeEach
    fun beforeEach() {
        whenever(transaction.id).thenReturn(TX_ID)
        whenever(flowEngine.subFlow(any<TransactionBackchainSenderFlow>())).thenReturn(Unit)
    }

    @Test
    fun `does nothing when receiving payload successfully`() {
        whenever(sessionAlice.receive(Payload::class.java)).thenReturn(
            Payload.Success(successMessage)
        )
        whenever(sessionBob.receive(Payload::class.java)).thenReturn(
            Payload.Success(successMessage)
        )

        callSendTransactionFlow(transaction, sessions)

        verify(flowMessaging).sendAll(transaction, sessions.toSet())
        verify(sessionAlice).receive(Payload::class.java)
    }

    @Test
    fun `sending transaction with dependencies should call backchain flow`() {
        whenever(transaction.inputStateRefs).thenReturn(listOf(mock()))

        whenever(sessionAlice.receive(Payload::class.java)).thenReturn(
            Payload.Success(successMessage)
        )
        whenever(sessionBob.receive(Payload::class.java)).thenReturn(
            Payload.Success(successMessage)
        )

        callSendTransactionFlow(transaction, sessions)

        verify(flowEngine).subFlow(TransactionBackchainSenderFlow(TX_ID, sessionAlice))
    }

    @Test
    fun `sending transaction with no dependencies should not call backchain flow`() {
        whenever(sessionAlice.receive(Payload::class.java)).thenReturn(
            Payload.Success(successMessage)
        )
        whenever(sessionBob.receive(Payload::class.java)).thenReturn(
            Payload.Success(successMessage)
        )

        callSendTransactionFlow(transaction, sessions)

        verify(flowEngine, never()).subFlow(TransactionBackchainSenderFlow(TX_ID, sessionAlice))
    }

    @Test
    fun `sending unverified transaction should throw exception`() {
        whenever(sessionAlice.receive(Payload::class.java)).thenReturn(
            Payload.Failure<List<DigitalSignatureAndMetadata>>("fail")
        )

        assertThatThrownBy { callSendTransactionFlow(transaction, sessions) }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessageContaining("fail")
    }

    private fun callSendTransactionFlow(signedTransaction: UtxoSignedTransaction, sessions: List<FlowSession>) {
        val flow = spy(SendTransactionFlow(signedTransaction, sessions))

        flow.flowEngine = flowEngine
        flow.flowMessaging = flowMessaging
        flow.call()
    }
}