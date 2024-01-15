package net.corda.membership.lib.exceptions

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Thrown to indicate that there was an exception while updating the group parameters.
 */
class InvalidGroupParametersUpdateException(message: String, cause: Throwable? = null) : CordaRuntimeException(message, cause)
