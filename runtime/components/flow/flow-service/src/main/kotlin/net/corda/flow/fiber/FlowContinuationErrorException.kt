package net.corda.flow.fiber

import net.corda.v5.base.exceptions.CordaRuntimeException

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
 * Note that this exception type inherits from [CordaRuntimeException] in order that any internal code which catches
 * [CordaRuntimeException] types and rethrows as some other [Throwable] inherited type will still work. In those cases
 * the information about them being [FlowContinuationErrorException] is of course lost, and the consequences of that
 * will be the flow top level exception catching code will treat them as internal Corda errors with no special handling.
 * In general this means the log will contain the callstack from the point of the rethrow.
 *
 * @param message The original message thrown from the failing flow fiber
 * @param cause The original [Throwable] thrown from the failing flow fiber
 */
class FlowContinuationErrorException(message: String, cause: Throwable) : CordaRuntimeException(message, cause)
