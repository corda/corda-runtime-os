package net.corda.libs.statemanager.api

/**
 * Details of the result of a commit transaction
 */
data class TransactionResult(
    val failedToCreate: Set<String>,
    val createOrUpdateRecordsPreviousValue: Map<String, State>,
    val failedToUpdate: Map<String, State?>,
    val failedToDelete: Map<String, State?>,
)
