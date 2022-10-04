package net.corda.ledger.consensual.persistence

import net.corda.ledger.common.impl.transaction.WireTransaction
import net.corda.v5.application.persistence.CordaPersistenceException
import net.corda.v5.crypto.SecureHash

// TODO Currently this is WireTransaction. Later it will change to ConsensualSignedTransaction. AFAIK Ledger
//  Transactions are not going to be persisted.
/**
 * [LedgerPersistenceService] allows to insert and find Consensual Ledger transactions in the persistent store provided
 * by the platform.
 */
interface LedgerPersistenceService {
    /**
     * Persist a [WireTransaction] to the store.
     *
     * @param transaction Consensual Ledger transaction to persist.
     *
     * @throws CordaPersistenceException if an error happens during persist operation.
     */
    fun persist(transaction: WireTransaction)

    /**
     * Find a Consensual Ledger transaction in the persistence context given it's [id].
     *
     * @param id Consensual Ledger transaction ID.
     *
     * @return The found Consensual Ledger transaction, null if it could not be found in the persistence context.
     *
     * @throws CordaPersistenceException if an error happens during find operation.
     */
    fun find(id: SecureHash): WireTransaction?
}
