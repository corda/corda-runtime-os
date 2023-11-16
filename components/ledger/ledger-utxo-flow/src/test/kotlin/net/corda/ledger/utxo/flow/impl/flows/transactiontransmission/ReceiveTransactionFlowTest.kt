package net.corda.ledger.utxo.flow.impl.flows.transactiontransmission

import net.corda.v5.ledger.utxo.VisibilityChecker
import net.corda.crypto.core.SecureHashImpl
import net.corda.ledger.common.data.transaction.TransactionStatus.VERIFIED
import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainResolutionFlow
import net.corda.ledger.utxo.flow.impl.flows.backchain.dependencies
import net.corda.ledger.utxo.flow.impl.flows.finality.getVisibleStateIndexes
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.ledger.utxo.flow.impl.transaction.verifier.UtxoLedgerTransactionVerificationService
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

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

    @BeforeEach
    fun beforeEach() {
        whenever(transaction.id).thenReturn(TX_ID_1)
        whenever(flowEngine.subFlow(any<TransactionBackchainResolutionFlow>())).thenReturn(Unit)
        whenever(transaction.toLedgerTransaction()).thenReturn(ledgerTransaction)
    }

    @Test
    fun `successful verification in receive flow should persist transaction`() {
        whenever(sessionAlice.receive(UtxoSignedTransactionInternal::class.java)).thenReturn(transaction)
        whenever(transaction.getVisibleStateIndexes(visibilityChecker)).thenReturn(mutableListOf(0))

        callReceiveTransactionFlow(sessionAlice)

        verify(sessionAlice).receive(UtxoSignedTransactionInternal::class.java)
        verify(transaction).verifySignatorySignatures()
        verify(transaction).verifyAttachedNotarySignature()
        verify(transactionVerificationService).verify(ledgerTransaction)
        verify(sessionAlice).send(Payload.Success("Successfully received transaction."))
        verify(utxoLedgerPersistenceService).persist(transaction, VERIFIED, transaction.getVisibleStateIndexes(visibilityChecker))
    }

    @Test
    fun `receiving transaction with dependencies should call backchain resolution flow`() {
        whenever(sessionAlice.receive(UtxoSignedTransactionInternal::class.java)).thenReturn(transaction)
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
    fun `receiving invalid transaction should throw exception`() {
        whenever(sessionAlice.receive(UtxoSignedTransactionInternal::class.java)).thenReturn(transaction)
        whenever(transaction.verifySignatorySignatures()).thenThrow(
            CordaRuntimeException("Failed to verify"))

        assertThatThrownBy { callReceiveTransactionFlow(sessionAlice) }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessageContaining("Failed to verify transaction")

        verify(sessionAlice).receive(UtxoSignedTransactionInternal::class.java)
        verify(transaction).verifySignatorySignatures()
    }

    private fun callReceiveTransactionFlow(session: FlowSession) {
        val flow = spy(ReceiveTransactionFlow(session))

        flow.transactionVerificationService = transactionVerificationService
        flow.ledgerPersistenceService = utxoLedgerPersistenceService
        flow.flowEngine = flowEngine
        flow.visibilityChecker = visibilityChecker

        flow.call()
    }
}