package net.corda.ledger.utxo.flow.impl.flows.transactionbuilder.v1

import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainResolutionFlow
import net.corda.ledger.utxo.flow.impl.flows.transactionbuilder.ReceiveAndUpdateTransactionBuilderFlowCommonTest
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderInternal
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

@Suppress("MaxLineLength")
class ReceiveAndUpdateTransactionBuilderFlowV1Test : ReceiveAndUpdateTransactionBuilderFlowCommonTest() {

    @BeforeEach
    fun beforeEach() {
        whenever(mockFlowEngine.subFlow(any<TransactionBackchainResolutionFlow>())).thenReturn(Unit)
        originalTransactionalBuilder = utxoLedgerService.createTransactionBuilder()
    }

    override fun callSendFlow(): UtxoTransactionBuilderInternal {
        val flow = ReceiveAndUpdateTransactionBuilderFlowV1(
            session,
            originalTransactionalBuilder as UtxoTransactionBuilderInternal
        )

        flow.flowEngine = mockFlowEngine
        return flow.call() as UtxoTransactionBuilderInternal
    }

    override val payloadWrapper: Class<*>? = null

}
