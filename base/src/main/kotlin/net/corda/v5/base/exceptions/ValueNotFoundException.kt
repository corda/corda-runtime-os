package net.corda.v5.base.exceptions

/**
 * Exception, being thrown if a value for a specific key cannot be found in the [LayeredPropertyMap].
 */
class ValueNotFoundException(message: String?) : CordaRuntimeException(message)