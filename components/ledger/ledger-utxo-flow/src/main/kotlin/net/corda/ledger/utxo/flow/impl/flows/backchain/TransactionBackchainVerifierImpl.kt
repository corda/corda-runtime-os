package net.corda.ledger.utxo.flow.impl.flows.backchain

import net.corda.ledger.common.data.transaction.TransactionStatus.UNVERIFIED
import net.corda.ledger.common.data.transaction.TransactionStatus.VERIFIED
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.transaction.verifier.UtxoLedgerTransactionVerifierService
import net.corda.sandbox.type.SandboxConstants.CORDA_SYSTEM_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.trace
import net.corda.v5.crypto.SecureHash
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import org.slf4j.LoggerFactory

@Component(
    service = [ TransactionBackchainVerifier::class, UsedByFlow::class ],
    property = [ CORDA_SYSTEM_SERVICE ],
    scope = PROTOTYPE
)
class TransactionBackchainVerifierImpl @Activate constructor(
    @Reference(service = UtxoLedgerPersistenceService::class)
    private val utxoLedgerPersistenceService: UtxoLedgerPersistenceService,
    @Reference(service = UtxoLedgerTransactionVerifierService::class)
    private val utxoLedgerTransactionVerifierService: UtxoLedgerTransactionVerifierService
) : TransactionBackchainVerifier, UsedByFlow, SingletonSerializeAsToken {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @Suspendable
    override fun verify(resolvingTransactionId: SecureHash, topologicalSort: TopologicalSort): Boolean {
        val sortedTransactions = topologicalSort.complete().iterator()

        for (transactionId in sortedTransactions) {
            val transaction = utxoLedgerPersistenceService.find(transactionId, UNVERIFIED)
                ?: throw CordaRuntimeException("Transaction does not exist locally") // TODO what to do if transaction disappears
            try {
                log.trace { "Backchain resolution of $resolvingTransactionId - Verifying transaction $transactionId" }
                utxoLedgerTransactionVerifierService.verify(transaction.toLedgerTransaction())
                log.trace { "Backchain resolution of $resolvingTransactionId - Verified transaction $transactionId" }
            } catch (e: Exception) {
                // TODO revisit what exceptions get caught
                log.warn(
                    "Backchain resolution of $resolvingTransactionId - Verified of transaction $transactionId failed, message: " +
                            "${e.message}"
                )
                return false
            }
            utxoLedgerPersistenceService.updateStatus(transactionId, VERIFIED)
            log.trace { "Backchain resolution of $resolvingTransactionId - Updated status of transaction $transactionId to verified" }
            sortedTransactions.remove()
        }

        return true
    }
}
