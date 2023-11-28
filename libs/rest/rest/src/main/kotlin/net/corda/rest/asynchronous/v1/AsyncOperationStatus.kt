package net.corda.rest.asynchronous.v1

import java.time.Instant

data class AsyncOperationStatus(
    val requestId: String,
    val operation: String,
    val status: AsyncOperationState,
    val lastUpdatedTimestamp: Instant,
    val processingStage: String? = null,
    val errorReason: String? = null,
    val resourceId: String? = null
) {
    companion object {
        fun accepted(
            requestId: String,
            operation: String,
            timestamp: Instant
        ): AsyncOperationStatus {
            return AsyncOperationStatus(
                requestId = requestId,
                operation = operation,
                status = AsyncOperationState.ACCEPTED,
                lastUpdatedTimestamp = timestamp
            )
        }

        fun inProgress(
            requestId: String,
            operation: String,
            timestamp: Instant,
            processingStage: String? = null
        ): AsyncOperationStatus {
            return AsyncOperationStatus(
                requestId = requestId,
                operation = operation,
                status = AsyncOperationState.IN_PROGRESS,
                lastUpdatedTimestamp = timestamp,
                processingStage = processingStage
            )
        }

        fun succeeded(
            requestId: String,
            operation: String,
            timestamp: Instant,
            resourceId: String?,
        ): AsyncOperationStatus {
            return AsyncOperationStatus(
                requestId = requestId,
                operation = operation,
                status = AsyncOperationState.SUCCEEDED,
                lastUpdatedTimestamp = timestamp,
                resourceId = resourceId,
            )
        }

        fun failed(
            requestId: String,
            operation: String,
            timestamp: Instant,
            errorReason: String,
            processingStage: String? = null
        ): AsyncOperationStatus {
            return AsyncOperationStatus(
                requestId = requestId,
                operation = operation,
                status = AsyncOperationState.FAILED,
                lastUpdatedTimestamp = timestamp,
                processingStage = processingStage,
                errorReason = errorReason
            )
        }

        fun aborted(
            requestId: String,
            operation: String,
            timestamp: Instant
        ): AsyncOperationStatus {
            return AsyncOperationStatus(
                requestId = requestId,
                operation = operation,
                status = AsyncOperationState.ABORTED,
                lastUpdatedTimestamp = timestamp,
            )
        }
    }
}
