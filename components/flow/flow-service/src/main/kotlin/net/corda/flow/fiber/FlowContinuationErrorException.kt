package net.corda.flow.fiber

/**
 * An exception thrown by a flow fiber when it wishes to discontinue execution because it has received notification that
 * another flow it invoked has already failed. It is necessary to throw this exception so it can be filtered at the top
 * level exception catching code for executing flow fibers, to disambiguate them from Corda internal exceptions. When
 * these exceptions are caught Corda should not log a callstack, because there is no error in the flow fiber throwing
 * it, and callstacks logged would point to the Corda internal code which implements error handling. That would be very
 * misleading for developers of CorDapps.
 *
 * @param cause The original Throwable thrown from the failing flow fiber
 */
class FlowContinuationErrorException(cause: Throwable) : Exception(cause)
