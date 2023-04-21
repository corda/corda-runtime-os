package net.corda.ledger.persistence.consensual

import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.v5.ledger.common.transaction.CordaPackageSummary

interface ConsensualPersistenceService {
    /**
     * Retrieves transaction with given [id]
     */
    fun findTransaction(id: String): SignedTransactionContainer?

    /**
     * Persists [transaction] data to database and links it to existing CPKs.
     * @return List of missing CPKs (that were not linked to transaction).
     */
    fun persistTransaction(transaction: ConsensualTransactionReader): List<CordaPackageSummary>
}
