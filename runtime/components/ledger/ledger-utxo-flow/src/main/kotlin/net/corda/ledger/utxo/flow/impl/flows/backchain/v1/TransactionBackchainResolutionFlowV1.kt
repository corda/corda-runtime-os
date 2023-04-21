package net.corda.ledger.utxo.flow.impl.flows.backchain.v1

import net.corda.ledger.common.data.transaction.TransactionStatus.VERIFIED
import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainVerifier
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.sandbox.CordaSystemFlow
import net.corda.utilities.debug
import net.corda.utilities.trace
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@CordaSystemFlow
class TransactionBackchainResolutionFlowV1(
    private val initialTransactionIds: Set<SecureHash>,
    private val session: FlowSession,
) : SubFlow<Unit> {

    private companion object {
        val log: Logger = LoggerFactory.getLogger(TransactionBackchainResolutionFlowV1::class.java)
    }

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var transactionBackchainVerifier: TransactionBackchainVerifier

    @CordaInject
    lateinit var utxoLedgerPersistenceService: UtxoLedgerPersistenceService

    @Suspendable
    override fun call() {
        val alreadyVerifiedTransactions =
            initialTransactionIds.filter { utxoLedgerPersistenceService.find(it, VERIFIED) != null }.toSet()
        val originalTransactionsToRetrieve = initialTransactionIds - alreadyVerifiedTransactions
        if (originalTransactionsToRetrieve.isNotEmpty()) {
            log.debug {
                "Backchain resolution of $initialTransactionIds - Transaction needs to resolve its dependencies of " +
                        "$originalTransactionsToRetrieve in its backchain, starting transaction backchain resolution"
            }
            val topologicalSort = flowEngine.subFlow(
                TransactionBackchainReceiverFlowV1(
                    initialTransactionIds = initialTransactionIds,
                    originalTransactionsToRetrieve,
                    session
                )
            )
            log.debug {
                "Backchain resolution of $initialTransactionIds - Retrieved dependencies of $originalTransactionsToRetrieve from its " +
                        "backchain, beginning verification before storing the transactions locally"
            }
            try {
                if (!transactionBackchainVerifier.verify(initialTransactionIds, topologicalSort)) {
                    log.warn(
                        "Backchain resolution of $initialTransactionIds - Failed due to a transaction in its backchain failing verification"
                    )
                    throw CordaRuntimeException(
                        "Backchain resolution of $initialTransactionIds - Failed due to a transaction in its backchain failing verification"
                    )
                }
                log.debug {
                    "Backchain resolution of $initialTransactionIds - Completed, resolved ${topologicalSort.size} transactions"
                }
            } catch (e: Exception) {
                log.warn(
                    "Backchain resolution of $initialTransactionIds - Failed due to a transaction in its backchain failing verification"
                )
                throw e
            }
        } else {
            if (initialTransactionIds.isEmpty()) {
                log.trace {
                    "Backchain resolution of $initialTransactionIds - Has no dependencies in its backchain, skipping transaction " +
                            "backchain resolution"
                }
            } else {
                log.trace {
                    "Backchain resolution of $initialTransactionIds - Has dependencies $initialTransactionIds which have already " +
                            "been verified locally, skipping transaction backchain resolution"
                }
            }
            session.send(TransactionBackchainRequestV1.Stop)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TransactionBackchainResolutionFlowV1

        if (initialTransactionIds != other.initialTransactionIds) return false
        if (session != other.session) return false

        return true
    }

    override fun hashCode(): Int {
        var result = initialTransactionIds.hashCode()
        result = 31 * result + session.hashCode()
        return result
    }
}
