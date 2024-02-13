package net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.v1

import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainSenderFlow
import net.corda.ledger.utxo.flow.impl.flows.backchain.dependencies
import net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.common.UtxoTransactionPayload
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
import net.corda.v5.ledger.common.NotaryLookup
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

    @CordaInject
    lateinit var notaryLookup: NotaryLookup

    @CordaInject
    lateinit var ledgerPersistenceService: UtxoLedgerPersistenceService

    @Suspendable
    override fun call() {
        if (transaction.dependencies.isEmpty()) {
            log.trace {
                "No dependencies found for ${transaction.id}, no need for backchain resolution or filtered transactions."
            }
            flowMessaging.sendAll(
                UtxoTransactionPayload(transaction as UtxoSignedTransactionInternal),
                sessions.toSet()
            )
            return
        }

        val notaryInfo = requireNotNull(notaryLookup.lookup(transaction.notaryName)) {
            "Could not find notary with name: ${transaction.notaryName}"
        }

        if (notaryInfo.isBackchainRequired) {
            flowMessaging.sendAll(
                UtxoTransactionPayload(transaction as UtxoSignedTransactionInternal),
                sessions.toSet()
            )
        } else {
            val filteredTransactionsAndSignatures = ledgerPersistenceService.findFilteredTransactionsAndSignatures(
                transaction.inputStateRefs + transaction.referenceStateRefs,
                notaryInfo.publicKey,
                notaryInfo.name
            )
            flowMessaging.sendAll(
                UtxoTransactionPayload(
                    transaction as UtxoSignedTransactionInternal,
                    filteredTransactionsAndSignatures.values.toList()
                ),
                sessions.toSet()
            )
        }

        sessions.forEach {
            if (notaryInfo.isBackchainRequired) {
                flowEngine.subFlow(TransactionBackchainSenderFlow(transaction.id, it))
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