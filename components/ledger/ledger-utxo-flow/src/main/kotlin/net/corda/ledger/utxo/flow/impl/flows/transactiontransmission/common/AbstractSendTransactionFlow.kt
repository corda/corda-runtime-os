package net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.common

import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainSenderFlow
import net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.v1.SendTransactionFlowV1
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.utilities.trace
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.StateRef
import org.slf4j.LoggerFactory

abstract class AbstractSendTransactionFlow<T>(
    private val transaction: T,
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
        val dependencies = getTransactionDependencies(transaction)
        val dependentTransactionIds = dependencies.map { it.transactionId }.toSet()
        val transactionId = getTransactionId(transaction)
        val notaryName = getNotaryName(transaction)

        if (dependentTransactionIds.isEmpty()) {
            log.trace {
                "No dependencies found for $transactionId, no need for backchain resolution or filtered transactions."
            }
            flowMessaging.sendAll(
                UtxoTransactionPayload(transaction),
                sessions.toSet()
            )
            return
        }

        val notaryInfo = requireNotNull(notaryLookup.lookup(notaryName)) {
            "Could not find notary with name: $notaryName"
        }

        if (notaryInfo.isBackchainRequired) {
            flowMessaging.sendAll(
                UtxoTransactionPayload(transaction),
                sessions.toSet()
            )
        } else {
            val filteredTransactionsAndSignatures = ledgerPersistenceService.findFilteredTransactionsAndSignatures(
                dependencies,
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
                flowEngine.subFlow(TransactionBackchainSenderFlow(transactionId, it))
            }

            val sendingTransactionResult = it.receive(Payload::class.java)
            if (sendingTransactionResult is Payload.Failure) {
                throw CordaRuntimeException(
                    sendingTransactionResult.message
                )
            }
        }
    }

    abstract fun getTransactionDependencies(transaction: T): List<StateRef>

    abstract fun getTransactionId(transaction: T): SecureHash

    abstract fun getNotaryName(transaction: T): MemberX500Name
}