package net.corda.ledger.utxo.flow.impl.flows.backchain

import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.sandbox.CordaSystemFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.trace
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@CordaSystemFlow
class TransactionBackchainSenderFlow(private val transaction: UtxoSignedTransaction, private val session: FlowSession) : SubFlow<Unit> {

    private companion object {
        private val log: Logger = LoggerFactory.getLogger(TransactionBackchainSenderFlow::class.java)
    }

    @CordaInject
    lateinit var utxoLedgerPersistenceService: UtxoLedgerPersistenceService

    @Suspendable
    override fun call() {
        log.trace {
            "Backchain resolution of ${transaction.id} - Waiting to be told what transactions to send to ${session.counterparty} " +
                    "so that the backchain can be resolved"
        }
        while (true) {
            when (val request = session.receive(TransactionBackchainRequest::class.java)) {
                is TransactionBackchainRequest.Get -> {
                    val transactions = request.transactionIds.map { id ->
                        utxoLedgerPersistenceService.find(id)
                            ?: throw CordaRuntimeException("Requested transaction does not exist locally")
                    }
                    // sending in batches of 1
                    // TODO Switch to [FlowMessaging.sendAll]
                    transactions.map { session.send(listOf(it)) }
                    log.trace {
                        "Backchain resolution of ${transaction.id} - Sent backchain transactions ${transactions.map { it.id }} to " +
                                session.counterparty
                    }
                }

                is TransactionBackchainRequest.Stop -> {
                    log.trace {
                        "Backchain resolution of ${transaction.id} - Received stop, finishing sending of backchain transaction to " +
                                session.counterparty
                    }
                    return
                }
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TransactionBackchainSenderFlow

        if (session != other.session) return false

        return true
    }

    override fun hashCode(): Int {
        return session.hashCode()
    }
}