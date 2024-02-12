package net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.v1

import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainSenderFlow
import net.corda.ledger.utxo.flow.impl.flows.backchain.dependencies
import net.corda.ledger.utxo.flow.impl.flows.common.TransactionAndFilteredDependencyPayload
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
    lateinit var persistenceService: UtxoLedgerPersistenceService

    @Suspendable
    override fun call() {
        val newTransactionIds = transaction.dependencies

        val notary = requireNotNull(notaryLookup.lookup(transaction.notaryName)) {
            "Couldn't find notary: ${transaction.notaryName} on the network."
        }

        if (notary.isBackchainRequired) {
            flowMessaging.sendAll(TransactionAndFilteredDependencyPayload(transaction), sessions.toSet())
            sessions.forEach {
                flowEngine.subFlow(TransactionBackchainSenderFlow(transaction.id, it))

                val sendingTransactionResult = it.receive(Payload::class.java)
                if (sendingTransactionResult is Payload.Failure) {
                    throw CordaRuntimeException(
                        sendingTransactionResult.message
                    )
                }
            }
        } else {
            val filteredTransactionsAndSignatures = persistenceService.findFilteredTransactionsAndSignatures(
                transaction.inputStateRefs + transaction.referenceStateRefs,
                notary.publicKey,
                notary.name
            )

            require(newTransactionIds.size == filteredTransactionsAndSignatures.size) {
                "The number of filtered transactions didn't match the number of dependencies."
            }

            flowMessaging.sendAll(
                TransactionAndFilteredDependencyPayload(
                    transaction,
                    filteredTransactionsAndSignatures.values.toList()
                ),
                sessions.toSet()
            )

            sessions.forEach {
                it.send(filteredTransactionsAndSignatures.values)

                val sendingTransactionResult = it.receive(Payload::class.java)
                if (sendingTransactionResult is Payload.Failure) {
                    throw CordaRuntimeException(
                        sendingTransactionResult.message
                    )
                }
            }

        }
    }
}