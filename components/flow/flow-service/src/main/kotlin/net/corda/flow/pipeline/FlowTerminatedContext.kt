package net.corda.flow.pipeline

/**
 * [FlowTerminatedContext] contains context of the termination of the processing of a flow event.
 */
data class FlowTerminatedContext(
    /**
     * The termination status of the processing of this flow event.
     */
    val terminationStatus: TerminationStatus,
    /**
     * Additional details about the termination of this flow event.
     */
    val details: Map<String, String>? = null
) {
    enum class TerminationStatus {
        /**
         * Indicates to the flow context that this flow should be killed.
         */
        TO_BE_KILLED
    }

    companion object {
        const val TERMINATION_REASON_KEY = "TERMINATION_REASON"
    }
}