package net.corda.ledger.utxo.flow.impl.flows.backchain

import net.corda.ledger.common.data.transaction.TransactionStatus.INVALID
import net.corda.ledger.common.data.transaction.TransactionStatus.UNVERIFIED
import net.corda.ledger.common.data.transaction.TransactionStatus.VERIFIED
import net.corda.ledger.utxo.flow.impl.flows.finality.getVisibleStateIndexes
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.transaction.verifier.UtxoLedgerTransactionVerificationService
import net.corda.sandbox.type.SandboxConstants.CORDA_SYSTEM_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.utilities.trace
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.VisibilityChecker
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import org.slf4j.LoggerFactory

@Component(
    service = [TransactionBackchainVerifier::class, UsedByFlow::class],
    property = [CORDA_SYSTEM_SERVICE],
    scope = PROTOTYPE
)
class TransactionBackchainVerifierImpl @Activate constructor(
    @Reference(service = UtxoLedgerPersistenceService::class)
    private val utxoLedgerPersistenceService: UtxoLedgerPersistenceService,
    @Reference(service = UtxoLedgerTransactionVerificationService::class)
    private val utxoLedgerTransactionVerificationService: UtxoLedgerTransactionVerificationService,
    @Reference(service = VisibilityChecker::class)
    private val visibilityChecker: VisibilityChecker
) : TransactionBackchainVerifier, UsedByFlow, SingletonSerializeAsToken {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @Suspendable
    override fun verify(initialTransactionIds: Set<SecureHash>, topologicalSort: TopologicalSort): Boolean {
        val sortedTransactions = topologicalSort.complete().iterator()

        for (transactionId in sortedTransactions) {
            val (transaction, status) = utxoLedgerPersistenceService.findSignedLedgerTransactionWithStatus(
                transactionId,
                UNVERIFIED
            ) ?: throw CordaRuntimeException("Transaction does not exist locally")
            when (status) {
                INVALID -> {
                    log.warn(
                        "Backchain resolution of $initialTransactionIds - Verification of transaction $transactionId failed. " +
                            "The transaction is already invalid."
                    )
                    return false
                }
                VERIFIED -> {
                    log.trace {
                        "Backchain resolution of $initialTransactionIds - transaction $transactionId is already verified, " +
                            "skipping verification."
                    }
                }
                UNVERIFIED -> {
                    if (transaction == null) {
                        log.warn(
                            "Backchain resolution of $initialTransactionIds - Verification of transaction $transactionId failed. " +
                                "The transaction disappeared."
                        )
                        return false
                    }
                    try {
                        log.info("Backchain resolution of $initialTransactionIds - Verifying transaction $transactionId")
                        transaction.verifySignatorySignatures()
                        transaction.verifyAttachedNotarySignature()
                        utxoLedgerTransactionVerificationService.verify(transaction)
                        log.info("Backchain resolution of $initialTransactionIds - Verified transaction $transactionId")
                    } catch (e: Exception) {
                        // TODO revisit what exceptions get caught
                        log.warn(
                            "Backchain resolution of $initialTransactionIds - Verification of transaction $transactionId failed," +
                                " message: ${e.message}"
                        )
                        return false
                    }
                    val visibleStatesIndexes = transaction.getVisibleStateIndexes(visibilityChecker)
                    utxoLedgerPersistenceService.persist(transaction, VERIFIED, visibleStatesIndexes)
                    log.info("Backchain resolution of $initialTransactionIds - Stored transaction $transactionId as verified")
                }

                else -> {
                    log.warn(
                        "Backchain resolution of $initialTransactionIds - Verification of transaction $transactionId failed. " +
                            "Unexpected status $status"
                    )
                    return false
                }
            }
            sortedTransactions.remove()
        }

        return true
    }
}
