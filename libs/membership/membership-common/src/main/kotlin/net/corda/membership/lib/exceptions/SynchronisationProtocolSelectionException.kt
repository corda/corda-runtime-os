package net.corda.membership.lib.exceptions

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Thrown when the correct synchronisation protocol service could not be selected.
 */
class SynchronisationProtocolSelectionException (message: String, cause: Throwable? = null) : CordaRuntimeException(message, cause)
