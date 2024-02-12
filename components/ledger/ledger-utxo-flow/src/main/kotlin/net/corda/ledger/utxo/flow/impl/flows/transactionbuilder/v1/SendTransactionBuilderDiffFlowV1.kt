package net.corda.ledger.utxo.flow.impl.flows.transactionbuilder.v1

import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainSenderFlow
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerStateQueryService
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

    @CordaInject
    lateinit var ledgerStateQueryService: UtxoLedgerStateQueryService

    private companion object {
        val log: Logger = LoggerFactory.getLogger(SendTransactionBuilderDiffFlowV1::class.java)
    }

    @Suspendable
    override fun call() {
        log.trace { "Starting send transaction builder flow." }

        val newTransactionIds = transactionBuilder.dependencies

        // If we have no dependencies then we just send the transaction builder
        if (newTransactionIds.isEmpty()) {
            log.trace {
                "There are no new states transferred, therefore no backchain resolution or filtered dependencies required."
            }
            session.send(TransactionBuilderPayload(transactionBuilder))
            return
        }

        val notaryName = transactionBuilder.getNotaryName() ?: run {
            // Resolve the dependent state and refs
            val dependentStateAndRefs = ledgerStateQueryService.resolveStateRefs(
                transactionBuilder.inputStateRefs + transactionBuilder.referenceStateRefs
            )

            // Make sure they all have the same notary
            require(dependentStateAndRefs.map { it.state.notaryName }.toSet().size == 1) {
                "Every dependency needs to have the same notary."
            }

            // Get the notary from the state ref
            dependentStateAndRefs.first().state.notaryName
        }

        // Lookup the notary by name
        val notaryInfo = requireNotNull(notaryLookup.lookup(notaryName)) {
            "Could not find notary service with name: $notaryName"
        }

        if (notaryInfo.isBackchainRequired) {
            log.trace { "Sending proposed transaction builder components to ${session.counterparty}." }

            // If we need backchain resolution we send the transaction builder and start the backchain sender flow
            session.send(TransactionBuilderPayload(transactionBuilder))
            flowEngine.subFlow(TransactionBackchainSenderFlow(newTransactionIds, session))
        } else {
            // If we don't need backchain resolution we collect the filtered transactions for the given stateRefs
            // and add them to the payload
            val filteredTransactionsAndSignatures = persistenceService.findFilteredTransactionsAndSignatures(
                transactionBuilder.inputStateRefs + transactionBuilder.referenceStateRefs,
                notaryInfo.publicKey,
                notaryInfo.name
            ).values.toList()

            require(newTransactionIds.size == filteredTransactionsAndSignatures.size) {
                "The number of filtered transactions didn't match the number of dependencies."
            }

            log.trace { "Sending proposed transaction builder components with filtered dependencies to ${session.counterparty}." }
            session.send(TransactionBuilderPayload(transactionBuilder, filteredTransactionsAndSignatures))
        }
    }
}
