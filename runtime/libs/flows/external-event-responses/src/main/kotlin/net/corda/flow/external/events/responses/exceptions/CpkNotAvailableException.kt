package net.corda.flow.external.events.responses.exceptions

/**
 * Used to indicate that a CPKs cannot be retrieved (DB Worker might not be ready yet).
 *
 * This can happen if CPKs broadcast over kafka have not yet arrived.
 */
class CpkNotAvailableException(message: String, cause: Throwable? = null) : Exception(message, cause)
