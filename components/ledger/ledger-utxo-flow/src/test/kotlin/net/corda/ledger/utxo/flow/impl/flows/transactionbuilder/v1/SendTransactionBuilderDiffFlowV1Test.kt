package net.corda.ledger.utxo.flow.impl.flows.transactionbuilder.v1

import net.corda.ledger.utxo.flow.impl.flows.transactionbuilder.SendTransactionBuilderDiffFlowCommonTest
import net.corda.ledger.utxo.flow.impl.transaction.UtxoBaselinedTransactionBuilder

@Suppress("MaxLineLength")
class SendTransactionBuilderDiffFlowV1Test : SendTransactionBuilderDiffFlowCommonTest() {

    override fun callSendFlow() {
        val flow = SendTransactionBuilderDiffFlowV1(
            UtxoBaselinedTransactionBuilder(currentTransactionBuilder).diff(),
            session
        )

        flow.flowEngine = flowEngine
        flow.call()
    }

    override val payloadWrapper = null
}
