package net.corda.ledger.utxo.flow.impl.flows.transactiontransmission

import net.corda.crypto.core.SecureHashImpl
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.common.testkit.getWireTransactionExample
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionInternal
import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainResolutionFlow
import net.corda.ledger.utxo.flow.impl.transaction.factory.impl.UtxoLedgerTransactionFactoryImpl
import net.corda.ledger.utxo.test.UtxoLedgerTest
import net.corda.ledger.utxo.testkit.utxoTransactionMetadataExample
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.ledger.utxo.StateRef
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ReceiveLedgerTransactionFlowTest : UtxoLedgerTest() {
    private companion object {
        val TX_ID_1 = SecureHashImpl("SHA", byteArrayOf(2, 2, 2, 2))
        val TX_ID_2 = SecureHashImpl("SHA", byteArrayOf(3, 3, 3, 3))

        val TX_INPUT_DEPENDENCY_STATE_REF_1 = StateRef(TX_ID_1, 0)
        val TX_INPUT_DEPENDENCY_STATE_REF_2 = StateRef(TX_ID_2, 0)

        val TX_INPUT_REFERENCE_DEPENDENCY_STATE_REF_1 = StateRef(TX_ID_2, 0)
    }

    private val mockUtxoLedgerTransactionFactory = mock<UtxoLedgerTransactionFactoryImpl>()
    private val mockFlowEngine = mock<FlowEngine>()
    private val mockSerializationService = mock<SerializationService>()
    private val sessionAlice = mock<FlowSession>()
    private val ledgerTransaction = mock<UtxoLedgerTransactionInternal>()

    @Test
    fun `receiving transaction with dependencies should call backchain resolution flow`() {
        val wireTransaction = getWireTransactionExample(
            digestService,
            merkleTreeProvider,
            jsonMarshallingService,
            jsonValidator,
            metadata = utxoTransactionMetadataExample(),
            componentGroupLists = listOf(
                listOf(),
                listOf(),
                listOf(),
                listOf(),
                listOf(),
                listOf("Serialized StateRef1".toByteArray(), "Serialized StateRef2".toByteArray()),
                listOf("Serialized StateRef3".toByteArray()),
                listOf(),
                listOf()
            )
        )
        whenever(
            mockSerializationService.deserialize(
                "Serialized StateRef1".toByteArray(),
                StateRef::class.java
            )
        ).thenReturn(TX_INPUT_DEPENDENCY_STATE_REF_1)
        whenever(
            mockSerializationService.deserialize(
                "Serialized StateRef2".toByteArray(),
                StateRef::class.java
            )
        ).thenReturn(TX_INPUT_DEPENDENCY_STATE_REF_2)
        whenever(
            mockSerializationService.deserialize(
                "Serialized StateRef3".toByteArray(),
                StateRef::class.java
            )
        ).thenReturn(TX_INPUT_REFERENCE_DEPENDENCY_STATE_REF_1)

        whenever(mockUtxoLedgerTransactionFactory.create(wireTransaction)).thenReturn(ledgerTransaction)
        whenever(sessionAlice.receive(WireTransaction::class.java)).thenReturn(wireTransaction)

        callReceiveTransactionFlow(sessionAlice)

        verify(mockFlowEngine).subFlow(TransactionBackchainResolutionFlow(setOf(TX_ID_1, TX_ID_2), sessionAlice))
        verify(sessionAlice).send(Payload.Success("Successfully received transaction."))
    }

    @Test
    fun `receiving transaction with no dependencies shouldn't call backchain resolution flow`() {
        val wireTransaction = getWireTransactionExample(
            digestService,
            merkleTreeProvider,
            jsonMarshallingService,
            jsonValidator,
            metadata = utxoTransactionMetadataExample()
        )
        whenever(mockUtxoLedgerTransactionFactory.create(wireTransaction)).thenReturn(ledgerTransaction)
        whenever(sessionAlice.receive(WireTransaction::class.java)).thenReturn(wireTransaction)

        callReceiveTransactionFlow(sessionAlice)

        verify(sessionAlice).send(Payload.Success("Successfully received transaction."))
        verify(mockFlowEngine, never()).subFlow(
            TransactionBackchainResolutionFlow(
                setOf(),
                sessionAlice
            )
        )
    }

    private fun callReceiveTransactionFlow(session: FlowSession) {
        val flow = spy(ReceiveLedgerTransactionFlow(session))
        flow.utxoLedgerTransactionFactory = mockUtxoLedgerTransactionFactory
        flow.flowEngine = mockFlowEngine
        flow.serializationService = mockSerializationService
        flow.call()
    }
}
