package net.corda.ledger.utxo.flow.impl.flows.common

import net.corda.crypto.core.SecureHashImpl
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainSenderFlow
import net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.common.SendTransactionFlow
import net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.common.UtxoTransactionPayload
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.ledger.utxo.testkit.notaryX500Name
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransactionAndSignatures
import net.corda.v5.membership.NotaryInfo
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
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

    private val notaryInfo = mock<NotaryInfo>()
    private val persistenceService = mock<UtxoLedgerPersistenceService>()
    private val notaryLookup = mock<NotaryLookup>()

    @BeforeEach
    fun beforeEach() {
        whenever(transaction.id).thenReturn(TX_ID)
        whenever(flowEngine.subFlow(any<TransactionBackchainSenderFlow>())).thenReturn(Unit)

        val mockWireTx = mock<WireTransaction>()
        whenever(mockWireTx.id).thenReturn(TX_ID)
        whenever(transaction.wireTransaction).thenReturn(mockWireTx)

        // Backchain on by default
        whenever(transaction.notaryName).thenReturn(notaryX500Name)
        whenever(notaryInfo.isBackchainRequired).thenReturn(true)
        whenever(notaryInfo.name).thenReturn(notaryX500Name)
        whenever(notaryInfo.publicKey).thenReturn(mock())
        whenever(notaryLookup.lookup(notaryX500Name)).thenReturn(notaryInfo)
    }

    @Test
    fun `notary backchain on - does nothing when receiving payload successfully`() {
        whenever(transaction.inputStateRefs).thenReturn(listOf(mock()))

        whenever(sessionAlice.receive(Payload::class.java)).thenReturn(
            Payload.Success(successMessage)
        )
        whenever(sessionBob.receive(Payload::class.java)).thenReturn(
            Payload.Success(successMessage)
        )

        callSendTransactionFlow(transaction, sessions)

        verify(flowMessaging).sendAll(
            UtxoTransactionPayload(transaction.wireTransaction),
            sessions.toSet()
        )
        verify(sessionAlice).receive(Payload::class.java)
    }

    @Test
    fun `notary backchain on - sending transaction with dependencies should call backchain flow`() {
        whenever(transaction.inputStateRefs).thenReturn(listOf(mock()))

        whenever(sessionAlice.receive(Payload::class.java)).thenReturn(
            Payload.Success(successMessage)
        )
        whenever(sessionBob.receive(Payload::class.java)).thenReturn(
            Payload.Success(successMessage)
        )

        callSendTransactionFlow(transaction, sessions)

        verify(flowEngine).subFlow(TransactionBackchainSenderFlow(TX_ID, sessionAlice))
        verify(flowEngine).subFlow(TransactionBackchainSenderFlow(TX_ID, sessionBob))
    }

    @Test
    fun `notary backchain on - sending transaction with no dependencies should not call backchain flow`() {
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
    fun `notary backchain on - exceptions get propagated back from receiver`() {
        whenever(transaction.inputStateRefs).thenReturn(listOf(mock()))

        whenever(sessionAlice.receive(Payload::class.java)).thenReturn(
            Payload.Failure<List<DigitalSignatureAndMetadata>>("fail")
        )

        Assertions.assertThatThrownBy { callSendTransactionFlow(transaction, sessions) }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessageContaining("fail")
    }

    @Test
    fun `notary backchain off - should fetch filtered transactions and send them`() {
        val filteredTransactionAndSignatures = mock<UtxoFilteredTransactionAndSignatures>()
        val mockTxId = mock<SecureHash>()
        val dependency = StateRef(mock(), 0)

        whenever(transaction.inputStateRefs).thenReturn(listOf(dependency))
        whenever(notaryInfo.isBackchainRequired).thenReturn(false)

        whenever(
            persistenceService.findFilteredTransactionsAndSignatures(
                listOf(dependency),
                notaryInfo.publicKey,
                notaryInfo.name
            )
        ).thenReturn(mapOf(mockTxId to filteredTransactionAndSignatures))

        whenever(sessionAlice.receive(Payload::class.java)).thenReturn(
            Payload.Success(successMessage)
        )
        whenever(sessionBob.receive(Payload::class.java)).thenReturn(
            Payload.Success(successMessage)
        )

        callSendTransactionFlow(transaction, sessions)

        verify(flowEngine, never()).subFlow(TransactionBackchainSenderFlow(TX_ID, sessionAlice))
        verify(flowMessaging).sendAll(
            UtxoTransactionPayload(
                transaction.wireTransaction,
                listOf(filteredTransactionAndSignatures)
            ),
            sessions.toSet()
        )
    }

    @Test
    fun `notary backchain off - should still execute backchain resolution if forced`() {
        val dependency = StateRef(mock(), 0)

        whenever(transaction.inputStateRefs).thenReturn(listOf(dependency))
        whenever(notaryInfo.isBackchainRequired).thenReturn(false)

        whenever(sessionAlice.receive(Payload::class.java)).thenReturn(
            Payload.Success(successMessage)
        )
        whenever(sessionBob.receive(Payload::class.java)).thenReturn(
            Payload.Success(successMessage)
        )

        callSendTransactionFlow(
            transaction,
            sessions,
            forceBackchainResolution = true
        )

        verify(flowEngine).subFlow(TransactionBackchainSenderFlow(TX_ID, sessionAlice))
        verify(flowMessaging).sendAll(
            UtxoTransactionPayload(
                transaction.wireTransaction
            ),
            sessions.toSet()
        )
        verify(flowMessaging).sendAll(
            UtxoTransactionPayload(
                transaction.wireTransaction
            ),
            sessions.toSet()
        )
        verify(persistenceService, never()).findFilteredTransactionsAndSignatures(any(), any(), any())
    }

    private fun callSendTransactionFlow(
        signedTransaction: UtxoSignedTransaction,
        sessions: List<FlowSession>,
        forceBackchainResolution: Boolean = false
    ) {
        val flow = SendTransactionFlow(
            (signedTransaction as UtxoSignedTransactionInternal).wireTransaction,
            signedTransaction.id,
            signedTransaction.notaryName,
            signedTransaction.referenceStateRefs + signedTransaction.inputStateRefs,
            sessions,
            forceBackchainResolution
        )

        flow.flowEngine = flowEngine
        flow.flowMessaging = flowMessaging
        flow.notaryLookup = notaryLookup
        flow.ledgerPersistenceService = persistenceService
        flow.call()
    }
}
