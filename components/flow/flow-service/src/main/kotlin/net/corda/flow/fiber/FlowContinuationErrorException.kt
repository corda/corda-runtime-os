package net.corda.flow.fiber

/**
 * An exception thrown by a flow fiber when it wishes to discontinue execution because it has received notification that
 * another flow it invoked has already failed. It is necessary to throw this exception so it can be filtered at the top
 * level exception catching code. Without this, exceptions which halt the execution of a flow fiber for this reason
 * present themselves to the log as if these flows themselves failed, and the point of failure is described by way of a
 * callstack which always points to a Corda internal call. This creates misdirection to flow creators who want to see
 * only the root cause of any thrown exceptions, and not details of how Corda handles them internally.
 *
 * @param cause The original Throwable thrown from the failing flow fiber
 */
class FlowContinuationErrorException(cause: Throwable) : Exception(cause)
