package net.corda.libs.statemanager.impl.metrics

/**
 * Used by the state manager to record execution metrics.
 */
interface MetricsRecorder {
    // Tags used by the different State Manager operations
    enum class OperationType {
        GET, // Retrieve by key
        FIND, // Retrieve using filters
        CREATE, UPDATE, DELETE
    }

    fun <T> recordProcessingTime(operationType: OperationType, block: () -> T): T

    fun recordFailureCount(operationType: OperationType, count: Int)
}
