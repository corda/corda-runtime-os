package net.corda.ledger.utxo.flow.impl.flows.transactiontransmission

import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.utxo.flow.impl.flows.finality.getVisibleStateIndexes
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.ledger.utxo.flow.impl.transaction.verifier.TransactionVerificationException
import net.corda.ledger.utxo.flow.impl.transaction.verifier.UtxoLedgerTransactionVerificationService
import net.corda.v5.ledger.utxo.VisibilityChecker
import net.corda.sandbox.CordaSystemFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@CordaSystemFlow
class ReceiveTransactionFlow(
    private val session: FlowSession
) : SubFlow<UtxoSignedTransaction> {

    private companion object {
        private val log: Logger = LoggerFactory.getLogger(ReceiveTransactionFlow::class.java)
    }

    @CordaInject
    lateinit var transactionVerificationService: UtxoLedgerTransactionVerificationService

    @CordaInject
    lateinit var visibilityChecker: VisibilityChecker

    @CordaInject
    lateinit var persistenceService: UtxoLedgerPersistenceService

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call(): UtxoSignedTransaction {
        val transaction = session.receive(UtxoSignedTransaction::class.java)
        verifyTransaction(transaction)
        persistTransaction(transaction)

        return transaction
    }

    @Suspendable
    private fun verifyTransaction(signedTransaction: UtxoSignedTransaction) {
        try {
            transactionVerificationService.verify(signedTransaction.toLedgerTransaction())
        } catch(e: TransactionVerificationException) { throw e }
    }

    @Suspendable
    private fun persistTransaction(transaction: UtxoSignedTransaction) {
        val visibleStatesIndexes = (transaction as UtxoSignedTransactionInternal).getVisibleStateIndexes(visibilityChecker)
        log.info("xxx visibleStatesIndexes = $visibleStatesIndexes")
        persistenceService.persist(transaction, TransactionStatus.VERIFIED, visibleStatesIndexes)
        if (log.isDebugEnabled) {
            log.debug("Recorded transaction = ${transaction.id} since transaction is verified")
        }
    }
}