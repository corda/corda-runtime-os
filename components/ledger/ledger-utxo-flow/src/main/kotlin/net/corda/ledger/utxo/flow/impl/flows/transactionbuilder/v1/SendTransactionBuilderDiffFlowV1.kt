package net.corda.ledger.utxo.flow.impl.flows.transactionbuilder.v1

import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainSenderFlow
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderContainer
import net.corda.sandbox.CordaSystemFlow
import net.corda.utilities.trace
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.common.NotaryLookup
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@CordaSystemFlow
class SendTransactionBuilderDiffFlowV1(
    private val transactionBuilder: UtxoTransactionBuilderContainer,
    private val session: FlowSession
) : SubFlow<Unit> {

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var notaryLookup: NotaryLookup

    @CordaInject
    lateinit var persistenceService: UtxoLedgerPersistenceService

    private companion object {
        val log: Logger = LoggerFactory.getLogger(SendTransactionBuilderDiffFlowV1::class.java)
    }

    @Suspendable
    override fun call() {
        log.trace { "Starting send transaction builder flow." }

        val notaryName = requireNotNull(transactionBuilder.getNotaryName()) {
            "Notary name on transaction builder must not be null."
        }

        val notaryInfo = requireNotNull(notaryLookup.lookup(notaryName)) {
            "Could not find notary service with name: $notaryName"
        }

        val newTransactionIds = transactionBuilder.dependencies

        // If we couldn't find the notary we default to backchain resolution
        if (notaryInfo.isBackchainRequired) {
            log.trace { "Sending proposed transaction builder components to ${session.counterparty}." }
            session.send(transactionBuilder)

            if (newTransactionIds.isEmpty()) {
                log.trace { "There are no new states transferred, therefore no backchains need to be resolved." }
            } else {
                flowEngine.subFlow(TransactionBackchainSenderFlow(newTransactionIds, session))
            }
        } else {
            val filteredTransactionsAndSignatures = persistenceService.findFilteredTransactionsAndSignatures(
                transactionBuilder.inputStateRefs + transactionBuilder.referenceStateRefs,
                notaryInfo.publicKey,
                notaryInfo.name
            ).values.toList()

            require(newTransactionIds.size == filteredTransactionsAndSignatures.size) {
                "The number of filtered transactions didn't match the number of dependencies."
            }

            log.trace { "Sending proposed transaction builder components with filtered dependencies to ${session.counterparty}." }
            session.send(transactionBuilder.copy(filteredDependencies = filteredTransactionsAndSignatures))
        }
    }
}
