package net.corda.ledger.utxo.flow.impl.flows.transactiontransmission

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.common.testkit.getWireTransactionExample
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionInternal
import net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.common.TransactionDependencyResolutionFlow
import net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.common.UtxoTransactionPayload
import net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.v1.ReceiveWireTransactionFlowV1
import net.corda.ledger.utxo.flow.impl.transaction.factory.impl.UtxoLedgerTransactionFactoryImpl
import net.corda.ledger.utxo.test.UtxoLedgerTest
import net.corda.ledger.utxo.testkit.notaryX500Name
import net.corda.ledger.utxo.testkit.utxoTransactionMetadataExample
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.utxo.TimeWindow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.security.PublicKey

class ReceiveWireTransactionFlowV1Test : UtxoLedgerTest() {
    private val mockUtxoLedgerTransactionFactory = mock<UtxoLedgerTransactionFactoryImpl>()
    private val mockFlowEngine = mock<FlowEngine>()
    private val mockSerializationService = mock<SerializationService>()
    private val sessionAlice = mock<FlowSession>()
    private val wireTransaction = buildWireTransaction()
    private val ledgerTransaction = mock<UtxoLedgerTransactionInternal>()

    @BeforeEach
    fun beforeEach() {
        whenever(
            mockSerializationService.deserialize(
                "Notary".toByteArray(),
                MemberX500Name::class.java
            )
        ).thenReturn(notaryX500Name)

        whenever(
            mockSerializationService.deserialize(
                "NotaryKey".toByteArray(),
                PublicKey::class.java
            )
        ).thenReturn(mock())

        whenever(
            mockSerializationService.deserialize(
                "TimeWindow".toByteArray(),
                TimeWindow::class.java
            )
        ).thenReturn(mock())
    }

    @Test
    fun `flow should respond with success payload if subflow executes properly`() {
        whenever(mockUtxoLedgerTransactionFactory.create(wireTransaction)).thenReturn(ledgerTransaction)
        whenever(sessionAlice.receive(UtxoTransactionPayload::class.java)).thenReturn(
            UtxoTransactionPayload(wireTransaction)
        )

        // Subflow executes without errors
        whenever(mockFlowEngine.subFlow(any<TransactionDependencyResolutionFlow>())).thenAnswer { }

        callReceiveTransactionFlow(sessionAlice)

        verify(sessionAlice).send(Payload.Success(Unit))
    }

    @Test
    fun `sub-flow error is propagated and main flow fails too`() {
        whenever(mockUtxoLedgerTransactionFactory.create(wireTransaction)).thenReturn(ledgerTransaction)
        whenever(sessionAlice.receive(UtxoTransactionPayload::class.java)).thenReturn(
            UtxoTransactionPayload(wireTransaction)
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

    private fun buildWireTransaction(): WireTransaction {
        return getWireTransactionExample(
            digestService,
            merkleTreeProvider,
            jsonMarshallingService,
            jsonValidator,
            componentGroupLists = listOf(
                listOf("Notary".toByteArray(), "NotaryKey".toByteArray(), "TimeWindow".toByteArray()),
                listOf(),
                listOf(),
                listOf(),
                listOf(),
                listOf(),
                listOf(),
                listOf(),
                listOf()
            ),
            metadata = utxoTransactionMetadataExample()
        )
    }

    private fun callReceiveTransactionFlow(session: FlowSession) {
        val flow = ReceiveWireTransactionFlowV1(session)
        flow.utxoLedgerTransactionFactory = mockUtxoLedgerTransactionFactory
        flow.flowEngine = mockFlowEngine
        flow.serializationService = mockSerializationService
        flow.call()
    }
}
