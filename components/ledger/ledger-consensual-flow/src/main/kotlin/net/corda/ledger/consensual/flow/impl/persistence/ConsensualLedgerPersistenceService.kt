package net.corda.ledger.consensual.flow.impl.persistence

import net.corda.v5.application.persistence.CordaPersistenceException
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction

/**
 * [ConsensualLedgerPersistenceService] allows to insert and find consensual signed transactions in the persistent store provided
 * by the platform.
 */
interface ConsensualLedgerPersistenceService {
    /**
     * Persist a [ConsensualSignedTransaction] to the store.
     *
     * @param transaction Consensual signed transaction to persist.
     *
     * @throws CordaPersistenceException if an error happens during persist operation.
     */
    fun persist(transaction: ConsensualSignedTransaction)

    /**
     * Find a consensual signed transaction in the persistence context given it's [id].
     *
     * @param id Consensual signed transaction ID.
     *
     * @return The found consensual signed transaction, null if it could not be found in the persistence context.
     *
     * @throws CordaPersistenceException if an error happens during find operation.
     */
    fun find(id: SecureHash): ConsensualSignedTransaction?
}
