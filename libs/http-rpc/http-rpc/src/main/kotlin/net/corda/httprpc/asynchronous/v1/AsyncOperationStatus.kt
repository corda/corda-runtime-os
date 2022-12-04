package net.corda.httprpc.asynchronous.v1

import java.time.Instant

data class AsyncOperationStatus(
    val operationType: String,
    val operationData: Any,
    val state: AsyncOperationState,
    val requestTimestamp: Instant?,
    val completedTimestamp: Instant?,
    val errors: List<AsyncError>? = null
){
    companion object {
        fun ok(
            operationType: String,
            operationData: Any,
            state: AsyncOperationState,
            requestTimestamp: Instant?,
            completedTimestamp: Instant?,
            errors: List<AsyncError>?
        ): AsyncOperationStatus {
            return AsyncOperationStatus(operationType, operationData, state, requestTimestamp, completedTimestamp, errors)
        }

        fun inProgress(
            operationType: String,
            operationData: Any,
            requestTimestamp: Instant?,
            completedTimestamp: Instant?
        ): AsyncOperationStatus {
            return AsyncOperationStatus(operationType, operationData, AsyncOperationState.IN_PROGRESS, requestTimestamp, completedTimestamp)
        }

        fun errors(
            operationType: String,
            operationData: Any,
            requestTimestamp: Instant?,
            completedTimestamp: Instant?,
            errors: List<AsyncError>?
        ): AsyncOperationStatus {
            return AsyncOperationStatus(operationType, operationData, AsyncOperationState.COMPLETED, requestTimestamp, completedTimestamp, errors)
        }
    }
}