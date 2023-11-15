package net.corda.ledger.utxo.flow.impl.flows.transactiontransmission

import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainSenderFlow
import net.corda.ledger.utxo.flow.impl.flows.backchain.dependencies
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
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
class SendTransactionFlow(
    private val transaction: UtxoSignedTransaction,
    private val sessions: List<FlowSession>
) : SubFlow<Unit> {

    private companion object { val log = LoggerFactory.getLogger(this::class.java) }

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var utxoLedgerPersistenceService: UtxoLedgerPersistenceService

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
            when (sendingTransactionResult) {
                is Payload.Success -> return
                is Payload.Failure ->
                    throw CordaRuntimeException(
                        "Failed to send transaction: ${transaction.id} due to unverified transaction sent."
                    )
            }
        }
    }
}