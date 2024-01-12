package net.corda.libs.statemanager.api

/**
 * Details of the result of a commit transaction
 * @property failedToCreate Collection of keys for all those states that could not be persisted on the underlying persistent storage.
 * @property failedToUpdate Map with the most up-to-date version of the states, associated by key for easier access, that failed
 *      the optimistic locking check. If this state failed to be updated because the key was deleted the key is
 *      associated with null.
 * @property failedToDelete Map with the most up-to-date version of the states, associated by key for easier access, that failed
 *      the optimistic locking check.
 */
data class TransactionResult(
    val failedToCreate: Set<String>,
    val failedToUpdate: Map<String, State?>,
    val failedToDelete: Map<String, State>,
)
