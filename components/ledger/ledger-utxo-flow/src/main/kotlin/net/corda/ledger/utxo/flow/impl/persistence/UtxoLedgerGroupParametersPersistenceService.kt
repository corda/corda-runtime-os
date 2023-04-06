package net.corda.ledger.utxo.flow.impl.persistence

import net.corda.ledger.common.flow.transaction.SignedGroupParametersContainer
import net.corda.v5.application.persistence.CordaPersistenceException
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash

/**
 * [UtxoLedgerGroupParametersPersistenceService] allows to persist and find group parameters
 */
interface UtxoLedgerGroupParametersPersistenceService {

    /**
     * Find a group parameters by its hash
     *
     * @param hash The hash of the group parameters.
     *
     * @return The found signed group parameters, null if it could not be found in the persistence context.
     *
     * @throws CordaPersistenceException if an error happens during find operation.
     */
    @Suspendable
    fun find(hash: SecureHash): SignedGroupParametersContainer?

    /**
     * Persist a [SignedGroupParametersContainer] to the store if it does not exist there yet.
     *
     * @param signedGroupParameters Signed group parameters to persist.
     *
     * @throws CordaPersistenceException if an error happens during persist operation.
     */
    @Suspendable
    fun persistIfDoesNotExist(
        signedGroupParameters: SignedGroupParametersContainer,
    )
}