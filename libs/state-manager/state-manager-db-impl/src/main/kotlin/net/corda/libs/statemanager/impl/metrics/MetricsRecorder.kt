package net.corda.libs.statemanager.impl.metrics

/**
 * Used by the state manager to record execution metrics.
 */
interface MetricsRecorder {
    // Tags used by the different State Manager operations
    enum class OperationType {
        GET, // Retrieve by key
        FIND_ALL, FIND_ANY, FIND_BETWEEN, FIND_UPDATED, // Retrieve using filters
        CREATE, UPDATE, UPDATE_BETWEEN, UPDATE_REPO, DELETE, FIND
    }

    fun <T> recordProcessingTime(operationType: OperationType, block: () -> T): T
}
