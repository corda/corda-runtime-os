package net.corda.membership.lib.exceptions

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Thrown when the correct synchronisation protocol service could not be selected.
 */
class SynchronisationProtocolSelectionException (message: String, cause: Throwable? = null) : CordaRuntimeException(message, cause)

/**
 * Thrown when not the expected synchronisation type is configured for an identity.
 */
class SynchronisationProtocolTypeException (message: String) : CordaRuntimeException(message)
