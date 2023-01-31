package net.corda.httprpc.asynchronous.v1

import java.time.Instant

data class AsyncOperationStatus(
    val operationType: String,
    val operationData: Any,
    val state: AsyncOperationState,
    val requestTimestamp: Instant?,
    val latestUpdateTimestamp: Instant?,
    val heartbeatTimestamp: Instant?,
    val errors: List<AsyncError>? = null
) {
    companion object {
        @Suppress("LongParameterList")
        fun ok(
            operationType: String,
            operationData: Any,
            state: AsyncOperationState,
            requestTimestamp: Instant?,
            latestUpdateTimestamp: Instant?,
            heartbeatTimestamp: Instant?,
            errors: List<AsyncError>?
        ): AsyncOperationStatus {
            return AsyncOperationStatus(
                operationType,
                operationData,
                state,
                requestTimestamp,
                latestUpdateTimestamp,
                heartbeatTimestamp,
                errors
            )
        }

        fun inProgress(
            operationType: String,
            operationData: Any,
            requestTimestamp: Instant?,
            latestUpdateTimestamp: Instant?,
            heartbeatTimestamp: Instant?
        ): AsyncOperationStatus {
            return AsyncOperationStatus(
                operationType,
                operationData,
                AsyncOperationState.IN_PROGRESS,
                requestTimestamp,
                latestUpdateTimestamp,
                heartbeatTimestamp
            )
        }

        @Suppress("LongParameterList")
        fun errors(
            operationType: String,
            operationData: Any,
            requestTimestamp: Instant?,
            latestUpdateTimestamp: Instant?,
            heartbeatTimestamp: Instant?,
            errors: List<AsyncError>?
        ): AsyncOperationStatus {
            return AsyncOperationStatus(
                operationType,
                operationData,
                AsyncOperationState.COMPLETED,
                requestTimestamp,
                latestUpdateTimestamp,
                heartbeatTimestamp,
                errors
            )
        }
    }
}