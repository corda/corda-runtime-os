package net.corda.ledger.utxo.flow.impl.flows.backchain.v1

import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.sandbox.CordaSystemFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.utilities.trace
import net.corda.v5.crypto.SecureHash
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@CordaSystemFlow
class TransactionBackchainSenderFlowV1(private val headTransactionIds: Set<SecureHash>, private val session: FlowSession) : SubFlow<Unit> {

    constructor (headTransactionId: SecureHash, session: FlowSession) : this(setOf(headTransactionId), session)

    private companion object {
        val log: Logger = LoggerFactory.getLogger(TransactionBackchainSenderFlowV1::class.java)
    }

    @CordaInject
    lateinit var utxoLedgerPersistenceService: UtxoLedgerPersistenceService

    @Suspendable
    override fun call() {
        log.trace {
            "Backchain resolution of $headTransactionIds - Waiting to be told what transactions to send to ${session.counterparty} " +
                    "so that the backchain can be resolved"
        }
        while (true) {
            when (val request = session.receive(TransactionBackchainRequestV1::class.java)) {
                is TransactionBackchainRequestV1.Get -> {
                    val transactions = request.transactionIds.map { id ->
                        utxoLedgerPersistenceService.find(id)
                            ?: throw CordaRuntimeException("Requested transaction does not exist locally")
                    }
                    // sending in batches of 1
                    // TODO Switch to [FlowMessaging.sendAll]
                    transactions.map { session.send(listOf(it)) }
                    log.trace {
                        "Backchain resolution of $headTransactionIds - Sent backchain transactions ${transactions.map { it.id }} to " +
                                session.counterparty
                    }
                }

                is TransactionBackchainRequestV1.Stop -> {
                    log.trace {
                        "Backchain resolution of $headTransactionIds - Received stop, finishing sending of backchain transaction to " +
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

        other as TransactionBackchainSenderFlowV1

        if (session != other.session) return false
        if (headTransactionIds != other.headTransactionIds) return false

        return true
    }

    override fun hashCode(): Int {
        return session.hashCode()
    }
}
