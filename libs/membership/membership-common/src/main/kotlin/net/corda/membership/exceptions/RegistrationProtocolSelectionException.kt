package net.corda.membership.exceptions

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Thrown when the correct registration protocol service could not be selected.
 */
class RegistrationProtocolSelectionException (message: String, cause: Throwable? = null) : CordaRuntimeException(message, cause)