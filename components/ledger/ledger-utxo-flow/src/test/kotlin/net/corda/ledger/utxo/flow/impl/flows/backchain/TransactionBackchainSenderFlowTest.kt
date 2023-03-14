package net.corda.ledger.utxo.flow.impl.flows.backchain

import net.corda.crypto.core.SecureHashImpl
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class TransactionBackchainSenderFlowTest {

    private companion object {
        val TX_ID_0 = SecureHashImpl("SHA", byteArrayOf(1, 1, 1, 1))
        val TX_ID_1 = SecureHashImpl("SHA", byteArrayOf(2, 2, 2, 2))
        val TX_ID_2 = SecureHashImpl("SHA", byteArrayOf(3, 3, 3, 3))
        val TX_ID_3 = SecureHashImpl("SHA", byteArrayOf(4, 4, 4, 4))
    }

    private val session = mock<FlowSession>()
    private val utxoLedgerPersistenceService = mock<UtxoLedgerPersistenceService>()

    private val transactionBackchainIsRequestedFor = mock<UtxoSignedTransaction>()
    private val transaction1 = mock<UtxoSignedTransaction>()
    private val transaction2 = mock<UtxoSignedTransaction>()
    private val transaction3 = mock<UtxoSignedTransaction>()

    private val ledgerTransaction1 = mock<UtxoLedgerTransaction>()
    private val ledgerTransaction2 = mock<UtxoLedgerTransaction>()
    private val ledgerTransaction3 = mock<UtxoLedgerTransaction>()

    private val flow = TransactionBackchainSenderFlow(TX_ID_0, session)

    @BeforeEach
    fun beforeEach() {
        flow.utxoLedgerPersistenceService = utxoLedgerPersistenceService

        whenever(transactionBackchainIsRequestedFor.id).thenReturn(TX_ID_0)

        whenever(utxoLedgerPersistenceService.find(TX_ID_1)).thenReturn(transaction1)
        whenever(utxoLedgerPersistenceService.find(TX_ID_2)).thenReturn(transaction2)
        whenever(utxoLedgerPersistenceService.find(TX_ID_3)).thenReturn(transaction3)

        whenever(transaction1.toLedgerTransaction()).thenReturn(ledgerTransaction1)
        whenever(transaction2.toLedgerTransaction()).thenReturn(ledgerTransaction2)
        whenever(transaction3.toLedgerTransaction()).thenReturn(ledgerTransaction3)
    }

    @Test
    fun `does nothing when receiving an initial stop request`() {
        whenever(session.receive(TransactionBackchainRequest::class.java)).thenReturn(TransactionBackchainRequest.Stop)

        flow.call()

        verify(session).receive(TransactionBackchainRequest::class.java)
        verifyNoInteractions(utxoLedgerPersistenceService)
    }

    @Test
    fun `sends the requested transactions to the requesting session`() {
        whenever(session.receive(TransactionBackchainRequest::class.java))
            .thenReturn(TransactionBackchainRequest.Get(setOf(TX_ID_1, TX_ID_2, TX_ID_3)), TransactionBackchainRequest.Stop)

        whenever(ledgerTransaction1.inputStateRefs).thenReturn(emptyList())
        whenever(ledgerTransaction1.referenceStateRefs).thenReturn(emptyList())
        whenever(ledgerTransaction2.inputStateRefs).thenReturn(emptyList())
        whenever(ledgerTransaction2.referenceStateRefs).thenReturn(emptyList())
        whenever(ledgerTransaction3.inputStateRefs).thenReturn(emptyList())
        whenever(ledgerTransaction3.referenceStateRefs).thenReturn(emptyList())

        flow.call()

        verify(session).send(listOf(transaction1))
        verify(session).send(listOf(transaction2))
        verify(session).send(listOf(transaction3))
    }
}