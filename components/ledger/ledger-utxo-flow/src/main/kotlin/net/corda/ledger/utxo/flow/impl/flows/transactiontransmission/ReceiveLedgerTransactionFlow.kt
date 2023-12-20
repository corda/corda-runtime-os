package net.corda.ledger.utxo.flow.impl.flows.transactiontransmission

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.utxo.flow.impl.flows.backchain.InvalidBackchainException
import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainResolutionFlow
import net.corda.ledger.utxo.flow.impl.flows.backchain.dependencies
import net.corda.ledger.utxo.flow.impl.transaction.factory.UtxoLedgerTransactionFactory
import net.corda.sandbox.CordaSystemFlow
import net.corda.utilities.trace
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@CordaSystemFlow
class ReceiveLedgerTransactionFlow(
    private val session: FlowSession
) : SubFlow<UtxoLedgerTransaction> {

    private companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var utxoLedgerTransactionFactory: UtxoLedgerTransactionFactory

    @Suspendable
    override fun call(): UtxoLedgerTransaction {
        val wireTransaction = session.receive(WireTransaction::class.java)
        val ledgerTransaction = utxoLedgerTransactionFactory.create(wireTransaction)

        val transactionDependencies = ledgerTransaction.dependencies
        if (transactionDependencies.isNotEmpty()) {
            try {
                flowEngine.subFlow(TransactionBackchainResolutionFlow(transactionDependencies, session))
            } catch (e: InvalidBackchainException) {
                val message = "Invalid transaction: ${wireTransaction.id} found during back-chain resolution."
                log.warn(message, e)
                session.send(Payload.Failure<List<DigitalSignatureAndMetadata>>(message))
                throw e
            }
        } else {
            log.trace {
                "Transaction with id ${wireTransaction.id} has no dependencies so backchain resolution will not be performed."
            }
        }

        session.send(Payload.Success("Successfully received transaction."))

        return ledgerTransaction
    }
}