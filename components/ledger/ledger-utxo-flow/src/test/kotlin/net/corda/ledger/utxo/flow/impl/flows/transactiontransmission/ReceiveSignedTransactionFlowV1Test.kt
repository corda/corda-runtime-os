package net.corda.ledger.utxo.flow.impl.flows.transactiontransmission

import net.corda.crypto.core.SecureHashImpl
import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.common.TransactionDependencyResolutionFlow
import net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.common.UtxoTransactionPayload
import net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.v1.ReceiveSignedTransactionFlowV1
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.ledger.utxo.flow.impl.transaction.verifier.UtxoLedgerTransactionVerificationService
import net.corda.ledger.utxo.test.UtxoLedgerTest
import net.corda.ledger.utxo.testkit.notaryX500Name
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransactionAndSignatures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ReceiveSignedTransactionFlowV1Test : UtxoLedgerTest() {
    private val mockFlowEngine = mock<FlowEngine>()
    private val sessionAlice = mock<FlowSession>()
    private val signedTransaction = mock<UtxoSignedTransactionInternal>()
    private val filteredDependency = mock<UtxoFilteredTransactionAndSignatures>()
    private val ledgerTransaction = mock<UtxoLedgerTransaction>()

    private val transactionVerificationService = mock<UtxoLedgerTransactionVerificationService>()
    private val persistenceService = mock<UtxoLedgerPersistenceService>()

    @BeforeEach
    fun beforeEach() {
        whenever(signedTransaction.id).thenReturn(SecureHashImpl("SHA", byteArrayOf(1, 1, 1)))
        whenever(signedTransaction.notaryName)
            .thenReturn(notaryX500Name)
        whenever(signedTransaction.verifySignatorySignatures())
            .thenAnswer { }
        whenever(signedTransaction.verifyAttachedNotarySignature())
            .thenAnswer { }
        whenever(signedTransaction.toLedgerTransaction())
            .thenReturn(ledgerTransaction)
        whenever(signedTransaction.outputStateAndRefs)
            .thenReturn(emptyList())
    }

    @Test
    fun `flow should respond with success payload if sub-flow executes properly`() {
        whenever(transactionVerificationService.verify(any())).doAnswer { }
        whenever(persistenceService.persist(any(), any(), any())).doReturn(emptyList())
        whenever(sessionAlice.receive(UtxoTransactionPayload::class.java)).thenReturn(
            UtxoTransactionPayload(
                signedTransaction,
                filteredDependencies = listOf(filteredDependency)
            )
        )

        // Subflow executes without errors
        whenever(mockFlowEngine.subFlow(any<TransactionDependencyResolutionFlow>())).thenAnswer { }

        callReceiveTransactionFlow(sessionAlice)

        verify(sessionAlice).send(Payload.Success(Unit))
    }

    @Test
    fun `sub-flow error is propagated and main flow fails too`() {
        whenever(sessionAlice.receive(UtxoTransactionPayload::class.java)).thenReturn(
            UtxoTransactionPayload(signedTransaction)
        )

        // Subflow fails
        whenever(mockFlowEngine.subFlow(any<TransactionDependencyResolutionFlow>()))
            .thenAnswer {
                throw IllegalArgumentException("Flow Error!!")
            }

        val ex = assertThrows<IllegalArgumentException> {
            callReceiveTransactionFlow(sessionAlice)
        }

        assertThat(ex).hasStackTraceContaining("Flow Error!!")
    }

    private fun callReceiveTransactionFlow(session: FlowSession) {
        val flow = ReceiveSignedTransactionFlowV1(session)
        flow.flowEngine = mockFlowEngine
        flow.transactionVerificationService = transactionVerificationService
        flow.ledgerPersistenceService = persistenceService
        flow.visibilityChecker = mock()
        flow.call()
    }
}
