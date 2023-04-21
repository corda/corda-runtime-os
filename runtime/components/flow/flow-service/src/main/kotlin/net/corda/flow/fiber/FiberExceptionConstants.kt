package net.corda.flow.fiber

object FiberExceptionConstants {
    private const val MISSING_SUSPENDABLE_ANNOTATION =
        "(Note: This may happen if you're missing a `@Suspendable` annotation somewhere in your flow.)"
    const val MISSING_START_ARGUMENTS = "Failed to find the start args for Rest started flow."
    const val FLOW_DISCONTINUED = "Flow was discontinued, reason: %s thrown, %s."
    const val FLOW_FAILED_THROWABLE = "FlowFiber failed due to Throwable being thrown."
    const val FLOW_FAILED_GENERIC = "runFlow failed to complete normally, forcing a failure."
    const val UNEXPECTED_OUTCOME = "Unexpected Flow outcome."
    const val INVALID_FLOW_RETURN = "Tried to return when suspension outcome says to continue."
    const val INVALID_FLOW_KEY = "Expected the flow key to have a UUID id found '%s' instead."
    const val UNABLE_TO_EXECUTE = "Unable to execute flow fiber: %s."
    const val NULL_FLOW_STACK_SERVICE =
        "Flow [%s] should have a single flow stack item when finishing but the stack was null. $MISSING_SUSPENDABLE_ANNOTATION"
    const val EMPTY_FLOW_STACK =
        "Flow [%s] should have a single flow stack item when finishing but was empty. $MISSING_SUSPENDABLE_ANNOTATION"
    const val INCORRECT_ITEM_COUNT =
        "Flow [%s] should have a single flow stack item when finishing but contained %s. $MISSING_SUSPENDABLE_ANNOTATION"

}