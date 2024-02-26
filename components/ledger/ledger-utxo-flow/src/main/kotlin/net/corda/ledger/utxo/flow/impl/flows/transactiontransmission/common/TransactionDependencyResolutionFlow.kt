package net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.common

import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.utxo.data.transaction.verifyFilteredTransactionAndSignatures
import net.corda.ledger.utxo.flow.impl.flows.backchain.InvalidBackchainException
import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainResolutionFlow
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.NotarySignatureVerificationService
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransactionAndSignatures
import net.corda.v5.membership.GroupParametersLookup
import org.slf4j.LoggerFactory

class TransactionDependencyResolutionFlow(
    private val session: FlowSession,
    private val transactionId: SecureHash,
    private val notaryName: MemberX500Name,
    private val transactionDependencies: Set<SecureHash>,
    private val filteredDependencies: List<UtxoFilteredTransactionAndSignatures>?
) : SubFlow<Unit> {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java)
    }

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var groupParametersLookup: GroupParametersLookup

    @CordaInject
    lateinit var notarySignatureVerificationService: NotarySignatureVerificationService

    @CordaInject
    lateinit var ledgerPersistenceService: UtxoLedgerPersistenceService

    @Suspendable
    override fun call() {
        if (transactionDependencies.isNotEmpty()) {
            if (filteredDependencies.isNullOrEmpty()) {
                // If we have dependencies but no filtered dependencies then we need to perform backchain resolution
                try {
                    flowEngine.subFlow(TransactionBackchainResolutionFlow(transactionDependencies, session))
                } catch (e: InvalidBackchainException) {
                    val message = "Invalid transaction: $transactionId found during back-chain resolution."
                    log.warn(message, e)
                    session.send(Payload.Failure<List<DigitalSignatureAndMetadata>>(message))
                    throw e
                }
            } else {
                // If we have dependencies and filtered dependencies then we need to perform filtered transaction verification
                require(filteredDependencies.size == transactionDependencies.size) {
                    "The number of filtered transactions received didn't match the number of dependencies."
                }

                val groupParameters = groupParametersLookup.currentGroupParameters
                val notary =
                    requireNotNull(groupParameters.notaries.firstOrNull { it.name == notaryName }) {
                        "Notary from initial transaction \"$notaryName\" " +
                            "cannot be found in group parameter notaries."
                    }

                // Verify the received filtered transactions
                filteredDependencies.forEach {
                    it.verifyFilteredTransactionAndSignatures(notary, notarySignatureVerificationService)
                }

                // Persist the verified filtered transactions
                ledgerPersistenceService.persistFilteredTransactionsAndSignatures(filteredDependencies)
            }
        }
    }
}
