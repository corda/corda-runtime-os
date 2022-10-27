package net.corda.httprpc.response

open class AsyncOperationStatus(
    val startTime: Instant?,
    val endTime: Instant?,
    val status: AsyncOperationState,
    val errors: List<AsyncOperationError>?
)