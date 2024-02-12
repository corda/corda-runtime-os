package net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.v1

import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainSenderFlow
import net.corda.ledger.utxo.flow.impl.flows.backchain.dependencies
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.sandbox.CordaSystemFlow
import net.corda.utilities.trace
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import org.slf4j.LoggerFactory

@CordaSystemFlow
class SendTransactionFlowV1(
    private val transaction: UtxoSignedTransaction,
    private val sessions: List<FlowSession>
) : SubFlow<Unit> {

    private companion object {
        val log = LoggerFactory.getLogger(SendTransactionFlowV1::class.java)
    }

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call() {
        flowMessaging.sendAll(transaction as UtxoSignedTransactionInternal, sessions.toSet())

        sessions.forEach {
            if (transaction.dependencies.isNotEmpty()) {
                flowEngine.subFlow(TransactionBackchainSenderFlow(transaction.id, it))
            } else {
                log.trace {
                    "Transaction with id ${transaction.id} has no dependencies so backchain resolution will not be performed."
                }
            }

            val sendingTransactionResult = it.receive(Payload::class.java)
            if (sendingTransactionResult is Payload.Failure) {
                throw CordaRuntimeException(
                    sendingTransactionResult.message
                )
            }
        }
    }
}