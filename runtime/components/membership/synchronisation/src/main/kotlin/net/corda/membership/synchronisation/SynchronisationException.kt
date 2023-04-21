package net.corda.membership.synchronisation

/**
 * Exception thrown during membership synchronisation.
 */
class SynchronisationException(message: String, cause: Throwable? = null) : Exception(message, cause)
