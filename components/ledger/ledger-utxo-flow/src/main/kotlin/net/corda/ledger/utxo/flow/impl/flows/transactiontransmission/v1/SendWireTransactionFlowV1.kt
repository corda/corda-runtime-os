package net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.v1

import net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.common.SendTransactionFlow
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.sandbox.CordaSystemFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction

@CordaSystemFlow
class SendWireTransactionFlowV1(
    private val signedTransaction: UtxoSignedTransaction,
    private val sessions: List<FlowSession>
) : SubFlow<Unit> {

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call() {
        flowEngine.subFlow(
            SendTransactionFlow(
                (signedTransaction as UtxoSignedTransactionInternal).wireTransaction,
                signedTransaction.id,
                signedTransaction.notaryName,
                signedTransaction.inputStateRefs + signedTransaction.referenceStateRefs,
                sessions,
                forceBackchainResolution = false
            )
        )
    }
}
