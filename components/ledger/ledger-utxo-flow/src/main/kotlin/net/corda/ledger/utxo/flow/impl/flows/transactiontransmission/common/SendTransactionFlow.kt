package net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.common

import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainSenderFlow
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
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

@Suppress("LongParameterList")
class SendTransactionFlow<T>(
    private val transaction: T,
    private val transactionId: SecureHash,
    private val notaryName: MemberX500Name,
    private val transactionDependencies: List<StateRef>,
    private val sessions: List<FlowSession>,
    private val forceBackchainResolution: Boolean
) : SubFlow<Unit> {

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
        val dependentTransactionIds = transactionDependencies.map { it.transactionId }.toSet()

        val notaryInfo = requireNotNull(notaryLookup.lookup(notaryName)) {
            "Could not find notary with name: $notaryName"
        }

        val isBackchainResolutionRequired = forceBackchainResolution || notaryInfo.isBackchainRequired

        if (isBackchainResolutionRequired) {
            flowMessaging.sendAll(
                UtxoTransactionPayload(transaction),
                sessions.toSet()
            )
        } else {
            val filteredTransactionsAndSignatures = ledgerPersistenceService.findFilteredTransactionsAndSignatures(
                transactionDependencies,
                notaryInfo.publicKey,
                notaryInfo.name
            )
            flowMessaging.sendAll(
                UtxoTransactionPayload(
                    transaction,
                    filteredTransactionsAndSignatures.values.toList()
                ),
                sessions.toSet()
            )
        }

        sessions.forEach {
            if (isBackchainResolutionRequired && dependentTransactionIds.isNotEmpty()) {
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
}
