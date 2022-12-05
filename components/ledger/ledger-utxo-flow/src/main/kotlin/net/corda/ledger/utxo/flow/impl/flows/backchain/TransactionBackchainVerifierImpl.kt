package net.corda.ledger.utxo.flow.impl.flows.backchain

import net.corda.ledger.common.data.transaction.TransactionStatus.UNVERIFIED
import net.corda.ledger.common.data.transaction.TransactionStatus.VERIFIED
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.loggerFor
import net.corda.v5.base.util.trace
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Component(service = [TransactionBackchainVerifier::class, UsedByFlow::class], scope = PROTOTYPE, property = ["corda.system=true"])
class TransactionBackchainVerifierImpl @Activate constructor(
    @Reference(service = TransactionVerifier::class)
    private val transactionVerifier: TransactionVerifier,
    @Reference(service = UtxoLedgerPersistenceService::class)
    private val utxoLedgerPersistenceService: UtxoLedgerPersistenceService
) : TransactionBackchainVerifier, UsedByFlow, SingletonSerializeAsToken {

    private companion object {
        val log = loggerFor<TransactionBackchainVerifierImpl>()
    }

    // we may want to tidy up transitively verified transactions that have sat around for a while
    // means we'd need an extra status to know what they are
    // we could clean up once the flow finishes but could that cause concurrency issues?
    // this would be needed if we hit an invalid transaction when verifying. All transactions that come depend on it can never verify,
    // therefore we should discard them from our database, otherwise people can fill up our database with invalid transactions, although
    // that can still be done by sending long backchains

    // return a boolean or just throw an exception?
    @Suspendable
    override fun verify(resolvingTransactionId: SecureHash, topologicalSort: TopologicalSort): Boolean {
        val sortedTransactions = topologicalSort.complete().iterator()

        for (transactionId in sortedTransactions) {
            // Consider moving to separate private method as it might reduce the fiber stack when coming back into the loop
            val transaction = utxoLedgerPersistenceService.find(transactionId, UNVERIFIED)
                ?: throw CordaRuntimeException("Transaction does not exist locally") // TODO what to do if transaction disappears
            try {
                log.trace { "Backchain resolution of $resolvingTransactionId - Verifying transaction $transactionId" }
                transactionVerifier.verify(transaction)
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

// temporary to mock verification
interface TransactionVerifier {
    fun verify(transaction: UtxoSignedTransaction)
}

@Component(service = [TransactionVerifier::class, UsedByFlow::class], scope = PROTOTYPE, property = ["corda.system=true"])
class TransactionVerifierImpl : TransactionVerifier, UsedByFlow {
    override fun verify(transaction: UtxoSignedTransaction) {
        // nothing
    }
}