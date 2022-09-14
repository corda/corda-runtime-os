package net.corda.flow.fiber

import net.corda.v5.base.annotations.Suspendable

/**
 * An exception thrown by a flow fiber when it wishes to discontinue execution because it has received notification that
 * an error occurred from outside the running flow fiber.
 *
 * Errors like this typically come from the flow's own pipeline failing at some point outside the fiber, for example the
 * flow making an invalid IO request in user code which was flagged by the IO request handler. It can also be a knock on
 * effect of some other flow that this flow invoked (sub flow or initialised flow) failing, which this flow detects.
 *
 * Checks for these failures are done when the flow resumes, and to halt its own execution at this point, it needs to
 * throw. It is necessary to throw this exception in these cases so they can be filtered at the top level exception
 * catching code, to disambiguate them from Corda internal exceptions in the log. In other words they are exceptions
 * which Corda has already handled in some way and should not be treated as if Corda failed at the point they are
 * thrown.
 *
 * @param cause The original [Throwable] thrown from the failing flow fiber
 */
open class FlowContinuationErrorException(cause: Throwable) : Exception(cause)

/**
 * A more specific [FlowContinuationErrorException] exception which can be used to disambiguate exceptions thrown in
 * user code from those thrown by the platform. Note that the only way Corda can know if it is executing user code is if
 * the invoking of that code is wrapped in a call to [userCodeExceptionWrapper].
 *
 * @param cause The original [FlowContinuationErrorException] thrown from the failing flow fiber
 */
class FlowContinuationErrorInUserCodeException(e: FlowContinuationErrorException) :
    FlowContinuationErrorException(e) {
    val originatingCause
        get() = cause!!.cause!! // The arguments supplied to the exceptions are not nullable
}

/**
 * Executes the block provided and Translates any thrown FlowContinuationErrorException exceptions into
 * FlowContinuationErrorInUserCodeException exceptions. This is useful to disambiguate exceptions thrown in user
 * code from those thrown by the Corda platform, which means the handling of those exceptions can differ. Primarily
 * this helps determine whether it's useful for the callstack to end up in the log or not.
 *
 * @param block Code to execute, should be a call to invoke user flow code.
 *
 * @return The return from the block.
 *
 * @throws [FlowContinuationErrorInUserCodeException] whenever a [FlowContinuationErrorException] is thrown from the
 * block, all other exceptions are untouched and will throw as the block throws them.
 */
@Suspendable
fun <R> userCodeExceptionWrapper(block: () -> R): R = try {
    block()
} catch (e: FlowContinuationErrorException) {
    throw FlowContinuationErrorInUserCodeException(e)
}
