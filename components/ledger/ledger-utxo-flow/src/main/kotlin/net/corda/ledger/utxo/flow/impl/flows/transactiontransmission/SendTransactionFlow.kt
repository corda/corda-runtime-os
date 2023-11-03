package net.corda.ledger.utxo.flow.impl.flows.transactiontransmission

import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.sandbox.CordaSystemFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction

@CordaSystemFlow
class SendTransactionFlow(
    private val transaction: UtxoSignedTransaction,
    private val sessions: List<FlowSession>
) : SubFlow<Unit> {

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var utxoLedgerPersistenceService: UtxoLedgerPersistenceService

    @Suspendable
    override fun call() {
        requireNotNull(utxoLedgerPersistenceService.findSignedTransaction(transaction.id)) {
            "Transaction to send = ${transaction.id} is not verified"
        }

        flowMessaging.sendAll(transaction, sessions.toSet())
    }
}