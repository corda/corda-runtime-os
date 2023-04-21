package net.corda.flow.rest.impl

object FlowRestExceptionConstants {
    const val NO_EXCEPTION_MESSAGE = "No exception message provided."
    const val FATAL_ERROR = "Fatal error occurred, can no longer start flows from this worker."
    const val NON_FATAL_ERROR = "Non-fatal error occurred when starting flow."
    const val TEMPORARY_INTERNAL_FAILURE = "temporary internal failure. Please try again."
    const val UNINITIALIZED_ERROR = "FlowRestResource has not been initialised."
    const val ALREADY_EXISTS_ERROR = "A flow has already been started for the requested holdingId and clientRequestId."
    const val VALIDATION_ERROR = "Validation error while registering flow status listener, message: %s."
    const val UNEXPECTED_ERROR = "Unexpected error while registering flow status listener."
    const val NOT_OPERATIONAL = "Flow start capabilities of virtual node %s are not operational."
    const val FORBIDDEN = "User %s is not allowed to start a flow: %s."
    const val INVALID_ID = "Supplied clientRequestId %s is invalid, it must conform to the pattern %s."
    const val CPI_NOT_FOUND = "Failed to find a CPI for ID = %s."
    const val FLOW_STATUS_NOT_FOUND = "Failed to find the flow status for holdingId = %s and clientRequestId = %s."

}